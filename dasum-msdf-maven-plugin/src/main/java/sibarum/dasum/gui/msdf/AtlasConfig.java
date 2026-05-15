package sibarum.dasum.gui.msdf;

import java.io.File;

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
}
