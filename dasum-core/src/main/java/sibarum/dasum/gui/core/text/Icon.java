package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

/**
 * Helpers for rendering icons from an icon-font {@link FontGroup}. An
 * "icon" is just a {@link Component.Text} sized to one glyph, drawn from
 * a font whose glyphs live in the Unicode Private Use Area
 * (U+E000–U+F8FF) rather than the ASCII range.
 *
 * <p>The icon-font atlas is registered as a regular {@link FontGroup} at
 * startup (typically with name {@code "icons"}) by the plugin-generated
 * pipeline; the codepoint constants come from the generated
 * {@code Icons} class. Usage:
 * <pre>{@code
 *   FontGroups.register(FontGroup.of("icons", iconAtlas, iconTexture));
 *   ...
 *   Component search = Icon.of(Icons.SEARCH, Em.of(1.2f), labelColor);
 * }</pre>
 *
 * <p>Default font group is {@code "icons"} for the no-fontGroup
 * convenience methods. Apps that register icon fonts under different
 * names should use the {@link #of(int, String, Em, Color)} overload.
 */
public final class Icon {

    /** Default font-group name for icon atlases. */
    public static final String DEFAULT_FONT_GROUP = "icons";

    private Icon() {}

    /** Render an icon glyph from the default {@code "icons"} font group. */
    public static Component.Text of(int codepoint, Em size, Color color) {
        return of(codepoint, DEFAULT_FONT_GROUP, size, color);
    }

    /**
     * Render an icon glyph from a named font group. Use when the app has
     * registered multiple icon fonts (e.g. one for utility icons, one
     * for brand glyphs).
     */
    public static Component.Text of(int codepoint, String fontGroup, Em size, Color color) {
        String content = codepointToString(codepoint);
        return new Component.Text(content, fontGroup, size, color,
            null, null, Em.ZERO,
            null, false,
            false, false, false, false, 0);
    }

    /**
     * Render an icon by source-library name via the generated
     * {@code Icons.byName} lookup pattern. Convenient at call sites that
     * read icon names from configuration or data rather than code, but
     * costs a hash-map lookup per call — prefer the codepoint overload
     * for hot paths.
     */
    public static Component.Text byName(IconLookup lookup, String name, Em size, Color color) {
        int cp = lookup.byName(name);
        if (cp < 0) throw new IllegalArgumentException("Unknown icon name: " + name);
        return of(cp, size, color);
    }

    /**
     * Small interface implemented by the generated {@code Icons} class so
     * the dasum-core {@link #byName} helper can accept it without
     * dasum-core depending on app-generated code. The generated class's
     * static {@code byName} method matches this shape — wrap it with
     * {@code Icons::byName} as a method reference at the call site.
     */
    @FunctionalInterface
    public interface IconLookup {
        int byName(String name);
    }

    /**
     * Encode a Unicode codepoint as a one- or two-char Java string.
     * Icon-font glyphs always fall in the BMP so the supplementary path
     * is a future-proofing belt for general callers — Lucide / Material
     * / Phosphor codepoints never reach it.
     */
    static String codepointToString(int codepoint) {
        if (codepoint < 0) throw new IllegalArgumentException("codepoint < 0: " + codepoint);
        if (Character.isBmpCodePoint(codepoint)) return String.valueOf((char) codepoint);
        return new String(Character.toChars(codepoint));
    }
}
