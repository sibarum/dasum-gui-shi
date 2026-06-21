package sibarum.dasum.gui.msdf;

import java.io.File;

/**
 * One supplementary font merged into an atlas alongside the primary
 * {@link AtlasConfig#font}. Each source contributes its own charset; its glyphs
 * are packed into the same PNG via msdf-atlas-gen's {@code -and} separator.
 * Field names match the XML element names Maven injects into.
 *
 * @see AtlasConfig#extraFonts
 */
public class FontSource {

    /** Path to the TTF/OTF input font, typically relative to {@code project.basedir}. */
    public File font;

    /**
     * Charset specifier for this font, resolved identically to
     * {@link AtlasConfig#charset} (presets {@code ascii} / {@code latin-1}, or a
     * verbatim msdf-atlas-gen charset such as {@code [0x2190, 0x21FF]}). Supplementary
     * fonts typically use explicit ranges for the codepoints the primary font lacks.
     */
    public String charset = "ascii";
}
