package sibarum.dasum.gui.core.data;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.ContextEvent;
import sibarum.dasum.gui.core.input.ContextMenuItem;
import sibarum.dasum.gui.core.input.ContextMenuProvider;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-click menu provider for {@link Component.DataTable}. Region-aware:
 * the click's pixel coordinates resolve to BODY / HEADER / GUTTER /
 * CORNER and the returned items change accordingly.
 *
 * <ul>
 *   <li>Body — Copy / Cut / Paste / Clear, plus row/col insert+delete when
 *       the source supports it.</li>
 *   <li>Header — Insert Column Left/Right / Delete Column(s).</li>
 *   <li>Gutter — Insert Row Above/Below / Delete Row(s).</li>
 *   <li>Corner — Select All.</li>
 * </ul>
 *
 * Items that the source's {@code can*} flags reject are simply absent
 * from the menu (no greyed-out hint).
 */
public final class TableContextMenu {

    private TableContextMenu() {}

    public static ContextMenuProvider defaultProvider() {
        return TableContextMenu::buildItems;
    }

    /** Register {@link #defaultProvider()} on {@code table}. */
    public static void registerDefaults(Component.DataTable table) {
        Handlers.onContextMenu(table, defaultProvider());
    }

    // ---------- internals ----------

    private static List<ContextMenuItem> buildItems(ContextEvent event) {
        Component.DataTable table = tableFor(event);
        if (table == null) return List.of();
        DataTableSource src = table.source();
        if (src == null) return List.of();
        DataTableStates.DataTableState s = DataTableStates.of(table);

        Region region = resolveRegion(table, event);
        int row = currentRow(s, region, table, event);
        int col = currentCol(s, region, table, event);

        List<ContextMenuItem> items = new ArrayList<>();
        switch (region) {
            case BODY -> {
                items.add(new ContextMenuItem("Copy",  () -> copy(event)));
                items.add(new ContextMenuItem("Cut",   () -> cut(event)));
                items.add(new ContextMenuItem("Paste", () -> paste(event)));
                items.add(new ContextMenuItem("Clear", () -> clear(table)));
                items.add(ContextMenuItem.separator());
                addRowMutators(items, src, row);
                items.add(ContextMenuItem.separator());
                addColMutators(items, src, col);
            }
            case HEADER -> {
                addColMutators(items, src, col);
            }
            case GUTTER -> {
                addRowMutators(items, src, row);
            }
            case CORNER -> {
                items.add(new ContextMenuItem("Select All",
                    () -> DataTableStates.setSelection(table, TableSelection.all())));
            }
            default -> {}
        }
        return items;
    }

    private static void addRowMutators(List<ContextMenuItem> items, DataTableSource src, int row) {
        if (src.canInsertRows()) {
            items.add(new ContextMenuItem("Insert Row Above",
                () -> { src.insertRowAbove(row); Invalidator.invalidate(); }));
            items.add(new ContextMenuItem("Insert Row Below",
                () -> { src.insertRowBelow(row); Invalidator.invalidate(); }));
        }
        if (src.canDeleteRows()) {
            items.add(new ContextMenuItem("Delete Row",
                () -> { src.deleteRows(row, row + 1); Invalidator.invalidate(); }));
        }
    }

    private static void addColMutators(List<ContextMenuItem> items, DataTableSource src, int col) {
        if (src.canInsertColumns()) {
            items.add(new ContextMenuItem("Insert Column Left",
                () -> { src.insertColumnLeft(col); Invalidator.invalidate(); }));
            items.add(new ContextMenuItem("Insert Column Right",
                () -> { src.insertColumnRight(col); Invalidator.invalidate(); }));
        }
        if (src.canDeleteColumns()) {
            items.add(new ContextMenuItem("Delete Column",
                () -> { src.deleteColumns(col, col + 1); Invalidator.invalidate(); }));
        }
    }

    private static void copy(ContextEvent event) {
        sibarum.dasum.gui.core.input.DataTableSelectionController.onCopy(event.windowHandle());
    }
    private static void cut(ContextEvent event) {
        sibarum.dasum.gui.core.input.DataTableSelectionController.onCut(event.windowHandle());
    }
    private static void paste(ContextEvent event) {
        sibarum.dasum.gui.core.input.DataTableSelectionController.onPaste(event.windowHandle());
    }
    private static void clear(Component.DataTable table) {
        DataTableStates.DataTableState s = DataTableStates.of(table);
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
        boolean any = false;
        for (int r = rStart; r <= rEnd; r++) {
            for (int c = cStart; c <= cEnd; c++) {
                if (src.canEdit(r, c)) {
                    try { if (src.trySet(r, c, "")) any = true; }
                    catch (UnsupportedOperationException ignored) {}
                }
            }
        }
        if (any) Invalidator.invalidate();
    }

    private enum Region { BODY, HEADER, GUTTER, CORNER, NONE }

    private static Region resolveRegion(Component.DataTable table, ContextEvent event) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return Region.NONE;
        PixelRect rect = lr.rectOf(table);
        if (rect == null) return Region.NONE;
        float pxPerEm = EmContext.pixelsPerEm();
        if (pxPerEm <= 0f) return Region.NONE;
        // Convert event.cursorEm* to pixels, then to table-local.
        float pxX = event.cursorEmX() * pxPerEm;
        float pxY = event.cursorEmY() * pxPerEm;
        float localX = pxX - rect.x();
        float localY = pxY - rect.y();
        if (localX < 0 || localY < 0 || localX > rect.width() || localY > rect.height()) {
            return Region.NONE;
        }
        boolean inHeader = localY < table.headerHeight().toPixels();
        boolean inGutter = localX < table.rowNumberColumnWidth().toPixels();
        if (inHeader && inGutter) return Region.CORNER;
        if (inHeader) return Region.HEADER;
        if (inGutter) return Region.GUTTER;
        return Region.BODY;
    }

    private static int currentRow(DataTableStates.DataTableState s, Region region,
                                  Component.DataTable table, ContextEvent event) {
        // Prefer the live selection's rowEnd (matches the visual cursor).
        if (s.selection != null) {
            return Math.max(0, s.selection.rowEnd());
        }
        return 0;
    }

    private static int currentCol(DataTableStates.DataTableState s, Region region,
                                  Component.DataTable table, ContextEvent event) {
        if (s.selection != null) {
            int col = Math.max(0, s.selection.colEnd());
            return Math.min(col, table.source().columnCount() - 1);
        }
        return 0;
    }

    private static Component.DataTable tableFor(ContextEvent event) {
        for (Component c : event.hitPath()) {
            if (c instanceof Component.DataTable dt) return dt;
        }
        return null;
    }
}
