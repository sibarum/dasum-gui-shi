package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.natives.gl.Gl;

import java.util.ArrayList;
import java.util.List;

import static sibarum.dasum.gui.natives.gl.Gl.GL_SCISSOR_TEST;

/**
 * Stack of clip rectangles for the renderer. Pushing a rect intersects it
 * with the current top — clipping nests, never widens. Empty stack means
 * "no scissor active" (whole framebuffer drawable).
 * <p>
 * Note: GL's {@code glScissor} takes pixels with a Y-up origin (bottom-left),
 * but our {@link PixelRect}s are Y-down (top-left). The stack flips Y using
 * the current framebuffer height supplied via {@link #beginFrame(int)}.
 * <p>
 * Whoever pushes/pops MUST first flush the batcher — drawing commands carry
 * implicit "current scissor" state, and changing it mid-batch would clip the
 * already-buffered geometry inconsistently.
 */
public final class ScissorStack {

    private final List<PixelRect> stack = new ArrayList<>();
    private int framebufferHeight;
    private boolean glEnabled = false;

    public void beginFrame(int framebufferHeightPx) {
        this.framebufferHeight = framebufferHeightPx;
        stack.clear();
        disable();
    }

    public void push(PixelRect rect) {
        PixelRect effective = stack.isEmpty() ? rect : intersect(stack.get(stack.size() - 1), rect);
        stack.add(effective);
        apply(effective);
    }

    public void pop() {
        if (stack.isEmpty()) return;
        stack.remove(stack.size() - 1);
        if (stack.isEmpty()) disable();
        else                 apply(stack.get(stack.size() - 1));
    }

    public int depth() { return stack.size(); }

    private void apply(PixelRect r) {
        if (!glEnabled) {
            Gl.glEnable(GL_SCISSOR_TEST);
            glEnabled = true;
        }
        int x = (int) r.x();
        int y = framebufferHeight - (int) (r.y() + r.height());
        int w = (int) Math.max(0f, r.width());
        int h = (int) Math.max(0f, r.height());
        Gl.glScissor(x, y, w, h);
    }

    private void disable() {
        if (glEnabled) {
            Gl.glDisable(GL_SCISSOR_TEST);
            glEnabled = false;
        }
    }

    private static PixelRect intersect(PixelRect a, PixelRect b) {
        float x = Math.max(a.x(), b.x());
        float y = Math.max(a.y(), b.y());
        float right  = Math.min(a.right(),  b.right());
        float bottom = Math.min(a.bottom(), b.bottom());
        return new PixelRect(x, y, Math.max(0f, right - x), Math.max(0f, bottom - y));
    }
}
