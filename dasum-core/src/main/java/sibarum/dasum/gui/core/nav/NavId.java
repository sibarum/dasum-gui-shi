package sibarum.dasum.gui.core.nav;

import sibarum.dasum.gui.core.component.Component;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Identity-keyed registry mapping a {@link Component} instance to a durable
 * navigation ID. Tag a component at the point it's built — like attaching a
 * click handler — and the {@link Navigator} can later resolve that ID back to
 * whatever instance currently occupies the tree:
 *
 * <pre>
 *   Component slider = NavId.tag(Themed.slider(...), "widgets.volume");
 * </pre>
 *
 * <p>Identity comparison is essential: two structurally-identical records would
 * otherwise share a tag (see [[record-equality-trap]] in project memory).
 * Because components are immutable records re-created on every tree rebuild,
 * apps re-tag on each build; {@link Navigator} always resolves against the live
 * tree, so stale entries for discarded instances are harmless and get evicted
 * by {@link sibarum.dasum.gui.core.component.Components#detach}.
 */
public final class NavId {

    private static final Map<Component, String> IDS = new IdentityHashMap<>();

    private NavId() {}

    /** Tag {@code c} with {@code id}; returns {@code c} for fluent build-site wiring. */
    public static Component tag(Component c, String id) {
        if (c != null && id != null) IDS.put(c, id);
        return c;
    }

    /** The navigation ID tagged on {@code c}, or {@code null} if untagged. */
    public static String idOf(Component c) {
        return IDS.get(c);
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops any
     * stored tag for {@code c}.
     */
    public static void clear(Component c) {
        IDS.remove(c);
    }

    /** Copy {@code from}'s tag to {@code to}. */
    public static void migrate(Component from, Component to) {
        String id = IDS.get(from);
        if (id != null) IDS.put(to, id);
    }
}
