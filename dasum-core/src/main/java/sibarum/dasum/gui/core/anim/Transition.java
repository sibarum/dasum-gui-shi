package sibarum.dasum.gui.core.anim;

/**
 * Declarative description of how a value should travel from one state to
 * another. Sealed so the animator can switch exhaustively over variants.
 * <p>
 * For M3 there is one variant: {@link Tween}. Spring is a planned future
 * variant — same interface, different mathematics.
 */
public sealed interface Transition permits Transition.Tween {

    /**
     * Duration-bounded interpolation with an easing curve.
     *
     * @param durationSeconds total time to animate from start to target
     * @param easing          curve shaping progress in [0, 1]
     */
    record Tween(float durationSeconds, Easing easing) implements Transition {}

    static Tween tween(float durationSeconds) {
        return new Tween(durationSeconds, Easing.LINEAR);
    }

    static Tween tween(float durationSeconds, Easing easing) {
        return new Tween(durationSeconds, easing);
    }
}
