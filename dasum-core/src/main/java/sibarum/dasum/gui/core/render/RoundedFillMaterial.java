package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

/**
 * Material for anti-aliased rounded rectangles with an optional inset border.
 * Mirrors {@link SolidFillMaterial} — a single {@code u_projection} uniform,
 * with all per-quad geometry (half extent, per-corner radii, fill/border
 * colors, border width) carried per-vertex so quads still batch into one draw
 * call. The rounding + border are computed in the fragment shader as a
 * signed-distance field.
 */
public final class RoundedFillMaterial implements Material {

    private static final String VERT_RESOURCE = "/shaders/rounded-fill.vert";
    private static final String FRAG_RESOURCE = "/shaders/rounded-fill.frag";

    private int program = 0;
    private int uProjection = -1;

    @Override
    public void init() {
        String vs = ShaderUtil.readResource(VERT_RESOURCE);
        String fs = ShaderUtil.readResource(FRAG_RESOURCE);
        program = ShaderUtil.buildProgram(vs, fs);
        uProjection = Gl.glGetUniformLocation(program, "u_projection");
        if (uProjection < 0) {
            throw new IllegalStateException("u_projection uniform not found in rounded-fill shader");
        }
    }

    @Override
    public void bind(float[] projection) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uProjection, false, projection);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
