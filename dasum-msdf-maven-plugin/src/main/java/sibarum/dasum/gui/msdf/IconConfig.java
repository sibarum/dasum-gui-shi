package sibarum.dasum.gui.msdf;

import java.io.File;
import java.util.List;

/**
 * Optional {@code <icons>} block inside an {@link AtlasConfig}. When set,
 * the atlas is generated from a named subset of glyphs in an icon font's
 * manifest rather than a {@code charset} preset. The plugin also emits a
 * Java {@code Icons} class with one constant per selected glyph so apps
 * reference icons by name instead of literal codepoints.
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>{@link AtlasConfig#font} points at the icon font's TTF.</li>
 *   <li>{@link #manifest} points at the icon library's name→codepoint
 *       map; {@link #manifestFormat} tells the plugin how to parse it
 *       ({@code lucide} for {@code info.json}, {@code material} for the
 *       {@code codepoints} text file).</li>
 *   <li>{@link #glyphList} or {@link #glyphListFile} names the icons to
 *       include. Anything not listed isn't baked into the atlas, so the
 *       output stays small even when the source font has thousands of
 *       glyphs.</li>
 *   <li>The generator writes the atlas PNG + JSON next to the text
 *       atlases, and writes {@code Icons.java} under
 *       {@link #generatedSourcesDir} in the package
 *       {@link #packageName}. The plugin adds that directory to the
 *       project's compile-source roots automatically.</li>
 * </ol>
 *
 * <h3>Name normalization</h3>
 * Source library names ({@code "arrow-up"}, {@code "check_circle"}) are
 * normalized to Java constants ({@code ARROW_UP}, {@code CHECK_CIRCLE})
 * by the following rules, in order:
 * <ol>
 *   <li>Replace {@code -}, {@code _}, and whitespace with {@code _}.</li>
 *   <li>Uppercase.</li>
 *   <li>Replace any non-{@code [A-Z0-9_]} character with {@code _}, then
 *       collapse consecutive {@code _} to one.</li>
 *   <li>If the result starts with a digit, prefix {@code _}
 *       ({@code 1st-place-medal} → {@code _1ST_PLACE_MEDAL}).</li>
 *   <li>If the result is a Java reserved word, suffix {@code _}.</li>
 * </ol>
 * Two source names that normalize to the same constant cause a build
 * failure listing both — there is no silent overwrite.
 *
 * <p>The generated {@code Icons} class also exposes
 * {@code byName(String)} which accepts <em>either</em> the original
 * library name ({@code "arrow-up"}) or the normalized name
 * ({@code "ARROW_UP"}).
 */
public class IconConfig {

    /** Path to the icon library's name→codepoint manifest. */
    public File manifest;

    /**
     * Format of {@link #manifest}. Supported values:
     * <ul>
     *   <li>{@code lucide} — JSON object keyed by icon name with a
     *       {@code "unicode"} hex string field per entry.</li>
     *   <li>{@code material} — plain text, {@code name codepointHex}
     *       per line.</li>
     * </ul>
     */
    public String manifestFormat;

    /**
     * Icon names to include, in the source library's convention
     * ({@code "arrow-up"} for Lucide, {@code "arrow_upward"} for
     * Material). Either this or {@link #glyphListFile} must be set.
     */
    public List<String> glyphList;

    /**
     * Alternative to {@link #glyphList}: a text file with one icon name
     * per line. Comments start with {@code #}; blank lines are ignored.
     */
    public File glyphListFile;

    /**
     * Root directory under which the generated {@code Icons.java} is
     * written. The plugin adds this directory to the project's
     * compile-source roots so the class is picked up by the standard
     * Java compiler step. The {@link #packageName} determines the
     * subdirectory inside this root.
     */
    public File generatedSourcesDir;

    /** Java package for the generated class. Required when icons are configured. */
    public String packageName;

    /** Class name. */
    public String className = "Icons";
}
