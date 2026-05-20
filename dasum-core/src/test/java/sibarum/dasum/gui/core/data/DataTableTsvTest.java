package sibarum.dasum.gui.core.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class DataTableTsvTest {

    @Test
    void plainRoundtrip() {
        String[][] grid = { {"a", "b", "c"}, {"1", "2", "3"} };
        String tsv = DataTableClipboard.toTsv(grid);
        assertEquals("a\tb\tc\n1\t2\t3", tsv);
        String[][] back = DataTableClipboard.fromTsv(tsv);
        assertArrayEquals(grid[0], back[0]);
        assertArrayEquals(grid[1], back[1]);
    }

    @Test
    void cellWithTabIsQuoted() {
        String[][] grid = { {"a\tb", "c"} };
        String tsv = DataTableClipboard.toTsv(grid);
        assertEquals("\"a\tb\"\tc", tsv);
        String[][] back = DataTableClipboard.fromTsv(tsv);
        assertEquals("a\tb", back[0][0]);
        assertEquals("c",    back[0][1]);
    }

    @Test
    void cellWithNewlineIsQuoted() {
        String[][] grid = { {"line1\nline2"} };
        String tsv = DataTableClipboard.toTsv(grid);
        assertEquals("\"line1\nline2\"", tsv);
        String[][] back = DataTableClipboard.fromTsv(tsv);
        assertEquals("line1\nline2", back[0][0]);
    }

    @Test
    void cellWithQuoteIsDoubled() {
        String[][] grid = { {"he said \"hi\""} };
        String tsv = DataTableClipboard.toTsv(grid);
        assertEquals("\"he said \"\"hi\"\"\"", tsv);
        String[][] back = DataTableClipboard.fromTsv(tsv);
        assertEquals("he said \"hi\"", back[0][0]);
    }

    @Test
    void crlfTreatedAsRowSeparator() {
        String[][] grid = DataTableClipboard.fromTsv("a\tb\r\nc\td");
        assertEquals(2, grid.length);
        assertEquals("a", grid[0][0]);
        assertEquals("b", grid[0][1]);
        assertEquals("c", grid[1][0]);
        assertEquals("d", grid[1][1]);
    }

    @Test
    void raggedRowsArePadded() {
        // Second row has only one cell — first row's width wins; result is rectangular.
        String[][] grid = DataTableClipboard.fromTsv("a\tb\tc\nx");
        assertEquals(2, grid.length);
        assertEquals(3, grid[0].length);
        assertEquals(3, grid[1].length);
        assertEquals("x", grid[1][0]);
        assertEquals("",  grid[1][1]);
        assertEquals("",  grid[1][2]);
    }

    @Test
    void emptyInputProducesSingleEmptyCell() {
        String[][] grid = DataTableClipboard.fromTsv("");
        assertEquals(1, grid.length);
        assertEquals(1, grid[0].length);
        assertEquals("", grid[0][0]);
    }

    @Test
    void nullInputProducesSingleEmptyCell() {
        String[][] grid = DataTableClipboard.fromTsv(null);
        assertEquals(1, grid.length);
        assertEquals(1, grid[0].length);
        assertEquals("", grid[0][0]);
    }
}
