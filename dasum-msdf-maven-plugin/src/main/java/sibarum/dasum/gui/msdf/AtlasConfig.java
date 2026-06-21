package sibarum.dasum.gui.msdf;

import java.io.File;
import java.util.List;

/**
 * One entry under the plugin's {@code <atlases>} block. Field names match
 * the XML element names Maven injects into. Plain POJO with public fields
 * is the friendliest shape for Maven parameter injection.
 */
public class AtlasConfig {

    /** Logical atlas name; output files are {@code <name>.png} and {@code <name>.json}. */
    public String name;

    /** Path to the TTF/OTF input font, typically relative to {@code project.basedir}. */
    public File font;

    /**
     * Charset specifier. Supported presets:
     * <ul>
     *   <li>{@code ascii} (printable ASCII, 0x20–0x7E) — the default</li>
     *   <li>{@code latin-1} (printable ASCII + Latin-1 Supplement)</li>
     * </ul>
     * Anything else is passed through verbatim as one line in the
     * generated msdf-atlas-gen charset file (see msdf-atlas-gen docs for
     * the syntax — supports ranges like {@code [0x20, 0x7E]} and literal
     * strings).
     */
    public String charset = "ascii";

    /** Atlas image dimensions in pixels (square). */
    public int atlasSize = 1024;

    /** Glyph em size in pixels within the atlas. */
    public int fontSize = 32;

    /** SDF distance range in output pixels. */
    public int pxRange = 4;

    /**
     * Optional icon-mode configuration. When non-null, the atlas is
     * generated from a named glyph subset of an icon font and a Java
     * {@code Icons} class is emitted alongside the PNG/JSON. The
     * {@link #charset} field is ignored in this mode.
     */
    public IconConfig icons;

    /**
     * Optional extra fonts merged into <em>this</em> atlas. Each contributes its
     * own charset; all glyphs are packed into the same PNG via msdf-atlas-gen's
     * {@code -and} separator, so the calling app sees one atlas / one font group
     * spanning every supplied font. Useful for sourcing glyphs the primary font
     * lacks (arrows, math symbols, …) without splitting into a second atlas.
     *
     * <p>Mutually exclusive with {@link #icons} — icon mode builds its own charset
     * from a manifest and emits a Java constants class, which doesn't compose with
     * multi-font merging.
     */
    public List<FontSource> extraFonts;
}
