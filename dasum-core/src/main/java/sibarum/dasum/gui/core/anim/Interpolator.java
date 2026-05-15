package sibarum.dasum.gui.core.anim;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.Vec2;

/**
 * Linear blend between two values. Per-type implementations live as static
 * fields here. Custom interpolators can be passed when constructing an
 * {@link Animated}.
 */
@FunctionalInterface
public interface Interpolator<T> {
    T interpolate(T from, T to, float t);

    Interpolator<Float> FLOAT = (a, b, t) -> a + (b - a) * t;
    Interpolator<Double> DOUBLE = (a, b, t) -> a + (b - a) * t;
    Interpolator<Vec2> VEC2 = (a, b, t) -> new Vec2(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
    Interpolator<Color> COLOR = (a, b, t) -> new Color(
        a.r() + (b.r() - a.r()) * t,
        a.g() + (b.g() - a.g()) * t,
        a.b() + (b.b() - a.b()) * t,
        a.a() + (b.a() - a.a()) * t);
}
