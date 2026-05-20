package sibarum.dasum.gui.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * TSV serialization for cell-range copy/paste. Matches Excel/Google
 * Sheets conventions:
 * <ul>
 *   <li>Tab between columns; LF between rows.</li>
 *   <li>Cells containing a tab, newline, or double-quote are wrapped in
 *       {@code "..."} and any embedded {@code "} doubled.</li>
 *   <li>Parser accepts CRLF and LF; emits LF on serialize.</li>
 * </ul>
 *
 * <p>Pure parser — no GL, no sidecar state. Tests cover quoted cells with
 * embedded newlines + tabs, and round-trip stability.
 */
public final class DataTableClipboard {

    private DataTableClipboard() {}

    /** Serialize a 2D string array to TSV. {@code null} cells become empty strings. */
    public static String toTsv(String[][] rows) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.length; r++) {
            if (r > 0) sb.append('\n');
            String[] row = rows[r];
            for (int c = 0; c < row.length; c++) {
                if (c > 0) sb.append('\t');
                sb.append(encodeCell(row[c]));
            }
        }
        return sb.toString();
    }

    /** Serialize a region of a source defined by {@code sel}. Clamps to the source's bounds. */
    public static String toTsv(DataTableSource src, TableSelection sel) {
        int rStart = Math.max(0, sel.rowStart());
        int rEnd   = sel.rowEnd();
        int cStart = Math.max(0, sel.colStart());
        int cEnd   = Math.min(src.columnCount() - 1, sel.colEnd());
        // Clamp row end if rowCount is known and finite.
        if (src.rowCount().isPresent()) {
            long max = src.rowCount().getAsLong() - 1;
            if (max < rEnd) rEnd = (int) Math.max(rStart - 1, max);
        }
        if (cEnd < cStart || rEnd < rStart) return "";
        int rows = rEnd - rStart + 1;
        int cols = cEnd - cStart + 1;
        String[][] grid = new String[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                String v = src.get(rStart + r, cStart + c);
                grid[r][c] = (v == null) ? "" : v;
            }
        }
        return toTsv(grid);
    }

    /**
     * Parse a TSV string into a rectangular 2D string array. Ragged rows
     * are padded with empty strings to match the widest row. Always
     * returns at least one row × one column.
     */
    public static String[][] fromTsv(String tsv) {
        if (tsv == null) return new String[][] { { "" } };
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int len = tsv.length();
        while (i < len) {
            char ch = tsv.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && tsv.charAt(i + 1) == '"') {
                        // Escaped quote inside quoted cell.
                        cell.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else {
                    cell.append(ch);
                    i++;
                }
            } else {
                if (ch == '\t') {
                    current.add(cell.toString());
                    cell.setLength(0);
                    i++;
                } else if (ch == '\n' || ch == '\r') {
                    // CRLF: consume both
                    current.add(cell.toString());
                    cell.setLength(0);
                    rows.add(current);
                    current = new ArrayList<>();
                    if (ch == '\r' && i + 1 < len && tsv.charAt(i + 1) == '\n') i += 2;
                    else i++;
                } else if (ch == '"' && cell.length() == 0) {
                    inQuotes = true;
                    i++;
                } else {
                    cell.append(ch);
                    i++;
                }
            }
        }
        // Trailing cell + row (only emit final row if it has content OR we already started cells).
        current.add(cell.toString());
        if (current.size() > 1 || !current.get(0).isEmpty() || rows.isEmpty()) {
            rows.add(current);
        }
        int maxCols = 0;
        for (List<String> r : rows) if (r.size() > maxCols) maxCols = r.size();
        if (maxCols == 0) maxCols = 1;
        String[][] grid = new String[rows.size()][maxCols];
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < maxCols; c++) {
                grid[r][c] = c < row.size() ? row.get(c) : "";
            }
        }
        return grid;
    }

    private static String encodeCell(String v) {
        if (v == null) return "";
        boolean needsQuote = false;
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\t' || ch == '\n' || ch == '\r' || ch == '"') { needsQuote = true; break; }
        }
        if (!needsQuote) return v;
        StringBuilder sb = new StringBuilder(v.length() + 2);
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '"') sb.append('"').append('"');
            else sb.append(ch);
        }
        sb.append('"');
        return sb.toString();
    }
}
