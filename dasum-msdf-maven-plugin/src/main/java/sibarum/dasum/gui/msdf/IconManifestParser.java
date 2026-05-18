package sibarum.dasum.gui.msdf;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an icon library's name→codepoint manifest. One format per icon
 * library convention:
 *
 * <ul>
 *   <li><b>lucide</b> — JSON file (typically {@code info.json}) shaped as
 *       <pre>{
 *   "a-arrow-down": {"encodedCode":"\\ea2c", "prefix":"lucide",
 *                    "name":"a-arrow-down", "unicode":"ea2c"},
 *   ...
 * }</pre>
 *       The icon name is the object key; the codepoint is the
 *       {@code "unicode"} field inside the value object, parsed as hex.</li>
 *   <li><b>material</b> — plain text {@code codepoints} file:
 *       <pre>search e8b6
 * settings e8b8
 * ...</pre>
 *       The first whitespace-separated token is the name; the second is
 *       the codepoint as a hex string (no {@code 0x} prefix).</li>
 * </ul>
 *
 * <p>Both parsers preserve insertion order so generator output is
 * deterministic and diff-friendly when source manifests update.
 */
final class IconManifestParser {

    private IconManifestParser() {}

    /**
     * Parse {@code manifest} according to {@code format}. Returns a
     * {@code LinkedHashMap} of icon name → codepoint, preserving source
     * order so downstream codegen is reproducible.
     */
    static Map<String, Integer> parse(File manifest, String format) throws MojoExecutionException {
        if (manifest == null) throw new MojoExecutionException("Icon manifest path is required.");
        if (!manifest.isFile()) throw new MojoExecutionException("Icon manifest not found: " + manifest);
        if (format == null || format.isBlank()) {
            throw new MojoExecutionException("Icon manifestFormat is required (lucide | material).");
        }
        String fmt = format.trim().toLowerCase();
        String body;
        try {
            body = Files.readString(manifest.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed reading icon manifest " + manifest + ": " + e.getMessage(), e);
        }
        return switch (fmt) {
            case "lucide"   -> parseLucide(body);
            case "material" -> parseMaterial(body);
            default -> throw new MojoExecutionException(
                "Unknown manifestFormat '" + format + "' (supported: lucide, material).");
        };
    }

    // Lucide info.json: object whose keys are icon names and whose values
    // are sub-objects with an "encodedCode" hex string preceded by an
    // escaped backslash (in JSON: "\\e585"; in memory after JSON unescape
    // would be "\e585"; but we're reading the file as raw text so we see
    // the literal two characters "\" + "\" + "e" + "5" + "8" + "5"... wait
    // no — the file uses ONE escaped backslash in source. So the on-disk
    // bytes are: \\e585 (a backslash literal in JSON source means the
    // string value contains one backslash). We extract whatever follows
    // the backslash(es) up to the closing quote.
    //
    // The regex tolerates either escaped (\\) or unescaped (\) lead-ins
    // so it's robust to both JSON-quoted and raw value variants.
    //
    // Avoiding a full JSON parser keeps the plugin dependency-free; the
    // structural assumption (no nested objects per entry) holds for every
    // Lucide info.json shipped to date.
    private static final Pattern LUCIDE_ENTRY = Pattern.compile(
        "\"([A-Za-z0-9][A-Za-z0-9_\\-]*)\"\\s*:\\s*\\{([^{}]*)\\}",
        Pattern.DOTALL);
    private static final Pattern ENCODED_CODE_FIELD = Pattern.compile(
        "\"encodedCode\"\\s*:\\s*\"\\\\+([0-9a-fA-F]+)\"");

    private static Map<String, Integer> parseLucide(String body) throws MojoExecutionException {
        Map<String, Integer> out = new LinkedHashMap<>();
        Matcher m = LUCIDE_ENTRY.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            String inside = m.group(2);
            Matcher u = ENCODED_CODE_FIELD.matcher(inside);
            if (!u.find()) continue; // some top-level keys (e.g. metadata) won't have an encodedCode
            int cp;
            try {
                cp = Integer.parseInt(u.group(1), 16);
            } catch (NumberFormatException e) {
                throw new MojoExecutionException(
                    "Lucide manifest: invalid encodedCode for '" + name + "': " + u.group(1));
            }
            out.put(name, cp);
        }
        if (out.isEmpty()) {
            throw new MojoExecutionException("Lucide manifest parsed zero icons — check the file format.");
        }
        return out;
    }

    // Material codepoints text file: name<whitespace>hex per line. Blank
    // lines and lines starting with # are ignored so users can carry
    // notes inline.
    private static Map<String, Integer> parseMaterial(String body) throws MojoExecutionException {
        Map<String, Integer> out = new LinkedHashMap<>();
        int lineNo = 0;
        for (String raw : body.split("\\R")) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                throw new MojoExecutionException(
                    "Material manifest line " + lineNo + ": expected 'name codepoint', got: " + raw);
            }
            String name = parts[0];
            int cp;
            try {
                cp = Integer.parseInt(parts[1], 16);
            } catch (NumberFormatException e) {
                throw new MojoExecutionException(
                    "Material manifest line " + lineNo + ": invalid hex codepoint '" + parts[1] + "'");
            }
            out.put(name, cp);
        }
        if (out.isEmpty()) {
            throw new MojoExecutionException("Material manifest parsed zero icons.");
        }
        return out;
    }
}
