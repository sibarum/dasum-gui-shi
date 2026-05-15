package sibarum.dasum.gui.core.anim;

import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.reactive.Property;

import java.util.Objects;

/**
 * Reactive property whose value transitions toward a target over time
 * rather than snapping. Components read {@link #get()} each frame to see
 * the current interpolated value.
 * <p>
 * Interrupt semantics: calling {@link #set(Object)} while a previous
 * animation is in flight re-targets from the CURRENT interpolated value,
 * producing a smooth handoff (no snap to start, no snap to old target).
 */
public final class Animated<T> {

    private final Property<T> property;
    private final Interpolator<T> interpolator;
    private final Transition defaultTransition;

    private T fromValue;
    private T toValue;
    private long startNanos;
    private Transition active;
    private boolean animating;

    public Animated(T initial, Interpolator<T> interpolator, Transition defaultTransition) {
        this.property = new Property<>(initial);
        this.interpolator = interpolator;
        this.defaultTransition = defaultTransition;
    }

    public T get() { return property.get(); }

    public void set(T target) { set(target, defaultTransition); }

    public void set(T target, Transition transition) {
        if (Objects.equals(property.get(), target) && !animating) return;
        fromValue = property.get();
        toValue = target;
        startNanos = System.nanoTime();
        active = transition;
        animating = true;
        AnimationManager.register(this);
        Invalidator.invalidate();
    }

    /** Returns true while still animating; false when settled. */
    boolean tick(long nowNanos) {
        if (!animating) return false;
        return switch (active) {
            case Transition.Tween tw -> tickTween(tw, nowNanos);
        };
    }

    private boolean tickTween(Transition.Tween tw, long nowNanos) {
        float elapsed = (nowNanos - startNanos) / 1_000_000_000f;
        float t = Math.min(1f, elapsed / tw.durationSeconds());
        float eased = tw.easing().apply(t);
        T value = interpolator.interpolate(fromValue, toValue, eased);
        property.set(value);
        if (t >= 1f) {
            animating = false;
            return false;
        }
        return true;
    }

    /** Seconds until this animator next requires a frame, or +∞ when settled. */
    double secondsUntilNextDeadline(long nowNanos) {
        if (!animating) return Double.POSITIVE_INFINITY;
        return switch (active) {
            case Transition.Tween tw -> {
                float elapsed = (nowNanos - startNanos) / 1_000_000_000f;
                yield Math.max(0d, tw.durationSeconds() - elapsed);
            }
        };
    }
}
