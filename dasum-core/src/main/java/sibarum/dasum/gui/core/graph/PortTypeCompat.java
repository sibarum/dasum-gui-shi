package sibarum.dasum.gui.core.graph;

import java.util.function.BiPredicate;

/**
 * Process-global port-type compatibility rule. Default: either side is
 * {@link PortType#ANY}, or both sides have the same {@code id}. Apps may
 * {@link #override} to implement richer semantics (subtype hierarchies,
 * implicit conversions, runtime type coercion, etc.).
 * <p>
 * The framework calls {@link #check} during connection validation. The
 * rule should be reflexive ({@code check(t, t)} → true) and symmetric
 * ({@code check(a, b) == check(b, a)}); the framework does not enforce
 * either but app-level UX will get strange if they're violated.
 */
public final class PortTypeCompat {

    private static BiPredicate<PortType, PortType> rule = defaultRule();

    private PortTypeCompat() {}

    /** Returns true if a connection from a port of type {@code out} to a port of type {@code in} is allowed. */
    public static boolean check(PortType out, PortType in) {
        return rule.test(out, in);
    }

    /** Replace the global compatibility rule. {@code null} resets to the default. */
    public static void override(BiPredicate<PortType, PortType> custom) {
        rule = (custom != null) ? custom : defaultRule();
    }

    private static BiPredicate<PortType, PortType> defaultRule() {
        return (a, b) -> a == PortType.ANY || b == PortType.ANY || a.id().equals(b.id());
    }
}
