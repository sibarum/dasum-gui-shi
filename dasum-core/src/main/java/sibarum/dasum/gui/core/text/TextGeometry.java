package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.PixelRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless geometry helpers for editable text: map between screen pixels
 * and character indices, compute caret rectangles, build selection
 * highlight rectangles per visual line.
 * <p>
 * "Visual line" = a {@link LineBreaker.LineSpan} for the current
 * (content, wrapWidth) — equivalent to source-line splits when wrap is
 * off. All methods take the effective content string explicitly so the
 * {@code text} package doesn't need to depend on input-state lookups.
 */
public final class TextGeometry {

    private TextGeometry() {}

    // ---------- visual-line helpers ----------

    /**
     * Visual-line index that contains caret index {@code idx}. A caret at
     * a line's exclusive end (the soft-wrap gap before the next line)
     * stays on the current line.
     */
    public static int lineOfIndex(Component.Text text, String content, int idx) {
        List<LineBreaker.LineSpan> lines = TextMetrics.lines(text, content);
        for (int k = 0; k < lines.size(); k++) {
            LineBreaker.LineSpan line = lines.get(k);
            if (idx < line.end()) return k;
            if (idx == line.end()) return k;
        }
        return lines.size() - 1;
    }

    public static int lineStartOfIndex(Component.Text text, String content, int idx) {
        List<LineBreaker.LineSpan> lines = TextMetrics.lines(text, content);
        int k = lineOfIndex(text, content, idx);
        return lines.get(k).start();
    }

    public static int lineEndOfIndex(Component.Text text, String content, int idx) {
        List<LineBreaker.LineSpan> lines = TextMetrics.lines(text, content);
        int k = lineOfIndex(text, content, idx);
        return lines.get(k).end();
    }

    public static int startOfLine(Component.Text text, String content, int line) {
        List<LineBreaker.LineSpan> lines = TextMetrics.lines(text, content);
        if (line <= 0) return 0;
        if (line >= lines.size()) return content.length();
        return lines.get(line).start();
    }

    public static int endOfLine(Component.Text text, String content, int line) {
        List<LineBreaker.LineSpan> lines = TextMetrics.lines(text, content);
        if (line < 0) return 0;
        if (line >= lines.size()) return content.length();
        return lines.get(line).end();
    }

    // ---------- screen ↔ caret index ----------

    public static int charIndexAt(Component.Text text, String content, PixelRect rect, float sx, float sy) {
        FontGroup fg = FontGroups.getOrDefault(text.fontGroup());
        float fontPx = text.fontSize().toPixels();
        float pad    = text.padding().toPixels();
        float gutter = TextMetrics.gutterWidthPixels(text, content);
        float lineH  = fg.atlas().metrics().lineHeight() * fontPx;

        List<LineBreaker.LineSpan> lines = TextMetrics.lines(text, content);
        int totalLines = lines.size();
        int k = (int) Math.floor((sy - rect.y() - pad) / lineH);
        if (k < 0) k = 0;
        if (k >= totalLines) k = totalLines - 1;

        LineBreaker.LineSpan line = lines.get(k);
        return line.start() + columnAtX(fg, content, line.start(), line.end(), fontPx, rect.x() + pad + gutter, sx);
    }

    private static int columnAtX(FontGroup fg, String s, int from, int to, float fontPx, float startX, float sx) {
        if (sx <= startX) return 0;
        float cur = startX;
        int col = 0;
        int j = from;
        while (j < to) {
            int cp = s.codePointAt(j);
            float adv = fg.layout().advance(cp, fontPx);
            float mid = cur + adv * 0.5f;
            if (sx < mid) return col;
            cur += adv;
            col++;
            j += Character.charCount(cp);
        }
        return col;
    }

    // ---------- caret position ----------

    public static PixelRect caretBounds(Component.Text text, String content, PixelRect rect, int caretIndex) {
        FontGroup fg = FontGroups.getOrDefault(text.fontGroup());
        float fontPx = text.fontSize().toPixels();
        float pad    = text.padding().toPixels();
        float lineH  = fg.atlas().metrics().lineHeight() * fontPx;

        int caret = clampIndex(content, caretIndex);
        int k = lineOfIndex(text, content, caret);
        LineBreaker.LineSpan line = TextMetrics.lines(text, content).get(k);
        int upTo = Math.min(caret, line.end());

        float gutter = TextMetrics.gutterWidthPixels(text, content);
        float x = rect.x() + pad + gutter + TextMetrics.lineAdvance(fg, content, line.start(), upTo, fontPx);
        float y = rect.y() + pad + k * lineH;
        return new PixelRect(x - 1f, y, 2f, lineH);
    }

    public static float caretVisualX(Component.Text text, String content, PixelRect rect, int caretIndex) {
        return caretBounds(text, content, rect, caretIndex).x() + 1f;
    }

    // ---------- range rectangles (selection + style) ----------

    /**
     * Per-visual-line {@link PixelRect}s covering the half-open character
     * range {@code [startIdx, endIdx)}. Used both by the selection fill
     * (via {@link #selectionRects}) and by the background-style sidecar
     * to paint per-range fills behind glyphs.
     * <p>
     * Indices that fall outside any visible line, or where
     * {@code startIdx >= endIdx} after normalization, produce an empty
     * list. On a fully-empty middle line the resulting rect gets a small
     * positive width so the range is still visible — matches how
     * selection highlights an empty line inside a multi-line drag.
     */
    public static List<PixelRect> styleRects(Component.Text text, String content, PixelRect rect, int startIdx, int endIdx) {
        List<PixelRect> out = new ArrayList<>();
        if (startIdx == endIdx) return out;
        if (startIdx > endIdx) { int t = startIdx; startIdx = endIdx; endIdx = t; }
        // Clip to live content so stale ranges (worker published before
        // the user deleted characters) don't ghost past the end.
        int len = content.length();
        if (startIdx < 0) startIdx = 0;
        if (endIdx   > len) endIdx = len;
        if (startIdx >= endIdx) return out;

        FontGroup fg = FontGroups.getOrDefault(text.fontGroup());
        float fontPx = text.fontSize().toPixels();
        float pad    = text.padding().toPixels();
        float gutter = TextMetrics.gutterWidthPixels(text, content);
        float lineH  = fg.atlas().metrics().lineHeight() * fontPx;

        List<LineBreaker.LineSpan> lines = TextMetrics.lines(text, content);
        int startLine = lineOfIndex(text, content, startIdx);
        int endLine   = lineOfIndex(text, content, endIdx);

        for (int k = startLine; k <= endLine; k++) {
            LineBreaker.LineSpan line = lines.get(k);
            int segFrom = (k == startLine) ? startIdx : line.start();
            int segTo   = (k == endLine)   ? endIdx   : line.end();
            // Clamp to this line's range so off-line indices don't ghost outward.
            segFrom = Math.max(line.start(), Math.min(line.end(), segFrom));
            segTo   = Math.max(line.start(), Math.min(line.end(), segTo));

            float fromX = rect.x() + pad + gutter + TextMetrics.lineAdvance(fg, content, line.start(), segFrom, fontPx);
            float toX   = rect.x() + pad + gutter + TextMetrics.lineAdvance(fg, content, line.start(), segTo,   fontPx);
            if (toX - fromX < 1f && segFrom == segTo && k < endLine) toX = fromX + fontPx * 0.3f;
            float y = rect.y() + pad + k * lineH;
            out.add(new PixelRect(fromX, y, Math.max(0f, toX - fromX), lineH));
        }
        return out;
    }

    /** Per-visual-line selection-highlight rectangles. Delegates to {@link #styleRects}. */
    public static List<PixelRect> selectionRects(Component.Text text, String content, PixelRect rect, int startIdx, int endIdx) {
        return styleRects(text, content, rect, startIdx, endIdx);
    }

    public static int clampIndex(String s, int idx) {
        if (idx < 0) return 0;
        return Math.min(idx, s.length());
    }
}
