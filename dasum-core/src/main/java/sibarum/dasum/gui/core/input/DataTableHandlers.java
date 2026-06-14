package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-{@code Component.DataTable} <em>activation</em> handlers — the discrete
 * "the user committed to a cell/row" event that complements the continuous
 * selection state carried by the table's
 * {@code Property<TableSelection>}.
 *
 * <p>Mirrors {@code PointHandlers} (dasum-vis) and the generic {@link Handlers}
 * sidecar: an identity-keyed registry the application populates, invoked by
 * {@link DataTableSelectionController} when a double-click or an Enter press
 * resolves on the table. Before this existed, apps had to hand-roll
 * double-click timing in their own GLFW callbacks and intercept Enter ahead of
 * the controller (because the controller consumes it for inline editing) — the
 * navigation gesture lived in app code rather than travelling with the table.
 *
 * <p><b>Why a separate registry rather than {@code Handlers.onClick}?</b>
 * {@code Handlers} fires for a component as an opaque whole; a DataTable is a
 * leaf, so a generic click can't say <em>which</em> row/cell. Activation needs
 * the resolved {@code (row, col, region)}, so — exactly like
 * {@code PointHandlers} for picked points — it gets its own typed payload.
 *
 * <p><b>Scope.</b> This registry covers activation only. Selection
 * (hover / click / drag / shift-extend) remains observable through the
 * component's selection {@code Property}; the two event kinds intentionally do
 * not overlap.
 */
public final class DataTableHandlers {

    /**
     * A user activation of a table cell.
     *
     * <p>Field validity depends on {@link #region}:
     * <ul>
     *   <li>{@code BODY} — both {@link #row} and {@link #col} are valid.</li>
     *   <li>{@code GUTTER} — {@link #row} is valid; {@link #col} is {@code -1}.</li>
     *   <li>{@code HEADER} — {@link #col} is valid; {@link #row} is {@code -1}.</li>
     *   <li>{@code CORNER} — neither is meaningful (both {@code 0}).</li>
     * </ul>
     * Keyboard activation always reports {@code BODY} (the selection cursor).
     *
     * @param table  the table that was activated
     * @param row    activated row (see region rules above)
     * @param col    activated column (see region rules above)
     * @param region which region of the table the activation landed in
     * @param source whether it came from a mouse double-click or the keyboard
     */
    public record Activation(
        Component.DataTable table, int row, int col,
        DataTableSelectionController.Region region, Source source
    ) {
        public enum Source { MOUSE, KEYBOARD }
    }

    private static final Map<Component, Consumer<Activation>> HANDLERS = new IdentityHashMap<>();

    private DataTableHandlers() {}

    /**
     * Register (or replace) the activation handler for {@code table}. Pass
     * {@code null} to remove it. Registering a handler also switches the
     * Enter key from "begin inline edit" to "activate" for that table (F2
     * still edits) — see {@link DataTableSelectionController}.
     */
    public static void onActivate(Component.DataTable table, Consumer<Activation> handler) {
        if (handler == null) HANDLERS.remove(table);
        else HANDLERS.put(table, handler);
    }

    /** The registered handler for {@code table}, or {@code null} if none. */
    public static Consumer<Activation> handlerFor(Component table) {
        return HANDLERS.get(table);
    }

    /** Per-component cleanup hook called by {@code Components.detach}. */
    public static void clear(Component c) {
        HANDLERS.remove(c);
    }

    /** Migrate state to a new component identity (used by {@code Components.migrateState}). */
    public static void migrate(Component from, Component to) {
        Consumer<Activation> h = HANDLERS.get(from);
        if (h != null) HANDLERS.put(to, h);
    }
}
