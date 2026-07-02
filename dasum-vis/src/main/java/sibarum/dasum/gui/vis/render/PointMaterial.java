package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

/**
 * GL program for {@link sibarum.dasum.gui.vis.scene.PointLayer} drawing:
 * round screen-space dots with per-vertex size (vertex layout
 * pos3 + color3 + size1, stride 28 bytes) and a per-layer opacity
 * uniform. The renderer owns one instance, bound once per point layer.
 */
final class PointMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uOpacity = -1;
    private int uPointScale = -1;
    private int uSoftness = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/point.vert");
        String fs = ShaderUtil.readResource("/shaders/point.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp        = Gl.glGetUniformLocation(program, "u_mvp");
        uOpacity    = Gl.glGetUniformLocation(program, "u_opacity");
        uPointScale = Gl.glGetUniformLocation(program, "u_point_scale");
        uSoftness   = Gl.glGetUniformLocation(program, "u_softness");
        if (uMvp < 0)     throw new IllegalStateException("u_mvp uniform missing");
        if (uOpacity < 0) throw new IllegalStateException("u_opacity uniform missing");
        if (uPointScale < 0) throw new IllegalStateException("u_point_scale uniform missing");
        if (uSoftness < 0) throw new IllegalStateException("u_softness uniform missing");
    }

    /** Screen-pixel hard dots (scatter): {@code a_size} used as-is, no falloff. */
    void bind(float[] mvp, float opacity) {
        bind(mvp, opacity, 0f, 0f);
    }

    /**
     * @param pointScale 0 → {@code a_size} is screen pixels; &gt; 0 → {@code a_size} is a world
     *                   diameter scaled by {@code pointScale / gl_Position.w} (perspective sizing).
     *                   Pass {@code proj[1][1] * viewportHeightPx / 2}.
     * @param softness   0 → hard round dot; &gt; 0 → Gaussian soft blob (volumetrics), width scaled.
     */
    void bind(float[] mvp, float opacity, float pointScale, float softness) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform1f(uOpacity, opacity);
        Gl.glUniform1f(uPointScale, pointScale);
        Gl.glUniform1f(uSoftness, softness);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
