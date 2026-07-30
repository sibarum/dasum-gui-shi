package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

/**
 * The single UI material — one program that draws flat fills, rounded/bordered
 * rects, and MSDF glyphs (see {@code /shaders/unified.*}). Uniforms: the frame
 * projection, plus the MSDF atlas + its distance range used only by glyph
 * fragments. The atlas may be unset for a flush that contains no glyphs (the
 * shader's glyph branch is simply not taken), so {@link #bind} tolerates a null
 * atlas rather than throwing.
 */
public final class UnifiedMaterial implements Material {

    private static final String VERT_RESOURCE = "/shaders/unified.vert";
    private static final String FRAG_RESOURCE = "/shaders/unified.frag";

    private int program = 0;
    private int uProjection = -1;
    private int uAtlas = -1;
    private int uDistanceRange = -1;

    private Texture atlas;
    private float distanceRange = 4f;

    @Override
    public void init() {
        String vs = ShaderUtil.readResource(VERT_RESOURCE);
        String fs = ShaderUtil.readResource(FRAG_RESOURCE);
        program = ShaderUtil.buildProgram(vs, fs);
        uProjection    = Gl.glGetUniformLocation(program, "u_projection");
        uAtlas         = Gl.glGetUniformLocation(program, "u_atlas");
        uDistanceRange = Gl.glGetUniformLocation(program, "u_distanceRange");
        if (uProjection < 0) {
            throw new IllegalStateException("u_projection uniform not found in unified shader");
        }
        // u_atlas / u_distanceRange may be optimized out if a build ever stops
        // using the glyph branch; tolerate -1 there rather than fail the frame.
    }

    public void setAtlas(Texture atlas, float distanceRange) {
        this.atlas = atlas;
        this.distanceRange = distanceRange;
    }

    @Override
    public void bind(float[] projection) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uProjection, false, projection);
        if (uAtlas >= 0 && atlas != null) {
            atlas.bind(0);
            Gl.glUniform1i(uAtlas, 0);
        }
        if (uDistanceRange >= 0) {
            Gl.glUniform1f(uDistanceRange, distanceRange);
        }
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
