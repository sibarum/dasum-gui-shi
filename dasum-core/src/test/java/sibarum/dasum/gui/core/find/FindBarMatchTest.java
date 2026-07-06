package sibarum.dasum.gui.core.find;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link FindBar#findMatches} — the pure search core. */
final class FindBarMatchTest {

    @Test
    void findsAllOccurrencesInOrder() {
        List<int[]> m = FindBar.findMatches("the cat sat on the mat", "at");
        assertEquals(3, m.size(), "'at' occurs in cat, sat, mat");
        assertArrayEquals(new int[]{5, 7}, m.get(0));   // c[at]
        assertArrayEquals(new int[]{9, 11}, m.get(1));  // s[at]
        assertArrayEquals(new int[]{20, 22}, m.get(2)); // m[at]
    }

    @Test
    void caseInsensitive() {
        List<int[]> m = FindBar.findMatches("Foo FOO foo fOo", "foo");
        assertEquals(4, m.size(), "matches regardless of case");
        assertArrayEquals(new int[]{0, 3}, m.get(0));
        assertArrayEquals(new int[]{12, 15}, m.get(3));
    }

    @Test
    void matchesAreNonOverlapping() {
        // "aa" in "aaaa": positions 0-2 and 2-4, not 0,1,2 overlapping.
        List<int[]> m = FindBar.findMatches("aaaa", "aa");
        assertEquals(2, m.size());
        assertArrayEquals(new int[]{0, 2}, m.get(0));
        assertArrayEquals(new int[]{2, 4}, m.get(1));
    }

    @Test
    void emptyOrBlankInputsYieldNothing() {
        assertTrue(FindBar.findMatches("hello", "").isEmpty(), "empty query");
        assertTrue(FindBar.findMatches("", "x").isEmpty(), "empty content");
        assertTrue(FindBar.findMatches(null, "x").isEmpty(), "null content");
        assertTrue(FindBar.findMatches("x", null).isEmpty(), "null query");
    }

    @Test
    void noMatchYieldsEmpty() {
        assertTrue(FindBar.findMatches("hello world", "zzz").isEmpty());
    }

    @Test
    void wholeStringMatch() {
        List<int[]> m = FindBar.findMatches("exact", "exact");
        assertEquals(1, m.size());
        assertArrayEquals(new int[]{0, 5}, m.get(0));
    }
}
