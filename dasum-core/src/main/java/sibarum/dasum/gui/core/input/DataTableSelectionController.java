package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.data.DataTableClipboard;
import sibarum.dasum.gui.core.data.DataTableSource;
import sibarum.dasum.gui.core.data.DataTableStates;
import sibarum.dasum.gui.core.data.TableSelection;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.lang.foreign.MemorySegment;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * Mouse + keyboard dispatch for {@code Component.DataTable}. Slots into
 * {@code App.wireInput} alongside {@code GraphSurfaceController} —
 * scroll-wheel routes here when the wheel-hit lands on a table; key
 * routing checks {@link #hasActiveTable} after the text-input controller
 * has refused.
 *
 * <p>Selection model:
 * <ul>
 *   <li>Click on body cell → single-cell selection; remembers anchor.</li>
 *   <li>Shift+click → extend to a block from anchor.</li>
 *   <li>Drag past 4px² → upgrade to block selection (rectangle from anchor to drag cursor).</li>
 *   <li>Click on column header → entire column selected.</li>
 *   <li>Click on row gutter → entire row selected.</li>
 *   <li>Click on top-left corner → entire sheet ({@link TableSelection.Mode#ALL}).</li>
 * </ul>
 *
 * <p>Editing is inline: F2 / Enter on a single-cell selection seeds an
 * edit buffer from the source's current value. Subsequent character input
 * mutates the buffer; Enter commits (calls {@link DataTableSource#trySet})
 * and moves down; Tab commits and moves right; Esc cancels.
 */
public final class DataTableSelectionController {

    private static final float DRAG_THRESHOLD_PX_SQ = 4f * 4f;

    /** Max gap between two presses on the same cell to count as a double-click. */
    private static final long DOUBLE_CLICK_NANOS = 400_000_000L; // 400 ms

    /** Region of the table the cursor hit on press. */
    public enum Region { BODY, HEADER, GUTTER, CORNER, NONE }

    private static Component.DataTable pressTable = null;
    private static Region pressRegion = Region.NONE;
    private static double pressX = 0d, pressY = 0d;
    private static int pressRow = -1, pressCol = -1;
    private static boolean dragStarted = false;

    // Double-click tracking — the prior qualifying press, so a second press on
    // the same cell within DOUBLE_CLICK_NANOS fires an activation. Centralized
    // here (rather than re-implemented in each app's GLFW callbacks) so the
    // table owns its own activation gesture. See {@link DataTableHandlers}.
    private static Component.DataTable lastClickTable = null;
    private static int  lastClickRow   = -1;
    private static int  lastClickCol   = -1;
    private static long lastClickNanos = 0L;

    private DataTableSelectionController() {}

    /** Whether a DataTable currently owns selection / edit focus. Used by other key handlers to defer. */
    public static boolean hasActiveTable() {
        return activeTable() != null;
    }

    /**
     * Currently-active table — the one whose selection / edit state should
     * receive keystrokes. Prefers the pressed table during a drag; otherwise
     * returns whichever table is currently focused (set by
     * {@link FocusState} on mouse-down). With two tables on screen,
     * disambiguation is "whichever was clicked most recently" because
     * clicking moves focus.
     */
    public static Component.DataTable activeTable() {
        if (pressTable != null) return pressTable;
        Component focused = FocusState.focused();
        return (focused instanceof Component.DataTable dt) ? dt : null;
    }

    // ---------- mouse ----------

    /**
     * On mouse-down: if the hit is inside a DataTable, claim the press and
     * update selection / anchor accordingly. Returns {@code true} to
     * consume the press.
     */
    public static boolean onMouseDown(double cursorX, double cursorY, boolean shift) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return false;
        Component root = OverlayStack.activeInputRoot(LatestLayout.root());
        if (root == null) return false;
        Component hit = HitTest.test(root, lr, (float) cursorX, (float) cursorY);
        if (!(hit instanceof Component.DataTable table)) return false;

        PixelRect rect = lr.rectOf(table);
        if (rect == null) return false;

        DataTableStates.DataTableState s = DataTableStates.of(table);
        // Resolve which region of the table was hit + the row/col under cursor.
        HitInfo info = resolveHit(table, rect, s, cursorX, cursorY);
        pressTable  = table;
        pressRegion = info.region;
        pressRow    = info.row;
        pressCol    = info.col;
        pressX      = cursorX;
        pressY      = cursorY;
        dragStarted = false;
        FocusState.set(table);

        // Commit any in-progress edit before changing selection.
        commitEdit(table);

        TableSelection sel;
        switch (info.region) {
            case BODY -> {
                if (shift && s.anchorRow >= 0 && s.anchorCol >= 0) {
                    sel = TableSelection.block(s.anchorRow, s.anchorCol, info.row, info.col);
                } else {
                    sel = TableSelection.cell(info.row, info.col);
                    s.anchorRow = info.row;
                    s.anchorCol = info.col;
                }
            }
            case HEADER -> {
                if (shift && s.anchorCol >= 0) {
                    sel = TableSelection.columns(s.anchorCol, info.col);
                } else {
                    sel = TableSelection.columns(info.col, info.col);
                    s.anchorRow = 0;
                    s.anchorCol = info.col;
                }
            }
            case GUTTER -> {
                if (shift && s.anchorRow >= 0) {
                    sel = TableSelection.rows(s.anchorRow, info.row);
                } else {
                    sel = TableSelection.rows(info.row, info.row);
                    s.anchorRow = info.row;
                    s.anchorCol = 0;
                }
            }
            case CORNER -> {
                sel = TableSelection.all();
                s.anchorRow = 0;
                s.anchorCol = 0;
            }
            default -> sel = null;
        }
        if (sel != null) DataTableStates.setSelection(table, sel);

        // Double-click → activation. Only tracked / fired when the app has
        // registered an activation handler; otherwise a double-click is just
        // two ordinary selecting clicks (preserves prior behavior for tables
        // that don't opt in). Shift-clicks extend selection, never activate.
        if (!shift && info.region != Region.NONE && DataTableHandlers.handlerFor(table) != null) {
            long now = System.nanoTime();
            boolean sameSpot = table == lastClickTable
                && info.row == lastClickRow && info.col == lastClickCol;
            if (sameSpot && (now - lastClickNanos) < DOUBLE_CLICK_NANOS) {
                fireActivation(table, info.row, info.col, info.region,
                    DataTableHandlers.Activation.Source.MOUSE);
                // Reset so a rapid third click doesn't re-fire as another pair.
                lastClickTable = null; lastClickRow = -1; lastClickCol = -1; lastClickNanos = 0L;
            } else {
                lastClickTable = table; lastClickRow = info.row;
                lastClickCol = info.col; lastClickNanos = now;
            }
        }
        return true;
    }

    /** Deliver an activation to the table's registered handler, if any. */
    private static void fireActivation(Component.DataTable table, int row, int col,
                                       Region region, DataTableHandlers.Activation.Source source) {
        Consumer<DataTableHandlers.Activation> h = DataTableHandlers.handlerFor(table);
        if (h == null) return;
        h.accept(new DataTableHandlers.Activation(table, row, col, region, source));
    }

    /** On cursor-move during a press: drag past threshold upgrades to a block / range selection. */
    public static void onCursorMove(double cursorX, double cursorY) {
        if (pressTable == null) return;
        if (!dragStarted) {
            double dx = cursorX - pressX;
            double dy = cursorY - pressY;
            if (dx * dx + dy * dy < DRAG_THRESHOLD_PX_SQ) return;
            dragStarted = true;
        }
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect rect = lr.rectOf(pressTable);
        if (rect == null) return;
        DataTableStates.DataTableState s = DataTableStates.of(pressTable);
        HitInfo info = resolveHit(pressTable, rect, s, cursorX, cursorY);
        // Project hit onto the appropriate axis based on the press region.
        TableSelection sel = switch (pressRegion) {
            case BODY    -> TableSelection.block(pressRow, pressCol, info.row, info.col);
            case HEADER  -> TableSelection.columns(pressCol, info.col >= 0 ? info.col : pressCol);
            case GUTTER  -> TableSelection.rows(pressRow,    info.row >= 0 ? info.row : pressRow);
            case CORNER  -> TableSelection.all();
            default      -> null;
        };
        if (sel != null) DataTableStates.setSelection(pressTable, sel);
    }

    /** Release ends the press; double-clicks on the same body cell start an inline edit. */
    public static void onMouseUp() {
        // (Double-click handling intentionally omitted — kept simple for v1.
        // F2 starts edit; double-click can be wired by the App's click
        // dispatcher when needed.)
        pressTable = null;
        pressRegion = Region.NONE;
        dragStarted = false;
    }

    public static boolean isDragging() { return pressTable != null; }

    public static void cancelDrag() {
        pressTable = null;
        pressRegion = Region.NONE;
        dragStarted = false;
    }

    // ---------- scroll wheel ----------

    /**
     * Route a scroll-wheel event to the table the cursor sits over. Returns
     * {@code true} to consume the wheel event (suppress the framework's
     * generic scroll dispatch).
     */
    public static boolean onScroll(double cursorX, double cursorY, double xOffset, double yOffset, boolean shift) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return false;
        Component root = OverlayStack.activeInputRoot(LatestLayout.root());
        if (root == null) return false;
        Component hit = HitTest.test(root, lr, (float) cursorX, (float) cursorY);
        if (!(hit instanceof Component.DataTable table)) return false;
        PixelRect rect = lr.rectOf(table);
        if (rect == null) return false;
        DataTableStates.DataTableState s = DataTableStates.of(table);
        float step = table.rowHeight().toPixels();

        // Decompose the wheel input into horizontal + vertical scroll
        // deltas. Three sources of horizontal motion:
        //   1. native horizontal wheel (xOffset != 0) — tilt-wheel,
        //      trackpad horizontal swipe. Honoured directly regardless
        //      of shift.
        //   2. shift held + vertical wheel — the standard "shift to
        //      scroll horizontally" affordance. Converts yOffset into
        //      horizontal motion and zeroes vertical.
        //   3. native vertical wheel (yOffset != 0) without shift —
        //      ordinary vertical scroll.
        // The original implementation dropped (1) entirely and gated (2)
        // on shift, so users with a horizontal-capable mouse / trackpad
        // saw no horizontal motion unless they also held shift.
        double dxRaw = xOffset;
        double dyRaw = yOffset;
        if (shift) {
            // Fold the dominant wheel axis into horizontal. If the user
            // genuinely scrolled horizontally with shift held, we still
            // want that to count — fall back to yOffset only when the
            // wheel didn't provide an x-axis component.
            dxRaw = (xOffset != 0d ? xOffset : yOffset);
            dyRaw = 0d;
        }

        float newScrollX = s.scrollPxX - (float) (dxRaw * step);
        float newScrollY = s.scrollPxY - (float) (dyRaw * step);

        float headerH = table.headerHeight().toPixels();
        float gutterW = table.rowNumberColumnWidth().toPixels();
        float bodyW   = Math.max(0f, rect.width()  - gutterW);
        float bodyH   = Math.max(0f, rect.height() - headerH);

        s.scrollPxX = Math.max(0f, Math.min(maxScrollX(table, bodyW), newScrollX));
        s.scrollPxY = Math.max(0f, Math.min(maxScrollY(table, bodyH), newScrollY));

        Invalidator.invalidate();
        return true;
    }

    /**
     * Pixel distance the user can scroll before the rightmost column
     * butts against the body's right edge. Negative for tables narrower
     * than the body (clamped to zero). The body width is the table's
     * full width minus the sticky row-number gutter.
     */
    private static float maxScrollX(Component.DataTable table, float bodyW) {
        DataTableSource src = table.source();
        if (src == null) return 0f;
        float total = 0f;
        for (int i = 0; i < src.columnCount(); i++) total += src.columnWidth(i).toPixels();
        return Math.max(0f, total - bodyW);
    }

    /**
     * Pixel distance the user can scroll before the last row butts
     * against the body's bottom edge. When the source's
     * {@link DataTableSource#rowCount} is unknown, we return
     * {@link Float#MAX_VALUE} — open-ended scroll behaves as before.
     */
    private static float maxScrollY(Component.DataTable table, float bodyH) {
        DataTableSource src = table.source();
        if (src == null) return 0f;
        OptionalLong rc = src.rowCount();
        if (rc.isEmpty()) return Float.MAX_VALUE;
        float total = rc.getAsLong() * table.rowHeight().toPixels();
        return Math.max(0f, total - bodyH);
    }

    // ---------- keyboard ----------

    /**
     * Route a key press to the active table. Returns {@code true} when consumed.
     * Mutates selection, scroll, edit buffer, or source data depending on the key.
     */
    public static boolean onKey(int key, int mods, MemorySegment windowHandle) {
        Component.DataTable table = activeTable();
        if (table == null) return false;
        DataTableStates.DataTableState s = DataTableStates.of(table);
        boolean shift = (mods & Glfw.GLFW_MOD_SHIFT)   != 0;
        boolean ctrl  = (mods & Glfw.GLFW_MOD_CONTROL) != 0;

        // ---- In-edit keys ----
        if (s.editingRow >= 0) {
            return handleEditKey(table, s, key, mods, windowHandle);
        }

        // ---- Out-of-edit keys ----
        // GLFW reports printable keys with their ASCII uppercase code, so
        // 'A' == GLFW_KEY_A == 65. App.java uses the char-literal idiom for
        // Ctrl-shortcuts; mirroring it here.
        if (ctrl) {
            if (key == 'C') { return onCopy(windowHandle); }
            if (key == 'X') { return onCut (windowHandle); }
            if (key == 'V') { return onPaste(windowHandle); }
            if (key == 'A') {
                DataTableStates.setSelection(table, TableSelection.all());
                return true;
            }
        }
        switch (key) {
            // F2 = 291, KP_ENTER = 335. Not in the bundled Glfw constants;
            // hardcoded to avoid bloating the native wrapper.
            case 291, Glfw.GLFW_KEY_ENTER, 335 -> {
                // F2 always edits. Enter / Keypad-Enter activate the current
                // selection when an activation handler is registered (so a
                // file browser opens the row); otherwise they begin an edit
                // (so a spreadsheet edits the cell).
                if (key != 291 && s.selection != null && DataTableHandlers.handlerFor(table) != null) {
                    fireActivation(table, currentRow(s), currentCol(s), Region.BODY,
                        DataTableHandlers.Activation.Source.KEYBOARD);
                } else {
                    beginEdit(table, s);
                }
                return true;
            }
            case Glfw.GLFW_KEY_DELETE, Glfw.GLFW_KEY_BACKSPACE -> {
                clearSelection(table, s);
                return true;
            }
            case Glfw.GLFW_KEY_LEFT  -> { moveSelection(table, s, 0, -1, shift); return true; }
            case Glfw.GLFW_KEY_RIGHT -> { moveSelection(table, s, 0,  1, shift); return true; }
            case Glfw.GLFW_KEY_UP    -> { moveSelection(table, s, -1, 0, shift); return true; }
            case Glfw.GLFW_KEY_DOWN  -> { moveSelection(table, s,  1, 0, shift); return true; }
            case Glfw.GLFW_KEY_HOME  -> { moveTo(table, s, currentRow(s), 0, shift); return true; }
            case Glfw.GLFW_KEY_END   -> { moveTo(table, s, currentRow(s), table.source().columnCount() - 1, shift); return true; }
            case Glfw.GLFW_KEY_TAB   -> { moveSelection(table, s, 0, shift ? -1 : 1, false); return true; }
            case Glfw.GLFW_KEY_ESCAPE -> {
                DataTableStates.setSelection(table, null);
                return true;
            }
            default -> { return false; }
        }
    }

    /** Character-input dispatch — only meaningful when an edit is in progress. */
    public static boolean onCharInput(int codepoint) {
        // Drop spurious chars emitted by Ctrl-chords on some platforms (e.g.
        // Ctrl+C → STX). Mirror of TextInputController's filter.
        if (InputState.ctrlHeld()) return false;
        Component.DataTable table = activeTable();
        if (table == null) return false;
        DataTableStates.DataTableState s = DataTableStates.of(table);
        if (s.editingRow < 0) {
            // Start a fresh edit seeded with the typed character if there's
            // a single-cell selection. Mirrors Excel's "start typing to edit"
            // behavior.
            if (s.selection != null && s.selection.mode() == TableSelection.Mode.CELLS
                && s.selection.rowStart() == s.selection.rowEnd()
                && s.selection.colStart() == s.selection.colEnd()
                && table.source().canEdit(s.selection.rowStart(), s.selection.colStart())) {
                s.editingRow = s.selection.rowStart();
                s.editingCol = s.selection.colStart();
                s.editBuffer.setLength(0);
                s.editCaret = 0;
                appendCodepoint(s, codepoint);
                Invalidator.invalidate();
                return true;
            }
            return false;
        }
        // In an active edit — append.
        appendCodepoint(s, codepoint);
        Invalidator.invalidate();
        return true;
    }

    // ---------- internal helpers ----------

    private record HitInfo(Region region, int row, int col) {}

    private static HitInfo resolveHit(Component.DataTable table, PixelRect rect,
                                     DataTableStates.DataTableState s,
                                     double cursorX, double cursorY) {
        float headerH = table.headerHeight().toPixels();
        float rowH    = table.rowHeight().toPixels();
        float gutterW = table.rowNumberColumnWidth().toPixels();
        float localX = (float) cursorX - rect.x();
        float localY = (float) cursorY - rect.y();
        if (localX < 0 || localY < 0 || localX > rect.width() || localY > rect.height()) {
            return new HitInfo(Region.NONE, -1, -1);
        }
        Region region;
        int row, col;
        boolean inHeader = localY < headerH;
        boolean inGutter = localX < gutterW;
        if (inHeader && inGutter) {
            region = Region.CORNER;
            row = 0;
            col = 0;
        } else if (inHeader) {
            region = Region.HEADER;
            col = colAt(table, localX - gutterW + Math.round(s.scrollPxX));
            row = -1;
        } else if (inGutter) {
            region = Region.GUTTER;
            row = (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
                (long) Math.floor((localY - headerH + Math.round(s.scrollPxY)) / rowH)));
            col = -1;
        } else {
            region = Region.BODY;
            row = (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
                (long) Math.floor((localY - headerH + Math.round(s.scrollPxY)) / rowH)));
            col = colAt(table, localX - gutterW + Math.round(s.scrollPxX));
        }
        return new HitInfo(region, row, col);
    }

    private static int colAt(Component.DataTable table, float unscrolledX) {
        DataTableSource src = table.source();
        int n = src.columnCount();
        float acc = 0f;
        for (int i = 0; i < n; i++) {
            float w = src.columnWidth(i).toPixels();
            if (unscrolledX < acc + w) return i;
            acc += w;
        }
        return Math.max(0, n - 1);
    }

    private static int currentRow(DataTableStates.DataTableState s) {
        return (s.selection == null) ? 0 : s.selection.rowEnd();
    }
    private static int currentCol(DataTableStates.DataTableState s) {
        return (s.selection == null) ? 0 : s.selection.colEnd();
    }

    private static void moveSelection(Component.DataTable table, DataTableStates.DataTableState s,
                                      int dRow, int dCol, boolean extend) {
        int r = currentRow(s) + dRow;
        int c = currentCol(s) + dCol;
        moveTo(table, s, r, c, extend);
    }

    private static void moveTo(Component.DataTable table, DataTableStates.DataTableState s,
                               int row, int col, boolean extend) {
        DataTableSource src = table.source();
        int colCount = src.columnCount();
        if (colCount <= 0) return;
        col = Math.max(0, Math.min(col, colCount - 1));
        if (row < 0) row = 0;
        OptionalLong rc = src.rowCount();
        if (rc.isPresent()) {
            long max = rc.getAsLong() - 1L;
            if (max >= 0 && row > max) row = (int) Math.min(max, Integer.MAX_VALUE);
        }
        if (extend && s.anchorRow >= 0 && s.anchorCol >= 0) {
            DataTableStates.setSelection(table, TableSelection.block(s.anchorRow, s.anchorCol, row, col));
        } else {
            DataTableStates.setSelection(table, TableSelection.cell(row, col));
            s.anchorRow = row;
            s.anchorCol = col;
        }
        ensureVisible(table, s, row, col);
    }

    private static void ensureVisible(Component.DataTable table, DataTableStates.DataTableState s,
                                      int row, int col) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect r = lr.rectOf(table);
        if (r == null) return;
        float headerH = table.headerHeight().toPixels();
        float rowH    = table.rowHeight().toPixels();
        float gutterW = table.rowNumberColumnWidth().toPixels();
        float bodyW   = Math.max(0f, r.width() - gutterW);
        float bodyH   = Math.max(0f, r.height() - headerH);

        // Vertical
        float topY = row * rowH;
        float botY = topY + rowH;
        if (topY < s.scrollPxY) s.scrollPxY = topY;
        else if (botY > s.scrollPxY + bodyH) s.scrollPxY = botY - bodyH;
        // Horizontal
        float leftX = 0f;
        for (int i = 0; i < col; i++) leftX += table.source().columnWidth(i).toPixels();
        float rightX = leftX + table.source().columnWidth(col).toPixels();
        if (leftX < s.scrollPxX) s.scrollPxX = leftX;
        else if (rightX > s.scrollPxX + bodyW) s.scrollPxX = rightX - bodyW;
        Invalidator.invalidate();
    }

    // ---------- edit ----------

    private static void beginEdit(Component.DataTable table, DataTableStates.DataTableState s) {
        if (s.selection == null || s.selection.mode() != TableSelection.Mode.CELLS) return;
        if (s.selection.rowStart() != s.selection.rowEnd()) return;
        if (s.selection.colStart() != s.selection.colEnd()) return;
        int row = s.selection.rowStart();
        int col = s.selection.colStart();
        if (!table.source().canEdit(row, col)) return;
        s.editingRow = row;
        s.editingCol = col;
        String v = table.source().get(row, col);
        s.editBuffer.setLength(0);
        if (v != null) s.editBuffer.append(v);
        s.editCaret = s.editBuffer.length();
        Invalidator.invalidate();
    }

    private static void commitEdit(Component.DataTable table) {
        DataTableStates.DataTableState s = DataTableStates.of(table);
        if (s.editingRow < 0) return;
        String value = s.editBuffer.toString();
        try {
            table.source().trySet(s.editingRow, s.editingCol, value);
        } catch (UnsupportedOperationException ignored) {
            // Source rejected — discard. Caller is welcome to detect by
            // checking source.canEdit before invoking the edit flow.
        }
        s.editingRow = -1;
        s.editingCol = -1;
        s.editBuffer.setLength(0);
        s.editCaret = 0;
        Invalidator.invalidate();
    }

    private static void cancelEdit(DataTableStates.DataTableState s) {
        s.editingRow = -1;
        s.editingCol = -1;
        s.editBuffer.setLength(0);
        s.editCaret = 0;
        Invalidator.invalidate();
    }

    private static boolean handleEditKey(Component.DataTable table, DataTableStates.DataTableState s,
                                         int key, int mods, MemorySegment windowHandle) {
        switch (key) {
            case Glfw.GLFW_KEY_ESCAPE -> { cancelEdit(s); return true; }
            case Glfw.GLFW_KEY_ENTER, 335 /* KP_ENTER */ -> {
                commitEdit(table);
                moveSelection(table, s, 1, 0, false);
                return true;
            }
            case Glfw.GLFW_KEY_TAB -> {
                commitEdit(table);
                moveSelection(table, s, 0, (mods & Glfw.GLFW_MOD_SHIFT) != 0 ? -1 : 1, false);
                return true;
            }
            case Glfw.GLFW_KEY_BACKSPACE -> {
                if (s.editCaret > 0) {
                    s.editBuffer.deleteCharAt(s.editCaret - 1);
                    s.editCaret--;
                    Invalidator.invalidate();
                }
                return true;
            }
            case Glfw.GLFW_KEY_DELETE -> {
                if (s.editCaret < s.editBuffer.length()) {
                    s.editBuffer.deleteCharAt(s.editCaret);
                    Invalidator.invalidate();
                }
                return true;
            }
            case Glfw.GLFW_KEY_LEFT -> {
                if (s.editCaret > 0) { s.editCaret--; Invalidator.invalidate(); }
                return true;
            }
            case Glfw.GLFW_KEY_RIGHT -> {
                if (s.editCaret < s.editBuffer.length()) { s.editCaret++; Invalidator.invalidate(); }
                return true;
            }
            case Glfw.GLFW_KEY_HOME -> { s.editCaret = 0; Invalidator.invalidate(); return true; }
            case Glfw.GLFW_KEY_END  -> { s.editCaret = s.editBuffer.length(); Invalidator.invalidate(); return true; }
            default -> { return false; }
        }
    }

    private static void appendCodepoint(DataTableStates.DataTableState s, int cp) {
        if (Character.isBmpCodePoint(cp)) {
            s.editBuffer.insert(s.editCaret, (char) cp);
            s.editCaret += 1;
        } else {
            s.editBuffer.insert(s.editCaret, Character.toChars(cp));
            s.editCaret += 2;
        }
    }

    private static void clearSelection(Component.DataTable table, DataTableStates.DataTableState s) {
        if (s.selection == null) return;
        DataTableSource src = table.source();
        int rStart = Math.max(0, s.selection.rowStart());
        int rEnd   = s.selection.rowEnd();
        int cStart = Math.max(0, s.selection.colStart());
        int cEnd   = Math.min(src.columnCount() - 1, s.selection.colEnd());
        if (src.rowCount().isPresent()) {
            long max = src.rowCount().getAsLong() - 1L;
            if (max < rEnd) rEnd = (int) Math.min(max, Integer.MAX_VALUE);
        }
        boolean anyChange = false;
        for (int r = rStart; r <= rEnd; r++) {
            for (int c = cStart; c <= cEnd; c++) {
                if (src.canEdit(r, c)) {
                    try { if (src.trySet(r, c, "")) anyChange = true; }
                    catch (UnsupportedOperationException ignored) {}
                }
            }
        }
        if (anyChange) Invalidator.invalidate();
    }

    // ---------- clipboard ----------

    public static boolean onCopy(MemorySegment windowHandle) {
        Component.DataTable table = activeTable();
        if (table == null) return false;
        DataTableStates.DataTableState s = DataTableStates.of(table);
        if (s.selection == null) return true;
        String tsv = DataTableClipboard.toTsv(table.source(), s.selection);
        Glfw.glfwSetClipboardString(windowHandle, tsv);
        return true;
    }

    public static boolean onCut(MemorySegment windowHandle) {
        if (!onCopy(windowHandle)) return false;
        Component.DataTable table = activeTable();
        if (table == null) return false;
        clearSelection(table, DataTableStates.of(table));
        return true;
    }

    public static boolean onPaste(MemorySegment windowHandle) {
        Component.DataTable table = activeTable();
        if (table == null) return false;
        DataTableStates.DataTableState s = DataTableStates.of(table);
        if (s.selection == null) return true;
        String clip = Glfw.glfwGetClipboardString(windowHandle);
        if (clip == null || clip.isEmpty()) return true;
        String[][] grid = DataTableClipboard.fromTsv(clip);
        int rStart = Math.max(0, s.selection.rowStart());
        int cStart = Math.max(0, s.selection.colStart());
        DataTableSource src = table.source();
        OptionalLong rc = src.rowCount();
        long maxRow = rc.isPresent() ? rc.getAsLong() - 1L : Long.MAX_VALUE;
        int maxCol  = src.columnCount() - 1;
        boolean anyChange = false;
        for (int r = 0; r < grid.length; r++) {
            long destR = (long) rStart + r;
            if (destR > maxRow) break;
            for (int c = 0; c < grid[r].length; c++) {
                int destC = cStart + c;
                if (destC > maxCol) break;
                int destRi = (int) Math.min(destR, Integer.MAX_VALUE);
                if (!src.canEdit(destRi, destC)) continue;
                try { if (src.trySet(destRi, destC, grid[r][c])) anyChange = true; }
                catch (UnsupportedOperationException ignored) {}
            }
        }
        if (anyChange) Invalidator.invalidate();
        return true;
    }
}
