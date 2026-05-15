package sibarum.dasum.gui.core.text;

/**
 * Word-boundary helpers for caret navigation and selection. Stateless;
 * every method takes the effective content string explicitly.
 * <p>
 * Character classification is binary:
 * <ul>
 *   <li>WORD — letters, digits, underscore</li>
 *   <li>NON_WORD — everything else (whitespace, newlines, punctuation)</li>
 * </ul>
 * Newlines are treated as ordinary non-word characters; word jumps cross
 * line breaks. Triple-click line selection uses {@link TextGeometry}'s
 * line-bound helpers and stops at newlines.
 */
public final class WordBoundary {

    private WordBoundary() {}

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Index of the next caret stop to the right of {@code from}.
     * Skips the current run (if any), then any non-word run, landing at
     * the start of the next word or end-of-string.
     */
    public static int nextWordBoundary(String s, int from) {
        int i = Math.max(0, Math.min(from, s.length()));
        int n = s.length();
        if (i >= n) return n;
        if (isWord(s.charAt(i))) {
            while (i < n && isWord(s.charAt(i))) i++;
        } else {
            while (i < n && !isWord(s.charAt(i))) i++;
            return i;
        }
        while (i < n && !isWord(s.charAt(i))) i++;
        return i;
    }

    /**
     * Index of the previous caret stop to the left of {@code from}.
     * Mirror of {@link #nextWordBoundary}.
     */
    public static int prevWordBoundary(String s, int from) {
        int i = Math.max(0, Math.min(from, s.length()));
        if (i <= 0) return 0;
        if (i > 0 && !isWord(s.charAt(i - 1))) {
            while (i > 0 && !isWord(s.charAt(i - 1))) i--;
            if (i == 0) return 0;
        }
        while (i > 0 && isWord(s.charAt(i - 1))) i--;
        return i;
    }

    /**
     * Returns {@code [start, end]} of the word containing {@code idx}. If
     * {@code idx} sits in a non-word run, returns the non-word run's
     * bounds instead — that's the standard double-click behavior for
     * selecting runs of punctuation/whitespace as a unit. Bounds are
     * clamped to {@code [0, s.length()]}.
     */
    public static int[] wordBoundsAt(String s, int idx) {
        int n = s.length();
        if (n == 0) return new int[] { 0, 0 };
        int i = Math.max(0, Math.min(idx, n));
        // If caret is at end-of-text, anchor on the previous char.
        int probe = (i < n) ? i : i - 1;
        boolean word = isWord(s.charAt(probe));

        int start = probe;
        while (start > 0 && isWord(s.charAt(start - 1)) == word) start--;
        int end = probe;
        while (end < n && isWord(s.charAt(end)) == word) end++;
        return new int[] { start, end };
    }
}
