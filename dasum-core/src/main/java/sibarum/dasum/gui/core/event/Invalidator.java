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
    private static volatile Thread mainThread;

    private Invalidator() {}

    public static void bindMainThread() {
        mainThread = Thread.currentThread();
    }

    public static void invalidate() {
        DIRTY.set(true);
        if (mainThread != null && Thread.currentThread() != mainThread) {
            Glfw.glfwPostEmptyEvent();
        }
    }

    public static boolean consumeDirty() {
        return DIRTY.getAndSet(false);
    }

    public static void forceDirty() {
        DIRTY.set(true);
    }
}
