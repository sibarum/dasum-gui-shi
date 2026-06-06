package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND;
import static sibarum.dasum.gui.natives.gl.Gl.GL_CURRENT_PROGRAM;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_BUFFER_BIT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_TEST;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FUNC_ADD;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ONE_MINUS_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SCISSOR_BOX;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SCISSOR_TEST;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_VIEWPORT;

/**
 * Try-with-resources guard for rendering 3D content into a component
 * rect. Encapsulates the GL state dance every custom 3D renderer must
 * perform — historically the most bug-prone part of the point-cloud
 * renderer (see {@code CustomRenderers.Renderer} javadoc and the
 * {@code GlStateGuard} rationale):
 *
 * <ol>
 *   <li>Flush the 2D batcher so buffered geometry isn't drawn under the
 *       new state.</li>
 *   <li>Push the scissor to the component rect.</li>
 *   <li>Save the current {@code glViewport} (queried from GL — the
 *       caller's frame setup is the source of truth) and re-aim it at
 *       the rect so NDC [-1, 1] maps to the component, not the whole
 *       framebuffer.</li>
 *   <li>Optionally enable depth testing and clear the rect's depth slice
 *       (scissored, so multiple viewports on screen don't fight).</li>
 * </ol>
 *
 * {@link #close()} restores everything the framework's 2D pipeline
 * assumes: depth test off, depth writes on, blending enabled with the
 * default func/equation (SRC_ALPHA / ONE_MINUS_SRC_ALPHA, FUNC_ADD),
 * program unbound, the saved viewport reinstated, batcher flushed and
 * scissor popped. Renderers may freely change blend state, depth mask
 * and programs inside the scope.
 */
public final class ViewportScope implements AutoCloseable {

    private final Batcher batcher;
    private final float[] projection;
    private final int[] savedViewport;
    private final int[] savedScissorBox;
    private final int savedProgram;
    private final boolean depth;

    /**
     * Enter the scope: flush, scissor to {@code rect}, retarget the
     * viewport, and (when {@code enableDepth}) clear the rect's depth
     * slice and enable depth testing.
     */
    public ViewportScope(Batcher batcher, float[] projection, PixelRect rect, boolean enableDepth) {
        this.batcher = batcher;
        this.projection = projection;
        this.depth = enableDepth;

        // Save EXACTLY what the caller had — GL state is the source of
        // truth, not a reconstructed (0, 0, fbW, fbH).
        savedScissorBox = Gl.glGetIntegerv4(GL_SCISSOR_BOX);
        savedProgram = Gl.glGetInteger(GL_CURRENT_PROGRAM);

        batcher.flush(projection);
        batcher.scissor().push(rect);

        savedViewport = Gl.glGetIntegerv4(GL_VIEWPORT);
        int saveH = savedViewport[3];

        // OpenGL viewport Y is bottom-up; flip our top-left rect.
        int vpX = (int) rect.x();
        int vpY = saveH - (int) (rect.y() + rect.height());
        Gl.glViewport(vpX, vpY, (int) rect.width(), (int) rect.height());

        if (enableDepth) {
            Gl.glEnable(GL_DEPTH_TEST);
            Gl.glClear(GL_DEPTH_BUFFER_BIT); // scissored to the rect
        }
    }

    @Override
    public void close() {
        if (depth) {
            Gl.glDisable(GL_DEPTH_TEST);
        }
        // Reset to the framework's 2D defaults regardless of what the
        // renderer did per layer.
        Gl.glDepthMask(true);
        Gl.glEnable(GL_BLEND);
        Gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        Gl.glBlendEquation(GL_FUNC_ADD);

        Gl.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);

        batcher.flush(projection); // commit 3D draws before the scissor pops
        batcher.scissor().pop();

        // The scissor stack restores the BOX only while an outer scope is
        // active; when the stack empties it disables the test and leaves
        // the box at our rect. Restore the entry value in that case so no
        // state — even harmless-while-disabled state — leaks out.
        if (Gl.glGetInteger(GL_SCISSOR_TEST) == 0) {
            Gl.glScissor(savedScissorBox[0], savedScissorBox[1], savedScissorBox[2], savedScissorBox[3]);
        }
        // Rebind whatever program the caller had (the final flush may have
        // bound a batcher material; the 2D pipeline rebinds before every
        // draw anyway, so this is purely about leaving state as found).
        Gl.glUseProgram(savedProgram);
    }
}
