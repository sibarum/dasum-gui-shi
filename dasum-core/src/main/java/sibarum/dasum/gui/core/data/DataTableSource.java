package sibarum.dasum.gui.core.data;

import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.OptionalLong;

/**
 * Adapter contract for a {@code Component.DataTable}'s backing data. The
 * framework treats sources as opaque providers of rows + columns; apps are
 * free to back a source with anything (in-memory list, file-mapped buffer,
 * SQLite query, network fetch). The interface is shaped to support
 * unbounded / streaming data: {@link #rowCount} may return empty when the
 * total is unknown, and {@link #get} may return {@code null} for a row
 * that's still being fetched.
 *
 * <p><b>Change notification.</b> The source itself calls
 * {@link Invalidator#invalidate} when async data lands. The framework's
 * renderer re-queries {@link #get} for the visible window every frame, so
 * the next frame after the invalidate picks up new data automatically. No
 * per-cell cache layer in the framework — the source owns its own caching.
 *
 * <p><b>Mutation.</b> All mutation methods (set, insert, delete) default
 * to throwing {@link UnsupportedOperationException}. Read-only sources
 * just don't override them. The {@code canEdit} / {@code canInsert*} /
 * {@code canDelete*} predicates let the renderer + context menu gate UI
 * affordances accordingly.
 *
 * <p><b>Identity.</b> The framework keys per-table sidecar state by the
 * {@code Component.DataTable} instance, NOT by the source. Two DataTables
 * sharing one source see independent selection / scroll / edit state.
 */
public interface DataTableSource {

    /** Number of columns. Fixed for a given source instance unless mutated via insert/delete column. */
    int columnCount();

    /** Display label for the column header strip. May be empty but not null. */
    String columnHeader(int col);

    /** Em width of column {@code col}. The renderer reads this every frame; cache if expensive. */
    Em columnWidth(int col);

    /**
     * Total row count, when known. Empty means "unknown / streaming" — the
     * renderer will scroll without a fixed bottom and stop emitting rows
     * once {@link #get} returns null for an entire row block past the
     * visible range.
     */
    OptionalLong rowCount();

    /**
     * The cell value at ({@code row}, {@code col}), or {@code null} if the
     * data isn't ready yet (the renderer paints a "loading" placeholder
     * for nulls). Implementations should be cheap — this is called for
     * every visible cell on every frame.
     */
    String get(int row, int col);

    /** Attempt to commit an edit. Return {@code false} to reject (e.g. validation failure). */
    default boolean trySet(int row, int col, String value) {
        throw new UnsupportedOperationException("Source is read-only");
    }

    /** Insert a blank row at index {@code row}, shifting subsequent rows down by one. */
    default void insertRowAbove(int row) {
        throw new UnsupportedOperationException("Source does not support row insert");
    }

    /** Insert a blank row immediately after index {@code row}. */
    default void insertRowBelow(int row) {
        insertRowAbove(row + 1);
    }

    /** Delete the half-open range {@code [from, toExclusive)} of rows. */
    default void deleteRows(int from, int toExclusive) {
        throw new UnsupportedOperationException("Source does not support row delete");
    }

    /** Insert a blank column at index {@code col}, shifting subsequent columns right by one. */
    default void insertColumnLeft(int col) {
        throw new UnsupportedOperationException("Source does not support column insert");
    }

    /** Insert a blank column immediately after index {@code col}. */
    default void insertColumnRight(int col) {
        insertColumnLeft(col + 1);
    }

    /** Delete the half-open range {@code [from, toExclusive)} of columns. */
    default void deleteColumns(int from, int toExclusive) {
        throw new UnsupportedOperationException("Source does not support column delete");
    }

    /** Whether ({@code row}, {@code col}) can be edited interactively. Defaults to "yes if mutation supported." */
    default boolean canEdit(int row, int col) {
        return true;
    }

    /** Whether this source supports {@link #insertRowAbove} / {@link #insertRowBelow}. */
    default boolean canInsertRows() { return false; }

    /** Whether this source supports {@link #deleteRows}. */
    default boolean canDeleteRows() { return false; }

    /** Whether this source supports {@link #insertColumnLeft} / {@link #insertColumnRight}. */
    default boolean canInsertColumns() { return false; }

    /** Whether this source supports {@link #deleteColumns}. */
    default boolean canDeleteColumns() { return false; }
}
