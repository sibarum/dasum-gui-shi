package sibarum.dasum.gui.core.event;

import sibarum.dasum.gui.natives.glfw.Glfw;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global dirty flag + cross-thread wake primitive. Any thread can call
 * {@link #invalidate()} to request a render. If called from a thread other
 * than the GLFW main thread, {@code glfwPostEmptyEvent} wakes the event loop.
 *
 * Race-free pattern: set the dirty flag BEFORE posting the empty event. The
 * event loop checks the flag on every wake-up regardless of source.
 */
public final class Invalidator {

    private static final AtomicBoolean DIRTY = new AtomicBoolean(true);
    /**
     * Tracks whether a cross-thread wakeup event has been posted and is
     * still pending consumption by the main thread. Coalesces bursts of
     * {@link #invalidate} from background threads into at most one
     * queued GLFW event, so the Windows message queue can't be flooded
     * by a high-frequency training worker (Property.set → invalidate →
     * glfwPostEmptyEvent at thousands of Hz was empirically overwhelming
     * Windows / GLFW's helper-window message pump, causing
     * glfwWaitEvents to stop returning).
     */
    private static final AtomicBoolean PENDING_WAKE = new AtomicBoolean(false);
    private static volatile Thread mainThread;

    private Invalidator() {}

    public static void bindMainThread() {
        mainThread = Thread.currentThread();
    }

    public static void invalidate() {
        DIRTY.set(true);
        if (mainThread != null && Thread.currentThread() != mainThread) {
            // Only post a fresh wakeup if there isn't already one in flight.
            if (PENDING_WAKE.compareAndSet(false, true)) {
                Glfw.glfwPostEmptyEvent();
            }
        }
    }

    public static boolean consumeDirty() {
        return DIRTY.getAndSet(false);
    }

    /**
     * Called by the event loop after returning from glfwWaitEvents.
     * Clears the pending-wake flag so the next cross-thread
     * {@link #invalidate} call may post a fresh wakeup event.
     */
    public static void consumePendingWake() {
        PENDING_WAKE.set(false);
    }

    public static void forceDirty() {
        DIRTY.set(true);
    }
}
