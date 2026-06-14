package sibarum.dasum.gui.files;

import sibarum.dasum.gui.core.data.DataTableSource;
import sibarum.dasum.gui.core.em.Em;

import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Read-only {@link DataTableSource} adapter exposing a {@link DirectoryModel}
 * as a four-column grid: Name / Size / Type / Modified. Cells are formatted
 * on demand from the model's live {@link DirectoryModel#entries()} list, so a
 * refresh / navigation is reflected the next frame without rebuilding the
 * source.
 *
 * <p>Every mutation method is left at its throwing default and
 * {@link #canEdit} returns {@code false}: a directory listing is not an
 * editable grid. (Rename / delete will arrive as explicit commands, not
 * inline cell edits.)
 */
public final class DirectoryTableSource implements DataTableSource {

    private static final int COL_NAME = 0;
    private static final int COL_SIZE = 1;
    private static final int COL_TYPE = 2;
    private static final int COL_MODIFIED = 3;

    private static final DateTimeFormatter MODIFIED_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final DirectoryModel model;

    public DirectoryTableSource(DirectoryModel model) {
        this.model = model;
    }

    @Override public int columnCount() { return 4; }

    @Override public String columnHeader(int col) {
        return switch (col) {
            case COL_NAME -> "Name";
            case COL_SIZE -> "Size";
            case COL_TYPE -> "Type";
            case COL_MODIFIED -> "Modified";
            default -> "";
        };
    }

    @Override public Em columnWidth(int col) {
        return switch (col) {
            case COL_NAME -> Em.of(24f);
            case COL_SIZE -> Em.of(8f);
            case COL_TYPE -> Em.of(7f);
            case COL_MODIFIED -> Em.of(12f);
            default -> Em.of(6f);
        };
    }

    @Override public OptionalLong rowCount() {
        return OptionalLong.of(model.entries().size());
    }

    @Override public String get(int row, int col) {
        Entry e = model.entryAt(row);
        if (e == null) return null;
        return switch (col) {
            case COL_NAME -> e.name();
            case COL_SIZE -> e.dir() ? "" : humanSize(e.size());
            case COL_TYPE -> e.type();
            case COL_MODIFIED -> formatTime(e.modified());
            default -> "";
        };
    }

    @Override public boolean canEdit(int row, int col) { return false; }

    // ---------- formatting ----------

    /** Binary-ish human-readable size: B / KB / MB / GB / TB. */
    static String humanSize(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double v = bytes;
        int i = -1;
        do { v /= 1024.0; i++; } while (v >= 1024.0 && i < units.length - 1);
        return (v >= 100 ? String.format(Locale.ROOT, "%.0f %s", v, units[i])
                         : String.format(Locale.ROOT, "%.1f %s", v, units[i]));
    }

    private static String formatTime(FileTime t) {
        return t == null ? "" : MODIFIED_FMT.format(t.toInstant());
    }
}
