package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

/**
 * GL program shared by {@link sibarum.dasum.gui.vis.scene.LineLayer} and
 * {@link sibarum.dasum.gui.vis.scene.TriangleLayer}: per-vertex colour
 * interpolation, per-layer opacity uniform, no point-sprite logic
 * (vertex layout pos3 + color3, stride 24 bytes). Only the draw mode
 * differs between the two layer types ({@code GL_LINES} vs
 * {@code GL_TRIANGLES}), so they share one program.
 */
final class FlatMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uOpacity = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/flat.vert");
        String fs = ShaderUtil.readResource("/shaders/flat.frag");
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
