package sibarum.dasum.gui.core.text;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs — once per (atlas, codepoint) — when text is laid out or rendered with a
 * printable character that isn't baked into the font atlas. The character
 * still shows as the atlas's missing-glyph box (or nothing, on older atlases),
 * which is easy to miss; this surfaces it as an error with a stack trace so the
 * offending string / call site can be tracked down.
 * <p>
 * Deduped because the retained-mode renderer re-draws the same text every dirty
 * frame — without dedup a single bad character would flood stderr. The hit path
 * (glyph present) never reaches here, so there's no cost for normal text.
 */
final class MissingGlyphLog {

    private static final Set<Long> SEEN = ConcurrentHashMap.newKeySet();

    private MissingGlyphLog() {}

    /**
     * Report a printable codepoint that is absent from {@code atlas}. Returns
     * {@code true} if this was the first sighting for that atlas+codepoint (and
     * it therefore logged), {@code false} if it was already reported.
     */
    static boolean report(AtlasData atlas, int codepoint) {
        long key = ((long) System.identityHashCode(atlas) << 32) | (codepoint & 0xFFFFFFFFL);
        if (!SEEN.add(key)) return false;

        String ch;
        try { ch = new String(Character.toChars(codepoint)); }
        catch (IllegalArgumentException e) { ch = "?"; }
        String name;
        try { name = Character.getName(codepoint); } catch (RuntimeException e) { name = null; }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "[dasum] ERROR missing glyph: U+%04X %s'%s' is not in the font atlas; "
                + "rendering the missing-glyph box. Add the character to the atlas charset, "
                + "or render this text with a font group that contains it.",
            codepoint, name != null ? "(" + name + ") " : "", ch));
        // Append the stack (skipping this method + GlyphLayout.resolve) so the
        // line pointing at the render/measure call site is easy to spot.
        StackTraceElement[] st = new Throwable().getStackTrace();
        for (int i = 1; i < st.length; i++) sb.append("\n\tat ").append(st[i]);

        System.err.println(sb);
        return true;
    }

    /** Test hook — forget every reported glyph so a test starts clean. */
    static void resetForTest() { SEEN.clear(); }
}
