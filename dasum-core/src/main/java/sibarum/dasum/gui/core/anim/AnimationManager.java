package sibarum.dasum.gui.core.anim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Global registry of active {@link Animated} instances. Drives them once
 * per frame and reports the soonest deadline back to the event loop so
 * it knows when to wake up.
 * <p>
 * Single-threaded by contract — all access happens on the GLFW main
 * thread. The {@link Animated#set} call that registers an animator may
 * be invoked from arbitrary threads in principle, but the {@link Animated}
 * itself only mutates from the main thread (set is invoked by the key
 * callback which fires on the main thread inside glfwWaitEventsTimeout).
 */
public final class AnimationManager {

    private static final List<Animated<?>> ACTIVE = new ArrayList<>();

    private AnimationManager() {}

    static void register(Animated<?> a) {
        for (Animated<?> existing : ACTIVE) {
            if (existing == a) return;
        }
        ACTIVE.add(a);
    }

    public static void tick(long nowNanos) {
        Iterator<Animated<?>> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (!it.next().tick(nowNanos)) it.remove();
        }
    }

    public static double secondsUntilNextDeadline(long nowNanos) {
        if (ACTIVE.isEmpty()) return Double.POSITIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        for (Animated<?> a : ACTIVE) {
            double d = a.secondsUntilNextDeadline(nowNanos);
            if (d < min) min = d;
        }
        return min;
    }
}
