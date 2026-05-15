package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Per-{@link Component.GraphSurface GraphSurface} child position registry.
 * Positions are stored in raw em (float) values so drag updates can write
 * fractional positions without {@link Em} allocation churn.
 * <p>
 * Both the outer surface and the child are looked up by reference identity
 * (matches the existing sidecar pattern: {@code ScrollStates},
 * {@code TextStates}, …). Unset positions default to {@code (0, 0)}.
 */
public final class GraphSurfacePositions {

    public record Pos(float emX, float emY) {}

    private static final Map<Component, Map<Component, Pos>> POSITIONS = new IdentityHashMap<>();

    private GraphSurfacePositions() {}

    public static Pos of(Component surface, Component child) {
        Map<Component, Pos> map = POSITIONS.get(surface);
        if (map == null) return new Pos(0f, 0f);
        Pos p = map.get(child);
        return p != null ? p : new Pos(0f, 0f);
    }

    public static void set(Component surface, Component child, float emX, float emY) {
        POSITIONS.computeIfAbsent(surface, k -> new IdentityHashMap<>()).put(child, new Pos(emX, emY));
        Invalidator.invalidate();
    }

    public static void setEm(Component surface, Component child, Em x, Em y) {
        set(surface, child, x.value(), y.value());
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Handles
     * both surface-keyed entries (drop the entire per-surface map) and
     * child-keyed entries (remove {@code c} from every surface's map).
     */
    public static void clear(Component c) {
        if (POSITIONS.remove(c) != null) {
            Invalidator.invalidate();
            return;
        }
        boolean changed = false;
        for (Map<Component, Pos> map : POSITIONS.values()) {
            if (map.remove(c) != null) changed = true;
        }
        if (changed) Invalidator.invalidate();
    }

    /** Migrate positions for {@code from} (handles both surface and child roles). */
    public static void migrate(Component from, Component to) {
        Map<Component, Pos> surfaceMap = POSITIONS.get(from);
        if (surfaceMap != null) {
            POSITIONS.put(to, new IdentityHashMap<>(surfaceMap));
        }
        for (Map<Component, Pos> map : POSITIONS.values()) {
            Pos p = map.get(from);
            if (p != null) map.put(to, p);
        }
    }
}
