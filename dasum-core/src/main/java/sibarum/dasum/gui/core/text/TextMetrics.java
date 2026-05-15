package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.component.Component;

import java.util.List;

/**
 * Per-Text intrinsic measurement. With {@code wrapWidth} set, "lines"
 * are visual lines produced by {@link LineBreaker}; otherwise they're
 * source-line splits on {@code '\n'}.
 */
public final class TextMetrics {

    private TextMetrics() {}

    public static List<LineBreaker.LineSpan> lines(Component.Text text, String content) {
        FontGroup fg = FontGroups.getOrDefault(text.fontGroup());
        float fontPx = text.fontSize().toPixels();
        Float wrapPx = text.wrapWidth() != null ? text.wrapWidth().toPixels() : null;
        return LineBreaker.breakLines(content, fg, fontPx, wrapPx);
    }

    public static float contentWidthPixels(Component.Text text, String content) {
        float maxLineWidth = 0f;
        for (LineBreaker.LineSpan line : lines(text, content)) {
            if (line.widthPx() > maxLineWidth) maxLineWidth = line.widthPx();
        }
        return maxLineWidth;
    }

    public static float contentHeightPixels(Component.Text text, String content) {
        FontGroup fg = FontGroups.getOrDefault(text.fontGroup());
        float fontPx = text.fontSize().toPixels();
        return lines(text, content).size() * fg.atlas().metrics().lineHeight() * fontPx;
    }

    public static int lineCount(Component.Text text, String content) {
        return lines(text, content).size();
    }

    public static float lineAdvance(FontGroup fg, String s, int from, int to, float fontPx) {
        float total = 0f;
        for (int j = from; j < to; ) {
            int cp = s.codePointAt(j);
            total += fg.layout().advance(cp, fontPx);
            j += Character.charCount(cp);
        }
        return total;
    }

    /** Pixel width including the Text's padding. Honours an explicit width if set. */
    public static float widthPixels(Component.Text text, String content) {
        float padPx = text.padding().toPixels();
        if (text.width() != null) return text.width().toPixels();
        return contentWidthPixels(text, content) + 2f * padPx;
    }

    /** Pixel height including the Text's padding. Honours an explicit height if set. */
    public static float heightPixels(Component.Text text, String content) {
        float padPx = text.padding().toPixels();
        if (text.height() != null) return text.height().toPixels();
        return contentHeightPixels(text, content) + 2f * padPx;
    }
}
