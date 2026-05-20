package sibarum.dasum.gui.core.data;

/**
 * Immutable rectangular selection in a {@code Component.DataTable}.
 * Selections are normalized lazily — {@link #rowStart} / {@link #rowEnd}
 * etc. return the min/max of the two endpoints so callers don't have to
 * track which corner is the anchor.
 *
 * <p>{@link Mode} encodes the kind of selection without changing the
 * record shape: {@link Mode#CELLS} is a free block, {@link Mode#ROWS}
 * spans every column for the selected row range, {@link Mode#COLUMNS}
 * spans every row for the column range, {@link Mode#ALL} is "the entire
 * sheet" (top-left header click). Renderer + context menu read the mode
 * to decide which highlight to draw and which menu items to show.
 *
 * <p>Endpoint indices are stored as the user clicked them so future
 * shift-extend can grow the selection from the anchor.
 *
 * <p>The "ALL" / row / column selections still carry concrete row0/row1
 * and col0/col1 (clamped to the table's current bounds when constructed)
 * so the renderer can paint without consulting the source — but consumers
 * checking {@link #contains} should still respect the {@link Mode}.
 */
public record TableSelection(int row0, int col0, int row1, int col1, Mode mode) {

    public enum Mode {
        /** Free rectangular block of cells. */
        CELLS,
        /** Entire rows {@code [rowStart(), rowEnd()]} — column range is ignored for highlight purposes. */
        ROWS,
        /** Entire columns {@code [colStart(), colEnd()]} — row range is ignored for highlight purposes. */
        COLUMNS,
        /** Entire sheet (sentinel — typically issued by a top-left corner click). */
        ALL
    }

    /** A single-cell selection. */
    public static TableSelection cell(int row, int col) {
        return new TableSelection(row, col, row, col, Mode.CELLS);
    }

    /** A rectangular block. {@code r0,c0} is the anchor; {@code r1,c1} the cursor side. */
    public static TableSelection block(int r0, int c0, int r1, int c1) {
        return new TableSelection(r0, c0, r1, c1, Mode.CELLS);
    }

    /** Row range. The cell endpoints encode the row span; column endpoints store the click columns for shift-extend continuity but are ignored by {@link #contains}. */
    public static TableSelection rows(int r0, int r1) {
        return new TableSelection(r0, 0, r1, Integer.MAX_VALUE, Mode.ROWS);
    }

    /** Column range. Symmetric to {@link #rows}. */
    public static TableSelection columns(int c0, int c1) {
        return new TableSelection(0, c0, Integer.MAX_VALUE, c1, Mode.COLUMNS);
    }

    /** Entire sheet. */
    public static TableSelection all() {
        return new TableSelection(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, Mode.ALL);
    }

    public int rowStart() { return Math.min(row0, row1); }
    public int rowEnd()   { return Math.max(row0, row1); }
    public int colStart() { return Math.min(col0, col1); }
    public int colEnd()   { return Math.max(col0, col1); }

    /**
     * Whether {@code (row, col)} falls inside this selection's effective
     * region. CELLS uses the rectangular bounds; ROWS / COLUMNS only check
     * the relevant axis; ALL always matches.
     */
    public boolean contains(int row, int col) {
        return switch (mode) {
            case CELLS   -> row >= rowStart() && row <= rowEnd()
                         && col >= colStart() && col <= colEnd();
            case ROWS    -> row >= rowStart() && row <= rowEnd();
            case COLUMNS -> col >= colStart() && col <= colEnd();
            case ALL     -> true;
        };
    }

    /** Number of distinct rows in the selection's row span (capped to {@code int}). */
    public int rowCount()    { return Math.max(0, rowEnd() - rowStart() + 1); }
    /** Number of distinct columns in the selection's column span (capped to {@code int}). */
    public int columnCount() { return Math.max(0, colEnd() - colStart() + 1); }

    /** Extend the (row, col) end of the selection while keeping the anchor. */
    public TableSelection withCursor(int r, int c) {
        return new TableSelection(row0, col0, r, c, mode == Mode.ALL ? Mode.CELLS : mode);
    }
}
