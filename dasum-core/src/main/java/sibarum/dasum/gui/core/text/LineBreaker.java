package sibarum.dasum.gui.core.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text content into visual lines for rendering and caret math.
 * <p>
 * When {@code wrapWidthPx} is {@code null}, splits on {@code '\n'} only —
 * the same behavior as before wrap support landed. When set, also wraps
 * to fit. Break-opportunity priority:
 * <ol>
 *   <li>Whitespace (primary; preferred break)</li>
 *   <li>Letter ↔ punctuation transition</li>
 *   <li>Letter ↔ digit transition</li>
 * </ol>
 * Long-word rule: a chunk wider than {@code wrapWidthPx} stays on the
 * current line rather than triggering a break — wrapping the unbreakable
 * word onto its own line would just overflow there anyway, so don't waste
 * the line break. The chunk simply overflows visually.
 * <p>
 * Trailing whitespace is included in a line's range and width — render is
 * unaffected (whitespace is non-printing) and caret-math at the wrap
 * boundary stays unambiguous.
 */
public final class LineBreaker {

    public record LineSpan(int start, int end, float widthPx) {}

    private LineBreaker() {}

    public static List<LineSpan> breakLines(String content, FontGroup fg, float fontPx, Float wrapWidthPx) {
        List<LineSpan> out = new ArrayList<>();
        int n = content.length();
        if (n == 0) {
            out.add(new LineSpan(0, 0, 0f));
            return out;
        }

        int i = 0;
        int lineStart = 0;
        float lineWidth = 0f;

        while (i < n) {
            char c = content.charAt(i);
            if (c == '\n') {
                out.add(new LineSpan(lineStart, i, lineWidth));
                i++;
                lineStart = i;
                lineWidth = 0f;
                continue;
            }

            // Find end of next chunk — extend until we hit '\n' or a break opportunity.
            int chunkEnd = i + 1;
            while (chunkEnd < n && content.charAt(chunkEnd) != '\n' && !canBreakBetween(content, chunkEnd)) {
                chunkEnd++;
            }
            float chunkWidth = measureWidth(content, i, chunkEnd, fg, fontPx);

            boolean lineEmpty   = (lineWidth == 0f);
            boolean fits        = (wrapWidthPx == null) || (lineWidth + chunkWidth <= wrapWidthPx);
            boolean chunkTooBig = (wrapWidthPx != null) && (chunkWidth > wrapWidthPx);

            if (fits || lineEmpty || chunkTooBig) {
                lineWidth += chunkWidth;
                i = chunkEnd;
            } else {
                // Break before this chunk. Skip any leading whitespace on the new
                // line so wrap doesn't visually indent the next paragraph.
                out.add(new LineSpan(lineStart, i, lineWidth));
                while (i < n && isLineWhitespace(content.charAt(i))) i++;
                lineStart = i;
                lineWidth = 0f;
                // Don't advance i past the chunk — loop will re-process it on the new line.
            }
        }
        out.add(new LineSpan(lineStart, n, lineWidth));
        return out;
    }

    /**
     * True if a visual line break is permissible between {@code s[i-1]}
     * and {@code s[i]}. Whitespace boundaries are primary; transitions
     * between letter/punct and letter/digit categories are secondary
     * fallbacks for long unbreakable sequences.
     */
    public static boolean canBreakBetween(String s, int i) {
        if (i <= 0 || i >= s.length()) return false;
        char prev = s.charAt(i - 1);
        char cur  = s.charAt(i);
        if (Character.isWhitespace(prev) || Character.isWhitespace(cur)) return true;
        boolean prevLetter = Character.isLetter(prev);
        boolean curLetter  = Character.isLetter(cur);
        boolean prevDigit  = Character.isDigit(prev);
        boolean curDigit   = Character.isDigit(cur);
        boolean prevPunct  = !prevLetter && !prevDigit;
        boolean curPunct   = !curLetter  && !curDigit;
        if ((prevLetter && curPunct) || (prevPunct && curLetter)) return true;
        if ((prevLetter && curDigit) || (prevDigit && curLetter)) return true;
        return false;
    }

    private static boolean isLineWhitespace(char c) {
        // Not '\n' — that's a hard break handled separately.
        return c == ' ' || c == '\t';
    }

    private static float measureWidth(String s, int from, int to, FontGroup fg, float fontPx) {
        float w = 0f;
        for (int j = from; j < to; ) {
            int cp = s.codePointAt(j);
            w += fg.layout().advance(cp, fontPx);
            j += Character.charCount(cp);
        }
        return w;
    }
}
