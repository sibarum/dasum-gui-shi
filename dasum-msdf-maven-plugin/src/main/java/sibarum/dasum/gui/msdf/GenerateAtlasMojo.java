package sibarum.dasum.gui.msdf;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates MSDF (multi-channel signed distance field) atlases from TTF/OTF
 * fonts via a bundled msdf-atlas-gen.exe binary. Two operating modes via the
 * {@code mode} parameter (driven by Maven profile property {@code msdf.mode}):
 * <ul>
 *   <li>{@code primary} (default): invoke msdf-atlas-gen to regenerate outputs;
 *       fails loudly if the bundled binary is unavailable for the current OS.</li>
 *   <li>{@code prebuilt}: skip the binary; verify that pre-generated atlas
 *       files exist at the configured output path. Used on alt-OS builds.</li>
 * </ul>
 * The primary mode is incremental: if all atlas outputs are newer than the
 * input font and this Mojo's parameters haven't changed since the last run,
 * the atlas is left alone.
 */
@Mojo(name = "generate-atlas", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class GenerateAtlasMojo extends AbstractMojo {

    @Parameter(property = "msdf.mode", defaultValue = "primary")
    private String mode;

    /** Base output directory; each atlas writes {@code <name>.png} and {@code <name>.json} here. */
    @Parameter(required = true)
    private File outputDir;

    @Parameter(required = true)
    private List<AtlasConfig> atlases;

    /** Working directory for extracted binary and intermediate charset files. */
    @Parameter(defaultValue = "${project.build.directory}/dasum-msdf")
    private File workDir;

    /**
     * Injected so we can register generated icon-class source roots with
     * the project's compile-source path. Optional — only used when an
     * atlas has an {@code <icons>} block.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (atlases == null || atlases.isEmpty()) {
            getLog().warn("No <atlases> configured; nothing to do.");
            return;
        }

        boolean prebuilt = "prebuilt".equalsIgnoreCase(mode);
        if (!prebuilt && !"primary".equalsIgnoreCase(mode)) {
            throw new MojoExecutionException("Invalid mode '" + mode + "' — expected 'primary' or 'prebuilt'.");
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new MojoExecutionException("Failed to create output dir: " + outputDir);
        }

        File binary = prebuilt ? null : extractBinary();

        for (AtlasConfig cfg : atlases) {
            processAtlas(cfg, binary, prebuilt);
        }
    }

    private File extractBinary() throws MojoExecutionException {
        String resource = "/bin/windows-x64/msdf-atlas-gen.exe";
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            throw new MojoExecutionException(
                "msdf-atlas-gen.exe is bundled only for Windows in this plugin. " +
                "Use -P msdf-prebuilt to consume pre-generated atlases on other platforms, " +
                "or supply a binary for the current OS.");
        }

        File binDir = new File(workDir, "bin");
        if (!binDir.exists() && !binDir.mkdirs()) {
            throw new MojoExecutionException("Failed to create binary work dir: " + binDir);
        }
        File target = new File(binDir, "msdf-atlas-gen.exe");

        if (!target.exists()) {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new MojoExecutionException(
                        "Bundled binary not found at classpath resource " + resource +
                        " — the plugin JAR may be corrupted.");
                }
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to extract msdf-atlas-gen.exe", e);
            }
            if (!target.setExecutable(true, false)) {
                getLog().warn("Could not mark msdf-atlas-gen.exe executable (Windows usually doesn't require it).");
            }
        }
        return target;
    }

    private void processAtlas(AtlasConfig cfg, File binary, boolean prebuilt) throws MojoExecutionException {
        if (cfg.name == null || cfg.name.isBlank()) {
            throw new MojoExecutionException("Atlas missing required <name>.");
        }
        if (cfg.font == null) {
            throw new MojoExecutionException("Atlas '" + cfg.name + "' missing required <font>.");
        }
        if (cfg.icons != null && hasExtraFonts(cfg)) {
            throw new MojoExecutionException(
                "Atlas '" + cfg.name + "' sets both <icons> and <extraFonts> — they are mutually exclusive. " +
                "Icon mode builds its own charset from a manifest and cannot be merged with extra fonts.");
        }

        File pngOut  = new File(outputDir, cfg.name + ".png");
        File jsonOut = new File(outputDir, cfg.name + ".json");

        if (prebuilt) {
            if (!pngOut.isFile() || !jsonOut.isFile()) {
                throw new MojoExecutionException(
                    "Prebuilt atlas '" + cfg.name + "' missing at " + outputDir +
                    " (expected " + pngOut.getName() + " and " + jsonOut.getName() +
                    "). Switch to the default primary profile to regenerate, or commit the atlas.");
            }
            getLog().info("Atlas '" + cfg.name + "' (prebuilt): " + pngOut.getName() + " + " + jsonOut.getName());
            return;
        }

        if (!cfg.font.isFile()) {
            throw new MojoExecutionException("Atlas '" + cfg.name + "' font not found: " + cfg.font);
        }
        if (hasExtraFonts(cfg)) {
            for (FontSource src : cfg.extraFonts) {
                if (src.font == null || !src.font.isFile()) {
                    throw new MojoExecutionException(
                        "Atlas '" + cfg.name + "' extra font not found: " + src.font);
                }
            }
        }

        // Resolve the glyph selection up front. For icon-mode atlases the
        // selection drives BOTH the msdf charset file AND the generated
        // Java constants class — keep them in sync by computing once.
        Map<String, Integer> selectedIcons = (cfg.icons != null) ? resolveIconSelection(cfg) : null;

        if (isUpToDate(cfg, pngOut, jsonOut, selectedIcons)) {
            getLog().info("Atlas '" + cfg.name + "' is up to date — skipping.");
            // Even when skipping the atlas regeneration, ensure the
            // generated-sources root is registered so the Java compiler
            // picks up the previously-emitted Icons class on incremental
            // builds.
            if (cfg.icons != null) registerGeneratedSourceRoot(cfg.icons);
            // The missing-glyph box is baked idempotently, so a previously
            // generated (but not-yet-baked) atlas still gets it on this run.
            bakeNotdef(cfg, pngOut, jsonOut);
            return;
        }

        List<FontInput> inputs = buildFontInputs(cfg, selectedIcons);
        runMsdfAtlasGen(binary, cfg, inputs, pngOut, jsonOut);

        if (!pngOut.isFile() || !jsonOut.isFile()) {
            throw new MojoExecutionException(
                "msdf-atlas-gen completed but expected outputs are missing: " +
                pngOut + " / " + jsonOut);
        }
        getLog().info("Atlas '" + cfg.name + "' generated: " +
            pngOut.length() + " bytes png + " + jsonOut.length() + " bytes json");

        if (cfg.icons != null) {
            File java = IconsCodegen.generate(cfg.icons, selectedIcons, cfg.icons.manifestFormat);
            getLog().info("Icons class generated: " + java + " (" + selectedIcons.size() + " constants)");
            registerGeneratedSourceRoot(cfg.icons);
        }

        bakeNotdef(cfg, pngOut, jsonOut);
    }

    /**
     * Bake the font-independent missing-glyph (tofu) box into a text atlas.
     * Skipped for icon atlases — they carry no digit to size the box against,
     * and a missing icon codepoint is the calling app's concern, not the text
     * renderer's. The op is idempotent (no-op if the box is already present).
     */
    private void bakeNotdef(AtlasConfig cfg, File pngOut, File jsonOut) throws MojoExecutionException {
        if (cfg.icons != null) return;
        if (NotdefGlyph.ensure(pngOut, jsonOut)) {
            getLog().info("Atlas '" + cfg.name + "': baked missing-glyph box (U+FFFD).");
        }
    }

    /**
     * Parse the icon manifest and filter to the user's requested glyph
     * list. Result preserves manifest order so atlas + codegen output is
     * deterministic. Names not present in the manifest fail loudly with
     * the full list of offenders — silently dropping unknown icons would
     * be far too easy to miss.
     */
    private Map<String, Integer> resolveIconSelection(AtlasConfig cfg) throws MojoExecutionException {
        IconConfig ic = cfg.icons;
        Map<String, Integer> manifest = IconManifestParser.parse(ic.manifest, ic.manifestFormat);

        List<String> requested = readGlyphList(ic);
        if (requested.isEmpty()) {
            throw new MojoExecutionException(
                "Atlas '" + cfg.name + "' icons block has no glyphs configured " +
                "(set <glyphList> or <glyphListFile>).");
        }

        Map<String, Integer> selected = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String name : requested) {
            Integer cp = manifest.get(name);
            if (cp == null) missing.add(name);
            else selected.put(name, cp);
        }
        if (!missing.isEmpty()) {
            throw new MojoExecutionException(
                "Icon names not found in manifest " + ic.manifest.getName() + ": " + missing);
        }
        return selected;
    }

    private List<String> readGlyphList(IconConfig ic) throws MojoExecutionException {
        List<String> out = new ArrayList<>();
        if (ic.glyphList != null) {
            for (String entry : ic.glyphList) {
                if (entry == null) continue;
                // Inline <glyphList> entries can be comma-separated for
                // convenience: a single <glyph>search, settings, home</glyph>
                // splits as the user expects.
                for (String part : entry.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) out.add(trimmed);
                }
            }
        }
        if (ic.glyphListFile != null) {
            if (!ic.glyphListFile.isFile()) {
                throw new MojoExecutionException("glyphListFile not found: " + ic.glyphListFile);
            }
            try {
                for (String raw : Files.readAllLines(ic.glyphListFile.toPath(), StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    out.add(line);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed reading glyphListFile " + ic.glyphListFile, e);
            }
        }
        return out;
    }

    private void registerGeneratedSourceRoot(IconConfig ic) {
        if (project == null || ic.generatedSourcesDir == null) return;
        String path = ic.generatedSourcesDir.getAbsolutePath();
        if (!project.getCompileSourceRoots().contains(path)) {
            project.addCompileSourceRoot(path);
            getLog().info("Added generated-sources root: " + path);
        }
    }

    private boolean isUpToDate(AtlasConfig cfg, File pngOut, File jsonOut,
                               Map<String, Integer> selectedIcons) {
        if (!pngOut.isFile() || !jsonOut.isFile()) return false;
        long outputMtime = Math.min(pngOut.lastModified(), jsonOut.lastModified());
        if (cfg.font.lastModified() > outputMtime) return false;
        if (hasExtraFonts(cfg)) {
            // Each merged font is part of the input — a newer one means stale outputs.
            for (FontSource src : cfg.extraFonts) {
                if (src.font != null && src.font.lastModified() > outputMtime) return false;
            }
        }
        if (cfg.icons != null) {
            // The icon manifest is part of the input, as is the generated
            // Icons.java (re-emit if it's missing or stale even when the
            // PNG/JSON would otherwise be considered fresh).
            if (cfg.icons.manifest != null && cfg.icons.manifest.lastModified() > outputMtime) return false;
            if (cfg.icons.glyphListFile != null && cfg.icons.glyphListFile.lastModified() > outputMtime) return false;
            File icoJava = expectedIconsJavaFile(cfg.icons);
            if (icoJava != null && (!icoJava.isFile() || icoJava.lastModified() < outputMtime)) {
                return false;
            }
        }
        return true;
    }

    private File expectedIconsJavaFile(IconConfig ic) {
        if (ic.generatedSourcesDir == null || ic.packageName == null) return null;
        String className = (ic.className == null || ic.className.isBlank()) ? "Icons" : ic.className;
        File pkgDir = new File(ic.generatedSourcesDir, ic.packageName.replace('.', '/'));
        return new File(pkgDir, className + ".java");
    }

    private static boolean hasExtraFonts(AtlasConfig cfg) {
        return cfg.extraFonts != null && !cfg.extraFonts.isEmpty();
    }

    /**
     * Resolve the (font, charset-file) inputs for one atlas. Icon mode is a single
     * font with an explicit codepoint list. Text mode is the primary font plus any
     * {@code <extraFonts>}, each with its own charset file — msdf-atlas-gen merges
     * them into one atlas image via {@code -and}.
     */
    private List<FontInput> buildFontInputs(AtlasConfig cfg, Map<String, Integer> selectedIcons)
            throws MojoExecutionException {
        List<FontInput> inputs = new ArrayList<>();
        if (selectedIcons != null) {
            inputs.add(new FontInput(cfg.font, writeCharsetFile(cfg.name, 0, iconCharsetContent(selectedIcons))));
            return inputs;
        }
        inputs.add(new FontInput(cfg.font, writeCharsetFile(cfg.name, 0, resolveCharsetContent(cfg.charset))));
        if (hasExtraFonts(cfg)) {
            int index = 1;
            for (FontSource src : cfg.extraFonts) {
                inputs.add(new FontInput(src.font, writeCharsetFile(cfg.name, index, resolveCharsetContent(src.charset))));
                index++;
            }
        }
        return inputs;
    }

    /**
     * Resolve a charset specifier to msdf-atlas-gen charset-file content. Presets
     * {@code ascii}/{@code latin-1} expand to ranges; anything else is passed
     * through verbatim (see msdf-atlas-gen docs for the syntax).
     */
    private static String resolveCharsetContent(String charset) {
        String preset = charset == null ? "ascii" : charset.trim();
        return switch (preset.toLowerCase()) {
            case "ascii"   -> "[0x20, 0x7E]\n";
            case "latin-1", "latin1" -> "[0x20, 0x7E]\n[0xA0, 0xFF]\n";
            default        -> preset.endsWith("\n") ? preset : preset + "\n";
        };
    }

    /**
     * Icon mode — one explicit codepoint per line. msdf-atlas-gen accepts hex
     * ({@code 0x...}) or decimal codepoints separated by whitespace/newlines.
     */
    private static String iconCharsetContent(Map<String, Integer> selectedIcons) {
        StringBuilder sb = new StringBuilder(selectedIcons.size() * 8);
        for (Integer cp : selectedIcons.values()) {
            sb.append("0x").append(Integer.toHexString(cp).toUpperCase()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Write a charset file for one font input. Index 0 is the primary font
     * ({@code <name>.txt}); extra fonts get {@code <name>.<index>.txt}.
     */
    private File writeCharsetFile(String atlasName, int index, String content) throws MojoExecutionException {
        File charsetsDir = new File(workDir, "charsets");
        if (!charsetsDir.exists() && !charsetsDir.mkdirs()) {
            throw new MojoExecutionException("Failed to create charsets work dir: " + charsetsDir);
        }
        String fileName = (index == 0) ? atlasName + ".txt" : atlasName + "." + index + ".txt";
        File f = new File(charsetsDir, fileName);
        try {
            Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write charset file " + f, e);
        }
        return f;
    }

    private void runMsdfAtlasGen(File binary, AtlasConfig cfg, List<FontInput> inputs,
                                  File pngOut, File jsonOut) throws MojoExecutionException {
        List<String> cmd = new ArrayList<>();
        cmd.add(binary.getAbsolutePath());
        // One font segment per input; -and separates them so msdf-atlas-gen packs
        // every font's glyphs into the single output atlas (a "variants" JSON).
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) cmd.add("-and");
            FontInput in = inputs.get(i);
            cmd.add("-font");      cmd.add(in.font().getAbsolutePath());
            cmd.add("-charset");   cmd.add(in.charsetFile().getAbsolutePath());
        }
        cmd.add("-type");      cmd.add("msdf");
        cmd.add("-format");    cmd.add("png");
        cmd.add("-size");      cmd.add(Integer.toString(cfg.fontSize));
        cmd.add("-pxrange");   cmd.add(Integer.toString(cfg.pxRange));
        cmd.add("-dimensions");cmd.add(Integer.toString(cfg.atlasSize));
        cmd.add(Integer.toString(cfg.atlasSize));
        cmd.add("-imageout");  cmd.add(pngOut.getAbsolutePath());
        cmd.add("-json");      cmd.add(jsonOut.getAbsolutePath());

        getLog().info("msdf-atlas-gen: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        try {
            Process p = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    getLog().info("[msdf-atlas-gen] " + line);
                }
            }
            int exit = p.waitFor();
            if (exit != 0) {
                throw new MojoExecutionException("msdf-atlas-gen exited with code " + exit + " for atlas '" + cfg.name + "'.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to invoke msdf-atlas-gen", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while waiting for msdf-atlas-gen", e);
        }
    }

    /** One resolved font input: a font file paired with its written charset file. */
    private record FontInput(File font, File charsetFile) {}
}
