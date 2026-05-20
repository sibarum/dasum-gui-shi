package sibarum.dasum.gui.core.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TableSelectionTest {

    @Test
    void cellSelectionIsSingleCell() {
        TableSelection sel = TableSelection.cell(3, 5);
        assertEquals(3, sel.rowStart());
        assertEquals(3, sel.rowEnd());
        assertEquals(5, sel.colStart());
        assertEquals(5, sel.colEnd());
        assertEquals(TableSelection.Mode.CELLS, sel.mode());
        assertEquals(1, sel.rowCount());
        assertEquals(1, sel.columnCount());
    }

    @Test
    void blockNormalizesEndpoints() {
        // Anchor at (7, 9), cursor at (3, 5) — should yield (3..7) × (5..9).
        TableSelection sel = TableSelection.block(7, 9, 3, 5);
        assertEquals(3, sel.rowStart());
        assertEquals(7, sel.rowEnd());
        assertEquals(5, sel.colStart());
        assertEquals(9, sel.colEnd());
        assertEquals(5, sel.rowCount());
        assertEquals(5, sel.columnCount());
    }

    @Test
    void containsCheckUsesMode() {
        TableSelection rows = TableSelection.rows(2, 4);
        assertTrue(rows.contains(3, 100), "row mode ignores column");
        assertFalse(rows.contains(5, 0), "outside row range");

        TableSelection cols = TableSelection.columns(1, 3);
        assertTrue(cols.contains(99, 2), "col mode ignores row");
        assertFalse(cols.contains(0, 4), "outside col range");

        TableSelection all = TableSelection.all();
        assertTrue(all.contains(0, 0));
        assertTrue(all.contains(Integer.MAX_VALUE, Integer.MAX_VALUE));

        TableSelection block = TableSelection.block(2, 2, 5, 5);
        assertTrue(block.contains(3, 3));
        assertFalse(block.contains(1, 3));
        assertFalse(block.contains(3, 6));
    }

    @Test
    void withCursorExtendsFromAnchor() {
        TableSelection initial = TableSelection.cell(2, 3); // anchor (2,3)
        TableSelection extended = initial.withCursor(5, 7);
        assertEquals(2, extended.rowStart());
        assertEquals(5, extended.rowEnd());
        assertEquals(3, extended.colStart());
        assertEquals(7, extended.colEnd());
    }

    @Test
    void withCursorPromotesAllToCellsBlock() {
        // Carrying ALL through a cursor-extend is meaningless — drop to CELLS
        // so subsequent contains() behaves intuitively.
        TableSelection all = TableSelection.all();
        TableSelection ext = all.withCursor(3, 4);
        assertEquals(TableSelection.Mode.CELLS, ext.mode(),
            "ALL → CELLS when extended");
    }
}
