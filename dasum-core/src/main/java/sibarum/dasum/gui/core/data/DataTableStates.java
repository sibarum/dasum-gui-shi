package sibarum.dasum.gui.core.data;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Identity-keyed sidecar holding the transient interaction state for each
 * {@code Component.DataTable} — scroll offset, current selection, edit
 * cursor, hover row/col, anchor for shift-extend selections.
 * <p>
 * Mirrors the {@code TextStates} / {@code ScrollStates} pattern: one
 * mutable {@link DataTableState} object per DataTable instance, looked up
 * by identity. Two DataTables sharing one {@link DataTableSource} have
 * independent state.
 *
 * <p>The selection mirror — the {@code Component.DataTable.selection}
 * {@link sibarum.dasum.gui.core.reactive.Property} — is updated whenever
 * {@link #setSelection} runs so subscribers see selection changes without
 * having to poll this sidecar.
 */
public final class DataTableStates {

    /**
     * Per-table mutable state. Public fields rather than getters/setters
     * to match the existing TextState / ScrollPosition idiom — the
     * sidecar pattern leans on direct field access from controllers.
     */
    public static final class DataTableState {
        /** Pixel-space scroll offsets. Snapped to integers in the renderer to avoid header-seam sub-pixel artifacts. */
        public float scrollPxX = 0f;
        public float scrollPxY = 0f;

        /** Current selection (read-only mirror of the Property). null = no selection. */
        public TableSelection selection = null;

        /** Anchor row/col for shift-extend. -1 = none. */
        public int anchorRow = -1;
        public int anchorCol = -1;

        /** Cell currently being edited inline. -1 = not editing. */
        public int editingRow = -1;
        public int editingCol = -1;

        /** Mutable edit buffer for the in-progress cell edit. Only meaningful when editingRow >= 0. */
        public StringBuilder editBuffer = new StringBuilder();
        /** Caret index into {@link #editBuffer}. */
        public int editCaret = 0;

        /** Last hovered row/col for cursor visuals. -1 = none. */
        public int hoverRow = -1;
        public int hoverCol = -1;

        DataTableState() {}
    }

    private static final Map<Component, DataTableState> STATES = new IdentityHashMap<>();

    private DataTableStates() {}

    /**
     * Returns the state for {@code table}, creating it on first access.
     * Matches {@code TextStates.of} / {@code ScrollStates.of} semantics —
     * sidecar entries are lazy.
     */
    public static DataTableState of(Component.DataTable table) {
        if (table == null) throw new IllegalArgumentException("table must not be null");
        return STATES.computeIfAbsent(table, k -> new DataTableState());
    }

    /**
     * Write a new selection, mirror it to the table's selection Property,
     * and invalidate. Use this rather than mutating
     * {@code DataTableState.selection} directly so subscribers fire.
     */
    public static void setSelection(Component.DataTable table, TableSelection sel) {
        DataTableState s = of(table);
        s.selection = sel;
        if (table.selection() != null) table.selection().set(sel);
        Invalidator.invalidate();
    }

    /** Per-component cleanup hook called by {@code Components.detach}. */
    public static void clear(Component c) {
        if (STATES.remove(c) != null) {
            Invalidator.invalidate();
        }
    }

    /** Migrate state to a new component identity (used by {@code Components.migrateState}). */
    public static void migrate(Component from, Component to) {
        DataTableState s = STATES.get(from);
        if (s != null) STATES.put(to, s);
    }
}
