package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.render.Color;

/**
 * A port's type tag — identifies what kind of value (or relationship) flows
 * through the port and which other ports it can connect to.
 * <p>
 * Open registry: apps create their own {@code PortType} instances for their
 * domain. The framework supplies only {@link #ANY} — a wildcard that
 * connects to any other type. Compatibility is checked via
 * {@link PortTypeCompat#check}; the default rule is "either side is ANY,
 * or both sides have the same {@code id}." Apps can override the rule
 * globally for richer semantics (subtyping, implicit conversions, etc.).
 *
 * @param id           stable identifier used for compatibility checks
 * @param displayName  human-readable label
 * @param color        port + connection-line color
 */
public record PortType(String id, String displayName, Color color) {

    /**
     * Wildcard type — compatible with every other type, including itself.
     * Use sparingly: typed ports give better error messages and UX. ANY is
     * for genuinely polymorphic ports (e.g. a "Debug" node that accepts
     * anything to print).
     */
    public static final PortType ANY = new PortType(
        "any", "Any", new Color(0.70f, 0.70f, 0.75f, 1f)
    );

    /** Convenience factory. */
    public static PortType of(String id, String displayName, Color color) {
        return new PortType(id, displayName, color);
    }
}
