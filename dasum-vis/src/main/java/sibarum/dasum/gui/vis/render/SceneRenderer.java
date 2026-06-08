package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.CustomRenderers;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.math.CameraMath;
import sibarum.dasum.gui.vis.math.CameraMode;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.scene.BlendMode;
import sibarum.dasum.gui.vis.scene.ImageLayer;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.PointLayer;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.SceneStates;
import sibarum.dasum.gui.vis.scene.TextLayer;
import sibarum.dasum.gui.vis.scene.TriangleLayer;
import sibarum.dasum.gui.vis.scene.VexelRayLayer;
import sibarum.dasum.gui.vis.scene.VexelRayView;

import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DST_COLOR;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FUNC_ADD;
import static sibarum.dasum.gui.natives.gl.Gl.GL_LINES;
import static sibarum.dasum.gui.natives.gl.Gl.GL_MAX;
import static sibarum.dasum.gui.natives.gl.Gl.GL_MIN;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ONE;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ONE_MINUS_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ONE_MINUS_SRC_COLOR;
import static sibarum.dasum.gui.natives.gl.Gl.GL_POINTS;
import static sibarum.dasum.gui.natives.gl.Gl.GL_PROGRAM_POINT_SIZE;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TRIANGLES;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ZERO;

/**
 * Draws a {@link SceneSnapshot}'s layers in painter's order through one
 * camera. Registered into {@link CustomRenderers} by
 * {@link sibarum.dasum.gui.vis.DasumVis#init}.
 *
 * <p>Per-frame work per visible scene viewport:
 * <ol>
 *   <li>Drain pending GL buffer deletions (main-thread cleanup for
 *       components detached on other threads).</li>
 *   <li>Read the current scene + camera atomically; sync GPU buffers
 *       (per-layer identity skip — see {@link SceneGlBuffers}).</li>
 *   <li>Enter a {@link ViewportScope} (flush, scissor, viewport retarget,
 *       depth clear in perspective).</li>
 *   <li>For each layer: apply its {@link BlendMode}, set depth writes
 *       (perspective: OPAQUE layers write depth, translucent layers test
 *       but don't write — near translucency must not punch holes for far
 *       content), bind the material, draw.</li>
 * </ol>
 *
 * <p>Main thread only. The scope restores all global GL state on close.
 */
public final class SceneRenderer implements AutoCloseable {

    private final PointMaterial pointMaterial         = new PointMaterial();
    private final FlatMaterial flatMaterial           = new FlatMaterial();
    private final ImageMaterial imageMaterial         = new ImageMaterial();
    private final SceneTextMaterial sceneTextMaterial = new SceneTextMaterial();
    private final VexelRayMaterial vexelRayMaterial   = new VexelRayMaterial();
    private final SceneGlBuffers buffers              = new SceneGlBuffers();

    /** Scratch MVP — reused across frames; main-thread only. */
    private final float[] scratchMvp = new float[16];

    private boolean initialized = false;

    public void init() {
        if (initialized) return;
        pointMaterial.init();
        flatMaterial.init();
        imageMaterial.init();
        sceneTextMaterial.init();
        vexelRayMaterial.init();
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
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) return;

        buffers.drainPendingDeletes();

        SceneSnapshot scene = SceneStates.sceneOf(cmp);
        if (scene == null || scene.layers().isEmpty()) return;

        CameraSpec cam = SceneStates.cameraOf(cmp);
        SceneGlBuffers.Entry entry = buffers.ensure(cmp, scene);

        CameraMath.mvp(cam, rect.width() / rect.height(), scratchMvp);
        boolean perspective = cam.mode() == CameraMode.PERSPECTIVE;

        try (ViewportScope scope = new ViewportScope(batcher, projection, rect, perspective)) {
            for (Layer layer : scene.layers()) {
                SceneGlBuffers.Slot slot = entry.slot(layer);
                if (slot == null || slot.vertexCount == 0) continue;

                applyBlend(layer.blend());
                if (perspective) {
                    // Translucent layers read depth but must not write it —
                    // a near translucent fragment writing depth would mask
                    // farther content drawn later in painter's order.
                    Gl.glDepthMask(layer.blend() == BlendMode.OPAQUE);
                }

                switch (layer) {
                    case PointLayer p -> {
                        Gl.glEnable(GL_PROGRAM_POINT_SIZE);
                        pointMaterial.bind(scratchMvp, p.opacity());
                        draw(slot, GL_POINTS);
                        Gl.glDisable(GL_PROGRAM_POINT_SIZE);
                    }
                    case LineLayer l -> {
                        flatMaterial.bind(scratchMvp, l.opacity());
                        draw(slot, GL_LINES);
                    }
                    case TriangleLayer t -> {
                        flatMaterial.bind(scratchMvp, t.opacity());
                        draw(slot, GL_TRIANGLES);
                    }
                    case ImageLayer img -> {
                        slot.texture.bind(0);
                        imageMaterial.bind(scratchMvp, img.opacity());
                        draw(slot, GL_TRIANGLES);
                    }
                    case VexelRayLayer v -> {
                        // Camera-anchored key light: over the viewer's right
                        // shoulder, recomputed per frame from the view basis —
                        // whatever the user orbits to is always lit (inspection-
                        // viewer convention; shadows sweep with the view).
                        float[] basis = CameraMath.viewBasis(cam);
                        float[] fwd = CameraMath.forward(cam);
                        float lx = 0.45f * basis[0] + 0.55f * basis[3] - fwd[0];
                        float ly = 0.45f * basis[1] + 0.55f * basis[4] - fwd[1];
                        float lz = 0.45f * basis[2] + 0.55f * basis[5] - fwd[2];
                        float len = (float) Math.sqrt(lx*lx + ly*ly + lz*lz);
                        float[] lightDir = {lx / len, ly / len, lz / len};
                        vexelRayMaterial.bind(scratchMvp, v,
                            CameraMath.eye(cam), fwd, lightDir,
                            cam.mode() == CameraMode.ORTHOGRAPHIC,
                            VexelRayView.current().ordinal());
                        draw(slot, GL_TRIANGLES);
                    }
                    case TextLayer txt -> {
                        // Leaving unit 0 bound to the atlas afterwards is
                        // safe — the 2D batcher's materials re-bind their
                        // atlas on every flush.
                        FontGroup fgrp = FontGroups.getOrDefault(txt.fontGroup());
                        fgrp.texture().bind(0);
                        float[] basis = txt.billboard()
                            ? CameraMath.viewBasis(cam)
                            : CameraMath.IDENTITY_BASIS;
                        sceneTextMaterial.bind(scratchMvp,
                            txt.anchor().x(), txt.anchor().y(), txt.anchor().z(),
                            basis, txt.color(), txt.opacity(), fgrp.distanceRange());
                        draw(slot, GL_TRIANGLES);
                    }
                }
            }
        }
        // ViewportScope.close() restored blend/depth/viewport/program state.
    }

    private static void draw(SceneGlBuffers.Slot slot, int mode) {
        Gl.glBindVertexArray(slot.vao);
        Gl.glDrawArrays(mode, 0, slot.vertexCount);
        Gl.glBindVertexArray(0);
    }

    private static void applyBlend(BlendMode mode) {
        switch (mode) {
            case OPAQUE -> Gl.glDisable(GL_BLEND);
            case ALPHA -> {
                Gl.glEnable(GL_BLEND);
                Gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                Gl.glBlendEquation(GL_FUNC_ADD);
            }
            case ADDITIVE -> {
                Gl.glEnable(GL_BLEND);
                Gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE);
                Gl.glBlendEquation(GL_FUNC_ADD);
            }
            case SCREEN -> {
                Gl.glEnable(GL_BLEND);
                Gl.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_COLOR);
                Gl.glBlendEquation(GL_FUNC_ADD);
            }
            case MULTIPLY -> {
                Gl.glEnable(GL_BLEND);
                Gl.glBlendFunc(GL_DST_COLOR, GL_ZERO);
                Gl.glBlendEquation(GL_FUNC_ADD);
            }
            case MAX -> {
                Gl.glEnable(GL_BLEND);
                Gl.glBlendFunc(GL_ONE, GL_ONE);
                Gl.glBlendEquation(GL_MAX);
            }
            case MIN -> {
                Gl.glEnable(GL_BLEND);
                Gl.glBlendFunc(GL_ONE, GL_ONE);
                Gl.glBlendEquation(GL_MIN);
            }
        }
    }

    @Override
    public void close() {
        buffers.close();
        pointMaterial.close();
        flatMaterial.close();
        imageMaterial.close();
        sceneTextMaterial.close();
        vexelRayMaterial.close();
    }
}
