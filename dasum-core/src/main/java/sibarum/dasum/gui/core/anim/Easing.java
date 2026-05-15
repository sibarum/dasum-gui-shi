package sibarum.dasum.gui.core.anim;

/**
 * Easing curves for tween animations. {@code t} is normalized progress in
 * [0, 1]; return is the eased progress, typically also in [0, 1] but not
 * required (e.g. spring-like ease curves can overshoot).
 */
@FunctionalInterface
public interface Easing {
    float apply(float t);

    Easing LINEAR          = t -> t;
    Easing EASE_IN_QUAD    = t -> t * t;
    Easing EASE_OUT_QUAD   = t -> 1f - (1f - t) * (1f - t);
    Easing EASE_IN_OUT_QUAD = t -> t < 0.5f ? 2f * t * t : 1f - 2f * (1f - t) * (1f - t);
    Easing EASE_OUT_CUBIC  = t -> { float u = 1f - t; return 1f - u * u * u; };
}
