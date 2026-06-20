package sibarum.dasum.gui.msdf;

import org.apache.maven.plugin.MojoExecutionException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bakes a font-independent "missing glyph" (tofu) box into a text atlas after
 * msdf-atlas-gen has run. The box is a square outline whose signed distance
 * field we compute analytically — it does <em>not</em> come from the font, so
 * every atlas gets the same recognisable indicator regardless of which face
 * was rasterised, and even for fonts whose own {@code .notdef} is blank.
 *
 * <p>The synthesised glyph lands at codepoint U+FFFD (REPLACEMENT CHARACTER),
 * the slot {@code AtlasData.NOTDEF_CODEPOINT} reads at runtime. The op is
 * idempotent: if the JSON already carries U+FFFD the atlas is left untouched,
 * so it's safe to run on every (even incremental) build.
 *
 * <p>Two things are written:
 * <ul>
 *   <li>an SDF box is rasterised into a free cell of the PNG, encoded the same
 *       way msdf-atlas-gen encodes glyphs — {@code sample = 0.5 + d/range},
 *       written into R, G and B alike so the shader's {@code median(r,g,b)}
 *       decode returns it unchanged (an axis-aligned box needs no true
 *       multi-channel MSDF);</li>
 *   <li>a glyph entry is spliced into the JSON's {@code glyphs} array with the
 *       cell's atlas bounds, em-space plane bounds, and an advance matching the
 *       font's digit {@code '0'} so the box sits at a natural width.</li>
 * </ul>
 */
final class NotdefGlyph {

    private NotdefGlyph() {}

    static final int NOTDEF_CODEPOINT = 0xFFFD;

    // Box geometry, in em. The stroke and insets are deliberately modest so
    // the box reads as a placeholder rather than a filled block.
    private static final double INK_HEIGHT   = 0.70;  // baseline -> top of box
    private static final double SIDE_INSET   = 0.07;  // each side, inside the advance
    private static final double STROKE       = 0.06;  // outline thickness

    /**
     * Inject the missing-glyph box into {@code png}/{@code json} if not already
     * present. Returns {@code true} if it modified the files.
     */
    static boolean ensure(File png, File json) throws MojoExecutionException {
        String text;
        try {
            text = Files.readString(json.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed reading atlas JSON " + json, e);
        }
        if (text.contains("\"unicode\":" + NOTDEF_CODEPOINT)) {
            return false; // already baked — idempotent no-op
        }

        Atlas atlas = parseAtlas(text, json);
        double advance = parseDigitAdvance(text); // '0' advance, falls back to 0.55

        // Plane bounds (em). The SDF needs half-a-range of padding around the
        // ink so the field crosses 0.5 at the outline and reaches the cell edge
        // as "outside"; that padding is folded into the plane/atlas extent
        // exactly as msdf-atlas-gen does for real glyphs.
        double pad   = (atlas.distanceRange / 2.0) / atlas.size;
        double inkW  = advance - 2 * SIDE_INSET;
        if (inkW <= STROKE * 3) inkW = advance * 0.6; // degenerate-advance guard
        double inkLeft = (advance - inkW) / 2.0;

        double planeLeft   = inkLeft - pad;
        double planeBottom = -pad;                 // ink sits on the baseline
        double planeRight  = inkLeft + inkW + pad;
        double planeTop    = INK_HEIGHT + pad;

        int cellW = (int) Math.round((planeRight - planeLeft) * atlas.size);
        int cellH = (int) Math.round((planeTop - planeBottom) * atlas.size);
        if (cellW < 3 || cellH < 3) {
            throw new MojoExecutionException(
                "Computed notdef cell is degenerate (" + cellW + "x" + cellH + "px) for " + json.getName());
        }

        BufferedImage img;
        try {
            img = ImageIO.read(png);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed reading atlas PNG " + png, e);
        }
        if (img == null) {
            throw new MojoExecutionException("Unrecognised atlas PNG (ImageIO returned null): " + png);
        }
        if (img.getWidth() != atlas.width || img.getHeight() != atlas.height) {
            // The JSON drives runtime UVs; if it disagrees with the PNG, the
            // whole atlas is already broken — fail rather than bake blind.
            throw new MojoExecutionException("Atlas PNG size " + img.getWidth() + "x" + img.getHeight()
                + " disagrees with JSON " + atlas.width + "x" + atlas.height + " for " + png.getName());
        }

        Cell cell = findFreeCell(text, atlas, cellW, cellH);
        if (cell == null) {
            throw new MojoExecutionException(
                "No free " + cellW + "x" + cellH + "px region in atlas " + png.getName()
                + " to place the missing-glyph box.");
        }

        rasterizeBox(img, cell, atlas.distanceRange, atlas.size);
        try {
            ImageIO.write(img, "png", png);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed writing atlas PNG " + png, e);
        }

        // Atlas bounds in msdf coords: texel centres are at integers, bounds at
        // texel edges (±0.5). Convert the top-down cell to the JSON's yOrigin.
        double abLeft  = cell.x - 0.5;
        double abRight = cell.x + cellW - 0.5;
        double abTop, abBottom;
        if (atlas.yOriginBottom) {
            abTop    = atlas.height - cell.y + 0.5;
            abBottom = atlas.height - cell.y - cellH + 0.5;
        } else {
            abTop    = cell.y - 0.5;
            abBottom = cell.y + cellH - 0.5;
        }

        String glyph = "{\"unicode\":" + NOTDEF_CODEPOINT
            + ",\"advance\":" + num(advance)
            + ",\"planeBounds\":{\"left\":" + num(planeLeft) + ",\"bottom\":" + num(planeBottom)
            + ",\"right\":" + num(planeRight) + ",\"top\":" + num(planeTop) + "}"
            + ",\"atlasBounds\":{\"left\":" + num(abLeft) + ",\"bottom\":" + num(abBottom)
            + ",\"right\":" + num(abRight) + ",\"top\":" + num(abTop) + "}}";

        String injected = spliceGlyph(text, glyph, json);
        try {
            Files.writeString(json.toPath(), injected, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed writing atlas JSON " + json, e);
        }
        return true;
    }

    // --- SDF rasterisation -------------------------------------------------

    private static void rasterizeBox(BufferedImage img, Cell cell, double range, double size) {
        // Cell-local pixel rectangle for the box outline. The ink is inset
        // from the cell edge by half the distance range on every side.
        double half = range / 2.0;
        double outerL = half, outerT = half;
        double outerR = cell.w - half, outerB = cell.h - half;
        double t = STROKE * size; // stroke thickness in px
        double innerL = outerL + t, innerT = outerT + t;
        double innerR = outerR - t, innerB = outerB - t;

        for (int iy = 0; iy < cell.h; iy++) {
            for (int ix = 0; ix < cell.w; ix++) {
                double px = ix + 0.5, py = iy + 0.5;
                double dOuter = insideDist(px, py, outerL, outerT, outerR, outerB);
                double dInner = insideDist(px, py, innerL, innerT, innerR, innerB);
                // Frame = inside outer AND outside inner; positive within stroke.
                double frame = Math.min(dOuter, -dInner);
                double sample = 0.5 + frame / range;
                int v = clamp255((int) Math.round(sample * 255.0));
                int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
                img.setRGB(cell.x + ix, cell.y + iy, rgb);
            }
        }
    }

    /** Signed distance to an axis-aligned rect, positive INSIDE (exact at corners). */
    private static double insideDist(double px, double py, double l, double t, double r, double b) {
        double qx = Math.max(l - px, px - r); // >0 outside on x
        double qy = Math.max(t - py, py - b); // >0 outside on y
        double outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
        double inside = Math.min(Math.max(qx, qy), 0);
        return -(outside + inside); // flip: positive inside
    }

    // --- free-cell search --------------------------------------------------

    private static final Pattern ATLAS_BOUNDS = Pattern.compile(
        "\"atlasBounds\":\\{\"left\":([-0-9.eE]+),\"bottom\":([-0-9.eE]+),"
        + "\"right\":([-0-9.eE]+),\"top\":([-0-9.eE]+)\\}");

    /**
     * Find a top-down pixel cell of {@code cellW x cellH} (plus a 2px margin)
     * that overlaps no existing glyph. Uses a coarse occupancy grid built from
     * every {@code atlasBounds} in the JSON; the atlas is square and typically
     * mostly empty, so this nearly always lands on the first scanned row.
     */
    private static Cell findFreeCell(String text, Atlas atlas, int cellW, int cellH) {
        int margin = 2;
        int needW = cellW + 2 * margin, needH = cellH + 2 * margin;
        if (needW > atlas.width || needH > atlas.height) return null;

        boolean[] occ = new boolean[atlas.width * atlas.height];
        Matcher m = ATLAS_BOUNDS.matcher(text);
        while (m.find()) {
            double left   = Double.parseDouble(m.group(1));
            double bottom = Double.parseDouble(m.group(2));
            double right  = Double.parseDouble(m.group(3));
            double top    = Double.parseDouble(m.group(4));
            // To top-down pixel rows/cols, padded conservatively by 1px.
            int c0 = clamp((int) Math.floor(left) - 1, 0, atlas.width);
            int c1 = clamp((int) Math.ceil(right) + 1, 0, atlas.width);
            int r0, r1;
            if (atlas.yOriginBottom) {
                r0 = clamp((int) Math.floor(atlas.height - top) - 1, 0, atlas.height);
                r1 = clamp((int) Math.ceil(atlas.height - bottom) + 1, 0, atlas.height);
            } else {
                r0 = clamp((int) Math.floor(top) - 1, 0, atlas.height);
                r1 = clamp((int) Math.ceil(bottom) + 1, 0, atlas.height);
            }
            for (int r = r0; r < r1; r++) {
                int base = r * atlas.width;
                for (int c = c0; c < c1; c++) occ[base + c] = true;
            }
        }

        // Scan from the bottom of the image upward: msdf-atlas-gen packs from
        // the top, so the bottom rows are the likeliest free band.
        for (int y = atlas.height - needH; y >= 0; y--) {
            for (int x = 0; x <= atlas.width - needW; x++) {
                if (isFree(occ, atlas.width, x, y, needW, needH)) {
                    return new Cell(x + margin, y + margin, cellW, cellH);
                }
            }
        }
        return null;
    }

    private static boolean isFree(boolean[] occ, int w, int x, int y, int bw, int bh) {
        for (int r = y; r < y + bh; r++) {
            int base = r * w;
            for (int c = x; c < x + bw; c++) {
                if (occ[base + c]) return false;
            }
        }
        return true;
    }

    // --- JSON helpers ------------------------------------------------------

    private static String spliceGlyph(String text, String glyph, File json) throws MojoExecutionException {
        // Insert before the closing ']' of the (non-empty) glyphs array, which
        // msdf-atlas-gen always emits immediately before "kerning".
        int marker = text.indexOf("],\"kerning\"");
        if (marker < 0) throw new MojoExecutionException(
            "Could not locate end of glyphs array in " + json.getName() + " — unexpected msdf-atlas-gen format.");
        return text.substring(0, marker) + "," + glyph + text.substring(marker);
    }

    private static Atlas parseAtlas(String text, File json) throws MojoExecutionException {
        double range = dbl(text, "\"distanceRange\":([-0-9.eE]+)", "distanceRange", json);
        double size  = dbl(text, "\"size\":([-0-9.eE]+)", "size", json);
        int width    = (int) dbl(text, "\"width\":([0-9]+)", "width", json);
        int height   = (int) dbl(text, "\"height\":([0-9]+)", "height", json);
        Matcher yo = Pattern.compile("\"yOrigin\":\"([a-zA-Z]+)\"").matcher(text);
        boolean yBottom = !yo.find() || !"top".equalsIgnoreCase(yo.group(1));
        if (range <= 0 || size <= 0 || width <= 0 || height <= 0) {
            throw new MojoExecutionException("Invalid atlas header in " + json.getName());
        }
        return new Atlas(range, size, width, height, yBottom);
    }

    private static double parseDigitAdvance(String text) {
        Matcher m = Pattern.compile("\\{\"unicode\":48,\"advance\":([-0-9.eE]+)").matcher(text);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.55;
    }

    private static double dbl(String text, String regex, String field, File json) throws MojoExecutionException {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) throw new MojoExecutionException("Atlas JSON " + json.getName() + " missing " + field);
        return Double.parseDouble(m.group(1));
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.6f", v);
        // Trim trailing zeros for a tidier, smaller JSON (keep at least one digit).
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s += "0";
        }
        return s;
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    private static int clamp255(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private record Atlas(double distanceRange, double size, int width, int height, boolean yOriginBottom) {}
    private record Cell(int x, int y, int w, int h) {}
}
