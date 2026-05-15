package sibarum.dasum.gui.core.event;

import sibarum.dasum.gui.core.anim.AnimationManager;
import sibarum.dasum.gui.core.window.Window;
import sibarum.dasum.gui.natives.glfw.Glfw;

/**
 * Single-window event loop with dynamic refresh limiting. Blocks in
 * {@code glfwWaitEventsTimeout} until either an event arrives or the next
 * animation deadline elapses. Idle means no events and no active
 * animations — in that state the loop renders zero frames and consumes
 * negligible CPU.
 * <p>
 * Each loop iteration: wait → advance animations → render if dirty or
 * animating. Cross-thread invalidate goes through
 * {@link Invalidator#invalidate()} → {@code glfwPostEmptyEvent}.
 */
public final class EventLoop {

    private final Window window;
    private final Renderer renderer;
    private double maxRefreshHz = 120.0;

    private long idleFrameCount = 0;
    private long renderedFrameCount = 0;

    public EventLoop(Window window, Renderer renderer) {
        this.window = window;
        this.renderer = renderer;
    }

    public void setMaxRefreshHz(double hz) {
        if (hz <= 0) throw new IllegalArgumentException("hz must be > 0");
        this.maxRefreshHz = hz;
    }

    public long renderedFrameCount() { return renderedFrameCount; }
    public long idleFrameCount() { return idleFrameCount; }

    public void run() {
        Invalidator.bindMainThread();
        Invalidator.forceDirty();

        double frameIntervalSec = 1.0 / maxRefreshHz;

        while (!window.shouldClose()) {
            long now = System.nanoTime();
            double animDeadline = AnimationManager.secondsUntilNextDeadline(now);

            if (animDeadline == Double.POSITIVE_INFINITY) {
                Glfw.glfwWaitEvents();
            } else {
                Glfw.glfwWaitEventsTimeout(Math.max(0d, Math.min(animDeadline, frameIntervalSec)));
            }

            long postWait = System.nanoTime();
            AnimationManager.tick(postWait);

            boolean dirty = Invalidator.consumeDirty();
            boolean animating = AnimationManager.secondsUntilNextDeadline(postWait) < Double.POSITIVE_INFINITY;

            if (dirty || animating) {
                renderer.renderFrame();
                window.swapBuffers();
                renderedFrameCount++;
            } else {
                idleFrameCount++;
            }
        }
    }

    @FunctionalInterface
    public interface Renderer { void renderFrame(); }
}
