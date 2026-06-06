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

    void init() {
        String vs = ShaderUtil.readResource("/shaders/point.vert");
        String fs = ShaderUtil.readResource("/shaders/point.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp     = Gl.glGetUniformLocation(program, "u_mvp");
        uOpacity = Gl.glGetUniformLocation(program, "u_opacity");
        if (uMvp < 0)     throw new IllegalStateException("u_mvp uniform missing");
        if (uOpacity < 0) throw new IllegalStateException("u_opacity uniform missing");
    }

    void bind(float[] mvp, float opacity) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform1f(uOpacity, opacity);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
