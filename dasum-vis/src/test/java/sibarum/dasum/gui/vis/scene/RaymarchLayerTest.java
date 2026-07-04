package sibarum.dasum.gui.vis.scene;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.math.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation, defensive copying, and standard-shader assembly for the RaymarchLayer seam. */
final class RaymarchLayerTest {

    private static final String SRC = "in vec3 v_world; void main() { gl_FragDepth = 0.5; }";
    private static final Vec3 C = Vec3.ZERO;
    private static final Vec3 H = new Vec3(1f, 1f, 1f);

    @Test
    void defaultsAndAccessors() {
        RaymarchLayer r = new RaymarchLayer(SRC, C, H, Map.of("u_color", Uniform.vec4(1f, 0f, 0f, 1f)));
        assertEquals(BlendMode.OPAQUE, r.blend(), "a surface — OPAQUE writes depth");
        assertEquals(1f, r.opacity());
        assertSame(SRC, r.fragmentSource());
        assertEquals(1, r.uniforms().size());
    }

    @Test
    void uniformMapIsDefensivelyCopiedAndImmutable() {
        Map<String, Uniform> mutable = new LinkedHashMap<>();
        mutable.put("u_maxSteps", Uniform.i(64));
        RaymarchLayer r = new RaymarchLayer(SRC, C, H, mutable);

        mutable.put("u_maxSteps", Uniform.i(999)); // must not leak into the layer
        assertEquals(new Uniform.Int1(64), r.uniforms().get("u_maxSteps"));
        assertThrows(UnsupportedOperationException.class,
            () -> r.uniforms().put("x", Uniform.f(1f)));
    }

    @Test
    void rejectsSystemUniformNames() {
        for (String reserved : RaymarchLayer.SYSTEM_UNIFORMS) {
            assertThrows(IllegalArgumentException.class,
                () -> new RaymarchLayer(SRC, C, H, Map.of(reserved, Uniform.i(0))),
                reserved + " is renderer-owned");
        }
    }

    @Test
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new RaymarchLayer("  ", C, H, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RaymarchLayer(null, C, H, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RaymarchLayer(SRC, null, H, Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new RaymarchLayer(SRC, C, new Vec3(0f, 1f, 1f), Map.of()), "halfExtent must be > 0");
        assertThrows(IllegalArgumentException.class, () -> new RaymarchLayer(SRC, C, H, null));
        assertThrows(IllegalArgumentException.class,
            () -> new RaymarchLayer(SRC, C, H, Map.of(), BlendMode.ALPHA, 1.5f));
        assertThrows(IllegalArgumentException.class,
            () -> new RaymarchLayer(SRC, C, H, Map.of(), null, 1f));
    }

    @Test
    void withersChangeOnlyTheirField() {
        RaymarchLayer r = new RaymarchLayer(SRC, C, H, Map.of("u_maxSteps", Uniform.i(64)));

        RaymarchLayer moved = r.withCenter(new Vec3(2f, 0f, 0f)).withOpacity(0.5f).withBlend(BlendMode.ALPHA);
        assertEquals(2f, moved.center().x());
        assertEquals(0.5f, moved.opacity());
        assertEquals(BlendMode.ALPHA, moved.blend());
        assertSame(SRC, moved.fragmentSource());

        RaymarchLayer added = r.withUniform("u_relax", Uniform.f(0.7f));
        assertEquals(2, added.uniforms().size());
        assertEquals(new Uniform.Float1(0.7f), added.uniforms().get("u_relax"));
        assertEquals(1, r.uniforms().size(), "wither must not mutate the source layer");
    }

    @Test
    void standardFactoryAssemblesShaderAndDefaultUniforms() {
        RaymarchLayer r = RaymarchLayer.standard(
            "float map(vec3 p) { return length(p) - 0.8; }",
            C, new Vec3(1.2f, 1.2f, 1.2f), new Color(0.7f, 0.8f, 0.95f, 1f));

        String src = r.fragmentSource();
        assertTrue(src.contains("float map(vec3 p)"), "SDF spliced in");
        assertTrue(src.contains("gl_FragDepth"), "harness present");
        assertFalse(src.contains("//@SDF@"), "splice marker consumed");

        assertEquals(new Uniform.Int1(96), r.uniforms().get("u_maxSteps"));
        assertEquals(BlendMode.OPAQUE, r.blend());
        Uniform col = r.uniforms().get("u_color");
        assertEquals(new Uniform.Vec4f(0.7f, 0.8f, 0.95f, 1f), col);
    }

    @Test
    void uniformValueValidation() {
        assertThrows(IllegalArgumentException.class, () -> Uniform.vec4Array(new float[]{1f, 2f, 3f}));
        assertThrows(IllegalArgumentException.class, () -> Uniform.mat4(new float[]{1f}));
        assertNotSame(Uniform.i(1), Uniform.i(1)); // distinct instances, value-equal
        assertEquals(Uniform.i(1), Uniform.i(1));
    }

    @Test
    void standardRejectsBlankSdf() {
        assertThrows(IllegalArgumentException.class, () -> RaymarchShader.standard("  "));
    }
}
