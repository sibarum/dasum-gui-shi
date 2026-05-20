package sibarum.dasum.gui.vis.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.CustomRenderers;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.math.CameraMode;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.pointcloud.PointCloudSnapshot;
import sibarum.dasum.gui.vis.pointcloud.PointCloudStates;

import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_BUFFER_BIT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_TEST;
import static sibarum.dasum.gui.natives.gl.Gl.GL_LINES;
import static sibarum.dasum.gui.natives.gl.Gl.GL_POINTS;
import static sibarum.dasum.gui.natives.gl.Gl.GL_PROGRAM_POINT_SIZE;
import static sibarum.dasum.gui.natives.gl.Gl.GL_VIEWPORT;

/**
 * The actual point-cloud draw routine. Registered into
 * {@link CustomRenderers} by {@link sibarum.dasum.gui.vis.DasumVis#init}
 * so {@code dasum-core}'s {@code Render} pipeline can dispatch
 * {@code Component.PointCloud} components without depending on this
 * module.
 *
 * <p>Per-frame work per visible point-cloud viewport:
 * <ol>
 *   <li>Drain any pending GL buffer deletions (lazy main-thread cleanup
 *       for components recently detached on other threads).</li>
 *   <li>Read the current snapshot + camera spec atomically.</li>
 *   <li>Skip the upload if the snapshot reference hasn't changed since
 *       last frame — workers republishing at high frequency cost only
 *       the snapshot allocation, not a GPU upload.</li>
 *   <li>Flush the batcher's pending 2D geometry, push our scissor,
 *       clear the scissored depth slice (perspective mode only),
 *       enable depth + program-point-size, draw {@code GL_POINTS},
 *       restore.</li>
 * </ol>
 *
 * <p>Main thread only. The cross-thread contract is upheld by reading
 * everything through {@code PointCloudStates}' atomic refs and by
 * keeping all JOML allocations inside this class.
 */
public final class PointCloudRenderer implements AutoCloseable {

    /** Default point diameter in pixels — adjustable later via per-component state. */
    private static final float DEFAULT_POINT_SIZE_PX = 5f;

    private final PointCloudMaterial material      = new PointCloudMaterial();
    private final LineSegmentMaterial lineMaterial = new LineSegmentMaterial();
    private final PointCloudGlBuffers buffers      = new PointCloudGlBuffers();

    /** Scratch buffers — reused across frames; main-thread only. */
    private final Matrix4f scratchMvp     = new Matrix4f();
    private final Matrix4f scratchProj    = new Matrix4f();
    private final Matrix4f scratchView    = new Matrix4f();
    private final Vector3f scratchEye     = new Vector3f();
    private final Vector3f scratchTarget  = new Vector3f();
    private final Vector3f scratchUp      = new Vector3f(0f, 1f, 0f);
    private final float[]  scratchMatrix  = new float[16];

    private boolean initialized = false;

    public void init() {
        if (initialized) return;
        material.init();
        lineMaterial.init();
        initialized = true;
    }

    public CustomRenderers.Renderer asRenderer() {
        return this::render;
    }

    /** Called by the cleaner registered in {@link sibarum.dasum.gui.vis.DasumVis#init}. */
    public void onComponentDetached(Component c) {
        buffers.scheduleDelete(c);
    }

    private void render(Component cmp, PixelRect rect, Batcher batcher, float[] projection) {
        if (!(cmp instanceof Component.PointCloud pc)) return;
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) return;

        // Process any deferred cleanup before this frame's work.
        buffers.drainPendingDeletes();

        PointCloudSnapshot snap = PointCloudStates.snapshotOf(pc);
        if (snap == null) return;
        if (snap.pointCount() == 0 && snap.segmentCount() == 0) return;

        CameraSpec cam = PointCloudStates.cameraOf(pc);
        PointCloudGlBuffers.Entry buf = buffers.ensure(pc, snap);
        if (buf == null) return;

        composeMvp(cam, rect.width() / rect.height());
        scratchMvp.get(scratchMatrix);

        // Flush the 2D batcher before changing global GL state. The scissor
        // stack already manages glEnable(GL_SCISSOR_TEST) / glScissor.
        batcher.flush(projection);
        batcher.scissor().push(rect);

        // Save the current viewport so we restore EXACTLY what the caller
        // set, not a guessed (0, 0, fbW, fbH). Querying GL state avoids
        // a previous footgun: a caller that didn't pass valid framebuffer
        // dimensions would have left the viewport at (0, 0, 0, 0) and
        // every component drawn after the point cloud that frame would
        // be invisible (= zero-size viewport).
        int[] savedViewport = Gl.glGetIntegerv4(GL_VIEWPORT);
        int saveH = savedViewport[3];

        // Re-aim glViewport at this component's rect so NDC [-1,1] maps
        // to the rect (rather than the whole framebuffer that App.java
        // set up). Without this, a small-rect viewport (e.g. a node
        // thumbnail off in a corner) would have all its points scissored
        // away — NDC centre lands at framebuffer centre, which is outside
        // the rect. OpenGL viewport Y is bottom-up, so flip our top-left
        // rect using the saved viewport height.
        int vpX = (int) rect.x();
        int vpY = saveH - (int) (rect.y() + rect.height());
        int vpW = (int) rect.width();
        int vpH = (int) rect.height();
        Gl.glViewport(vpX, vpY, vpW, vpH);

        boolean perspective = cam.mode() == CameraMode.PERSPECTIVE;
        if (perspective) {
            // Scissored clear of just the viewport rect's depth slice — leaves
            // the rest of the framebuffer's depth state alone, so multiple
            // viewports on screen don't interfere.
            Gl.glEnable(GL_DEPTH_TEST);
            Gl.glClear(GL_DEPTH_BUFFER_BIT);
        }

        // ---- point layer ----
        if (buf.uploadedPointCount > 0) {
            Gl.glEnable(GL_PROGRAM_POINT_SIZE);
            material.bind(scratchMatrix, DEFAULT_POINT_SIZE_PX);
            Gl.glBindVertexArray(buf.pointVao);
            Gl.glDrawArrays(GL_POINTS, 0, buf.uploadedPointCount);
            Gl.glBindVertexArray(0);
            Gl.glDisable(GL_PROGRAM_POINT_SIZE);
        }

        // ---- line layer ----
        // Drawn AFTER points so a line on the same depth wins z-order ties
        // (lines are typically scaffolding — axes, edges — meant to sit on
        // top of point dots). Both layers use the same MVP / scissor /
        // viewport / depth state, so just swap programs + VAO.
        if (buf.uploadedLineVertexCount > 0) {
            lineMaterial.bind(scratchMatrix);
            Gl.glBindVertexArray(buf.lineVao);
            Gl.glDrawArrays(GL_LINES, 0, buf.uploadedLineVertexCount);
            Gl.glBindVertexArray(0);
        }

        if (perspective) {
            Gl.glDisable(GL_DEPTH_TEST);
        }
        Gl.glUseProgram(0);

        // Restore EXACTLY what the caller had — not a constructed value.
        Gl.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);

        batcher.flush(projection); // ensure point draws are committed before scissor pop
        batcher.scissor().pop();
    }

    private void composeMvp(CameraSpec cam, float aspect) {
        scratchTarget.set(cam.target().x(), cam.target().y(), cam.target().z());

        if (cam.mode() == CameraMode.ORTHOGRAPHIC) {
            float halfH = Math.max(1e-4f, cam.orthoScale());
            float halfW = halfH * (aspect > 0f ? aspect : 1f);
            scratchProj.identity().ortho(
                -halfW, halfW, -halfH, halfH,
                -cam.farPlane(), cam.farPlane()
            );
            scratchView.identity().translate(
                -scratchTarget.x, -scratchTarget.y, -scratchTarget.z
            );
        } else {
            scratchProj.identity().perspective(
                cam.fovYRad(),
                aspect > 0f ? aspect : 1f,
                cam.nearPlane(), cam.farPlane()
            );
            // Orbit camera: place eye at (0, 0, distance), rotate by yaw/pitch,
            // translate into orbit around target. Equivalent to a yaw-pitch
            // rotation about the target point.
            scratchEye.set(0f, 0f, cam.distance());
            scratchEye.rotateX(-cam.pitchRad());
            scratchEye.rotateY(cam.yawRad());
            scratchEye.add(scratchTarget);
            scratchView.identity().lookAt(scratchEye, scratchTarget, scratchUp);
        }

        scratchMvp.set(scratchProj).mul(scratchView);
    }

    @Override
    public void close() {
        // Force-drain pending deletes so detached-but-not-yet-cleaned
        // entries don't leak forever. Then materialize a snapshot of what
        // remains by recreating the entries map iteration (the internal map
        // is private; we rely on drainPendingDeletes plus future per-component
        // deletion on shutdown via the cleaner being invoked for the whole
        // tree — for MVP, on JVM exit the OS reclaims GL objects).
        buffers.drainPendingDeletes();
        material.close();
        lineMaterial.close();
    }
}
