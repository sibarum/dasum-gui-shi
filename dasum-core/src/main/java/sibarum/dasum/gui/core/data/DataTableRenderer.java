package sibarum.dasum.gui.core.data;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.CustomRenderers;
import sibarum.dasum.gui.core.render.DrawCommand;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;

import java.util.OptionalLong;

/**
 * Custom renderer for {@link Component.DataTable}. Virtualizes the visible
 * row + column window so a 1M-row table stays cheap.
 *
 * <p>Render passes, in order (each preceded by a flush + scissor push so
 * already-buffered geometry isn't drawn under the new clip):
 * <ol>
 *   <li>Body cells — banded backgrounds, selection fill, grid lines,
 *       cell glyphs. Translates with both scrollX + scrollY.</li>
 *   <li>Column header strip — sticky-top, translates horizontally only.</li>
 *   <li>Row-number gutter — sticky-left, translates vertically only.</li>
 *   <li>Top-left corner cell — sticky, no scroll.</li>
 * </ol>
 *
 * <p>Per-cell glyph emission stops once {@code cx} would exceed the cell's
 * right edge minus a small epsilon, so long values truncate cleanly
 * without bleeding into the next cell. No ellipsis (clip is the cheapest
 * option that conveys "more here than fits").
 *
 * <p>Async loading: cells whose {@link DataTableSource#get} returns null
 * render a thin grey horizontal bar centred in the cell — stateless,
 * one quad. The renderer doesn't animate; if the app wants a spinner it
 * publishes new snapshots and the next frame picks them up via
 * {@link sibarum.dasum.gui.core.event.Invalidator}.
 *
 * <p>Scissor management always pops the same number it pushes. The
 * preceding batcher.flush call exists to avoid leaking previously-buffered
 * geometry across the scissor change — required by ScissorStack's
 * contract.
 */
public final class DataTableRenderer implements CustomRenderers.Renderer {

    private static boolean REGISTERED = false;

    /** Register once at app startup. Idempotent. */
    public static synchronized void register() {
        if (REGISTERED) return;
        CustomRenderers.register(Component.DataTable.class, new DataTableRenderer());
        REGISTERED = true;
    }

    @Override
    public void render(Component c, PixelRect rect, Batcher batcher, float[] projection) {
        if (!(c instanceof Component.DataTable table)) return;
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) return;
        DataTableSource src = table.source();
        if (src == null) return;

        DataTableStates.DataTableState state = DataTableStates.of(table);

        // ---------- pixel layout ----------
        float headerH = table.headerHeight().toPixels();
        float rowH    = table.rowHeight().toPixels();
        float gutterW = table.rowNumberColumnWidth().toPixels();
        if (rowH <= 0f) return; // pathological — bail to avoid divide-by-zero

        float tableX  = rect.x();
        float tableY  = rect.y();
        float tableW  = rect.width();
        float tableH  = rect.height();

        float bodyX   = tableX + gutterW;
        float bodyY   = tableY + headerH;
        float bodyW   = Math.max(0f, tableW - gutterW);
        float bodyH   = Math.max(0f, tableH - headerH);

        // Snap scroll to integer pixels to avoid sub-pixel seams between
        // body and sticky header/gutter (those don't scroll on the same
        // axis, so non-integer scroll produces a half-pixel grid line
        // misalignment along the seam).
        float scrollX = Math.round(state.scrollPxX);
        float scrollY = Math.round(state.scrollPxY);

        // ---------- column geometry ----------
        int colCount = src.columnCount();
        // Cumulative pixel offset of each column's left edge in unscrolled
        // body space [0, 1, ..., colCount]. colXs[i] is the left edge of
        // column i; colXs[colCount] is one past the last column.
        float[] colXs = new float[colCount + 1];
        for (int i = 0; i < colCount; i++) {
            colXs[i + 1] = colXs[i] + src.columnWidth(i).toPixels();
        }
        // First/last visible column based on scrollX.
        int firstCol = 0;
        while (firstCol < colCount && colXs[firstCol + 1] < scrollX) firstCol++;
        int lastCol = firstCol;
        while (lastCol < colCount && colXs[lastCol] < scrollX + bodyW) lastCol++;
        // lastCol is exclusive.

        // ---------- row geometry ----------
        OptionalLong rcOpt = src.rowCount();
        long rowCountLong = rcOpt.orElse(Long.MAX_VALUE);
        long firstRow = (long) Math.floor(scrollY / rowH);
        if (firstRow < 0L) firstRow = 0L;
        long lastRow  = (long) Math.ceil((scrollY + bodyH) / rowH);
        if (lastRow > rowCountLong) lastRow = rowCountLong;
        // Stop emitting rows past the bottom even when rowCount is unknown
        // (streaming): we still cap visible rows at viewport extent below.

        // ---------- glyph metrics ----------
        FontGroup fg = FontGroups.getOrDefault(table.fontGroup());
        float fontPx   = table.fontSize().toPixels();
        float ascender = fg.atlas().metrics().ascender() * fontPx;

        // ===== Body pass =====
        batcher.flush(projection);
        batcher.scissor().push(new PixelRect(bodyX, bodyY, bodyW, bodyH));

        for (long row = firstRow; row < lastRow; row++) {
            float cy = bodyY + (row * rowH) - scrollY;
            // Banded row background — alternate. Use long row index so
            // banding stays stable past int range.
            Color rowBg = ((row & 1L) == 0L) ? table.cellBgEven() : table.cellBgOdd();
            batcher.submit(new DrawCommand.ColoredQuad(bodyX, cy, bodyW, rowH, rowBg));

            for (int col = firstCol; col < lastCol; col++) {
                float cx = bodyX + colXs[col] - scrollX;
                float cw = colXs[col + 1] - colXs[col];
                emitCellBody(table, state, src, batcher, fg, fontPx, ascender,
                    (int) Math.min(row, Integer.MAX_VALUE), col, cx, cy, cw, rowH);
            }
        }
        // Grid lines — horizontal between rows, vertical between cols.
        emitGridLines(batcher, table.gridLine(),
            bodyX, bodyY, bodyW, bodyH,
            scrollX, scrollY, rowH, colXs, firstRow, lastRow, firstCol, lastCol);

        // Set atlas before glyph submits. Flush ensures the body backgrounds
        // emitted above are flushed before the next pass (header), but we
        // also need to draw glyph quads accumulated for body cells before
        // changing scissor — they belong to the body clip.
        batcher.setTextAtlas(fg.texture(), fg.distanceRange(), projection);
        for (long row = firstRow; row < lastRow; row++) {
            float cy = bodyY + (row * rowH) - scrollY;
            for (int col = firstCol; col < lastCol; col++) {
                float cx = bodyX + colXs[col] - scrollX;
                float cw = colXs[col + 1] - colXs[col];
                int ir = (int) Math.min(row, Integer.MAX_VALUE);
                if (ir == state.editingRow && col == state.editingCol) {
                    // In-progress edit: draw the live buffer + caret, not the
                    // source value. Also paint a thin focus-ring quad so the
                    // user can see they're editing this cell.
                    String edit = state.editBuffer.toString();
                    emitCellGlyphs(batcher, fg, fontPx, ascender, edit,
                        cx, cy, cw, rowH, table.textColor());
                    // Caret — 2px-wide vertical bar at the caret's x.
                    float padL = 4f;
                    float caretX = cx + padL;
                    int caretIdx = Math.min(Math.max(0, state.editCaret), edit.length());
                    for (int j = 0; j < caretIdx; ) {
                        int cp = edit.codePointAt(j);
                        caretX += fg.layout().advance(cp, fontPx);
                        j += Character.charCount(cp);
                    }
                    batcher.submit(new DrawCommand.ColoredQuad(
                        caretX, cy + (rowH - fontPx) * 0.5f, 1.5f, fontPx,
                        new Color(table.textColor().r(), table.textColor().g(), table.textColor().b(), 0.95f)
                    ));
                    continue;
                }
                String value = src.get(ir, col);
                if (value == null) {
                    // null = "loading" — draw a thin centred bar instead of glyphs.
                    float barH = 2f;
                    float barW = cw * 0.6f;
                    batcher.submit(new DrawCommand.ColoredQuad(
                        cx + (cw - barW) * 0.5f,
                        cy + (rowH - barH) * 0.5f,
                        barW, barH,
                        new Color(table.textColor().r(), table.textColor().g(), table.textColor().b(), 0.25f)
                    ));
                    continue;
                }
                emitCellGlyphs(batcher, fg, fontPx, ascender, value,
                    cx, cy, cw, rowH, table.textColor());
            }
        }
        batcher.flush(projection);
        batcher.scissor().pop();

        // ===== Header pass =====
        batcher.flush(projection);
        batcher.scissor().push(new PixelRect(bodyX, tableY, bodyW, headerH));
        // Header bg over the whole header strip (visible columns + slack).
        batcher.submit(new DrawCommand.ColoredQuad(bodyX, tableY, bodyW, headerH, table.headerBg()));
        for (int col = firstCol; col < lastCol; col++) {
            float cx = bodyX + colXs[col] - scrollX;
            float cw = colXs[col + 1] - colXs[col];
            // Column-selection highlight in the header.
            if (selectionContainsColumn(state.selection, col)) {
                batcher.submit(new DrawCommand.ColoredQuad(cx, tableY, cw, headerH, table.selectionFill()));
            }
            // Right-edge vertical divider — 1px line.
            batcher.submit(new DrawCommand.ColoredQuad(cx + cw - 1f, tableY, 1f, headerH, table.gridLine()));
        }
        // Bottom edge of the header strip — horizontal divider.
        batcher.submit(new DrawCommand.ColoredQuad(bodyX, tableY + headerH - 1f, bodyW, 1f, table.gridLine()));

        // Header glyphs.
        batcher.setTextAtlas(fg.texture(), fg.distanceRange(), projection);
        for (int col = firstCol; col < lastCol; col++) {
            float cx = bodyX + colXs[col] - scrollX;
            float cw = colXs[col + 1] - colXs[col];
            emitCellGlyphs(batcher, fg, fontPx, ascender, src.columnHeader(col),
                cx, tableY, cw, headerH, table.textColor());
        }
        batcher.flush(projection);
        batcher.scissor().pop();

        // ===== Gutter pass =====
        batcher.flush(projection);
        batcher.scissor().push(new PixelRect(tableX, bodyY, gutterW, bodyH));
        batcher.submit(new DrawCommand.ColoredQuad(tableX, bodyY, gutterW, bodyH, table.headerBg()));
        for (long row = firstRow; row < lastRow; row++) {
            float cy = bodyY + (row * rowH) - scrollY;
            if (selectionContainsRow(state.selection, (int) Math.min(row, Integer.MAX_VALUE))) {
                batcher.submit(new DrawCommand.ColoredQuad(tableX, cy, gutterW, rowH, table.selectionFill()));
            }
            // Horizontal divider.
            batcher.submit(new DrawCommand.ColoredQuad(tableX, cy + rowH - 1f, gutterW, 1f, table.gridLine()));
        }
        // Right edge of the gutter — vertical divider.
        batcher.submit(new DrawCommand.ColoredQuad(tableX + gutterW - 1f, bodyY, 1f, bodyH, table.gridLine()));

        batcher.setTextAtlas(fg.texture(), fg.distanceRange(), projection);
        for (long row = firstRow; row < lastRow; row++) {
            float cy = bodyY + (row * rowH) - scrollY;
            String label = Long.toString(row + 1);
            emitCellGlyphs(batcher, fg, fontPx, ascender, label,
                tableX, cy, gutterW, rowH, table.textColor());
        }
        batcher.flush(projection);
        batcher.scissor().pop();

        // ===== Corner cell =====
        batcher.submit(new DrawCommand.ColoredQuad(tableX, tableY, gutterW, headerH, table.headerBg()));
        batcher.submit(new DrawCommand.ColoredQuad(tableX + gutterW - 1f, tableY, 1f, headerH, table.gridLine()));
        batcher.submit(new DrawCommand.ColoredQuad(tableX, tableY + headerH - 1f, gutterW, 1f, table.gridLine()));
    }

    // ---------- helpers ----------

    private void emitCellBody(Component.DataTable table, DataTableStates.DataTableState state,
                              DataTableSource src, Batcher batcher,
                              FontGroup fg, float fontPx, float ascender,
                              int row, int col, float cx, float cy, float cw, float ch) {
        // Selection highlight over the cell background. Drawn here so a later
        // glyph pass renders on top.
        TableSelection sel = state.selection;
        if (sel != null && sel.contains(row, col)) {
            batcher.submit(new DrawCommand.ColoredQuad(cx, cy, cw, ch, table.selectionFill()));
        }
    }

    private void emitGridLines(Batcher batcher, Color grid,
                               float bodyX, float bodyY, float bodyW, float bodyH,
                               float scrollX, float scrollY, float rowH,
                               float[] colXs, long firstRow, long lastRow,
                               int firstCol, int lastCol) {
        // Horizontal lines between body rows.
        for (long row = firstRow; row < lastRow; row++) {
            float lineY = bodyY + ((row + 1) * rowH) - scrollY - 1f;
            if (lineY < bodyY) continue;
            if (lineY > bodyY + bodyH) break;
            batcher.submit(new DrawCommand.ColoredQuad(bodyX, lineY, bodyW, 1f, grid));
        }
        // Vertical lines between body cols.
        for (int col = firstCol; col < lastCol; col++) {
            float lineX = bodyX + colXs[col + 1] - scrollX - 1f;
            if (lineX < bodyX) continue;
            if (lineX > bodyX + bodyW) break;
            batcher.submit(new DrawCommand.ColoredQuad(lineX, bodyY, 1f, bodyH, grid));
        }
    }

    /**
     * Emit glyphs for a string inside a cell rect. Padded left+top by a
     * small margin, vertically centred on the cell's baseline. Stops
     * emitting once the next glyph's right edge would exceed the cell —
     * cleanly truncates long strings without bleeding into siblings.
     */
    private void emitCellGlyphs(Batcher batcher, FontGroup fg, float fontPx, float ascender,
                                String text, float cx, float cy, float cw, float ch, Color color) {
        if (text == null || text.isEmpty()) return;
        float padL = 4f; // small left/right padding inside the cell
        float padR = 4f;
        float startX = cx + padL;
        float baseY  = cy + (ch - fontPx) * 0.5f + ascender;
        float clipX  = cx + cw - padR;
        float curX = startX;
        for (int j = 0; j < text.length(); ) {
            int cp = text.codePointAt(j);
            float adv = fg.layout().advance(cp, fontPx);
            if (curX + adv > clipX) break;
            DrawCommand.GlyphQuad q = fg.layout().build(cp, curX, baseY, fontPx, color);
            if (q != null) batcher.submit(q);
            curX += adv;
            j += Character.charCount(cp);
        }
    }

    private static boolean selectionContainsRow(TableSelection sel, int row) {
        if (sel == null) return false;
        return switch (sel.mode()) {
            case ROWS, ALL -> row >= sel.rowStart() && row <= sel.rowEnd();
            case CELLS     -> row >= sel.rowStart() && row <= sel.rowEnd();
            case COLUMNS   -> false;
        };
    }

    private static boolean selectionContainsColumn(TableSelection sel, int col) {
        if (sel == null) return false;
        return switch (sel.mode()) {
            case COLUMNS, ALL -> col >= sel.colStart() && col <= sel.colEnd();
            case CELLS        -> col >= sel.colStart() && col <= sel.colEnd();
            case ROWS         -> false;
        };
    }
}
