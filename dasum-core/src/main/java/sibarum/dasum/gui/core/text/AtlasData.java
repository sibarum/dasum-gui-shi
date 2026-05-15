package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.json.Json;
import sibarum.dasum.gui.core.render.Rect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed contents of an msdf-atlas-gen JSON output. Use
 * {@link #glyph(int)} for codepoint lookup.
 */
public record AtlasData(AtlasInfo info, FontMetrics metrics, Map<Integer, GlyphData> glyphs) {

    public GlyphData glyph(int codepoint) {
        return glyphs.get(codepoint);
    }

    public static AtlasData loadFromResource(String classpathPath) {
        try (InputStream in = AtlasData.class.getResourceAsStream(classpathPath)) {
            if (in == null) throw new IllegalStateException("Atlas JSON not found on classpath: " + classpathPath);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading atlas JSON " + classpathPath, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static AtlasData parse(String jsonText) {
        Map<String, Object> root = Json.parseObject(jsonText);

        Map<String, Object> atlasObj = (Map<String, Object>) root.get("atlas");
        AtlasInfo info = new AtlasInfo(
            (String) atlasObj.get("type"),
            asFloat(atlasObj.get("distanceRange")),
            asFloat(atlasObj.get("size")),
            asInt(atlasObj.get("width")),
            asInt(atlasObj.get("height")),
            !"top".equalsIgnoreCase(String.valueOf(atlasObj.getOrDefault("yOrigin", "bottom")))
        );

        Map<String, Object> metricsObj = (Map<String, Object>) root.get("metrics");
        FontMetrics metrics = new FontMetrics(
            asFloat(metricsObj.get("emSize")),
            asFloat(metricsObj.get("lineHeight")),
            asFloat(metricsObj.get("ascender")),
            asFloat(metricsObj.get("descender")),
            asFloat(metricsObj.get("underlineY")),
            asFloat(metricsObj.get("underlineThickness"))
        );

        List<Object> glyphList = (List<Object>) root.get("glyphs");
        Map<Integer, GlyphData> glyphs = new HashMap<>(glyphList.size() * 2);
        for (Object g : glyphList) {
            Map<String, Object> gm = (Map<String, Object>) g;
            int cp = asInt(gm.get("unicode"));
            float advance = asFloat(gm.get("advance"));
            Rect plane = asRect((Map<String, Object>) gm.get("planeBounds"));
            Rect atlas = asRect((Map<String, Object>) gm.get("atlasBounds"));
            glyphs.put(cp, new GlyphData(cp, advance, plane, atlas));
        }

        return new AtlasData(info, metrics, glyphs);
    }

    private static Rect asRect(Map<String, Object> m) {
        if (m == null) return null;
        return new Rect(
            asFloat(m.get("left")),
            asFloat(m.get("bottom")),
            asFloat(m.get("right")),
            asFloat(m.get("top"))
        );
    }

    private static float asFloat(Object o) {
        if (o == null) return 0f;
        return ((Number) o).floatValue();
    }

    private static int asInt(Object o) {
        if (o == null) return 0;
        return ((Number) o).intValue();
    }
}
