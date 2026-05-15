package sibarum.dasum.gui.core.debug;

import sibarum.dasum.gui.core.component.Component;

import java.util.function.Supplier;

/**
 * Minimal opt-in framework log. Enabled by the
 * {@code -Ddasum.debug=true} system property; no-op otherwise.
 *
 * <p>Used by call sites where identity-keyed state lookups happen
 * (context-menu resolution, click dispatch, connection events). The
 * canonical reproduction it surfaces is the "identity, never equals"
 * footgun: an app registers state on a {@code Component} via
 * {@link sibarum.dasum.gui.core.input.Handlers},
 * {@link sibarum.dasum.gui.core.graph.Ports}, etc., then transforms
 * that component via a {@code with*} method before placing it in the
 * render tree. The new record instance has a fresh identity, so the
 * sidecar lookup misses. Logs look like:
 *
 * <pre>
 *   [dasum/handlers] onClick(GraphSurface#0x4f1c2)
 *   …user clicks at runtime…
 *   [dasum/ctx-menu] hit GraphSurface#0xa7d31 — different identity
 *   [dasum/ctx-menu] no provider on hit path
 * </pre>
 *
 * The two GraphSurface ids differ — a {@code with*} was applied after
 * the {@code onClick} registration. Without this log, the symptom is
 * just "nothing happens on right-click."
 *
 * <p>Identity for a Component is {@code System.identityHashCode(c)}
 * formatted as hex, prefixed with the variant class's simple name.
 */
public final class Dbg {

    private static final boolean ENABLED = Boolean.getBoolean("dasum.debug");

    private Dbg() {}

    public static boolean enabled() { return ENABLED; }

    public static void log(String area, String msg) {
        if (ENABLED) emit(area, msg);
    }

    /** Lazy-evaluated variant for messages that compose strings. */
    public static void log(String area, Supplier<String> msg) {
        if (ENABLED) emit(area, msg.get());
    }

    private static void emit(String area, String msg) {
        System.err.println("[dasum/" + area + "] " + msg);
    }

    /**
     * Compact identity-printing format for a Component. Includes the
     * variant simple name plus the JVM identity hash code in hex.
     * {@code null} returns the string {@code "null"}.
     */
    public static String id(Component c) {
        if (c == null) return "null";
        return c.getClass().getSimpleName() + "#0x"
            + Integer.toHexString(System.identityHashCode(c));
    }
}
