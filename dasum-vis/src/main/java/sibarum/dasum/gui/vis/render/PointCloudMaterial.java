package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

/**
 * GL program + uniforms for the point-cloud shader. The renderer owns
 * one instance; it is bound once per viewport draw (after the batcher
 * has flushed its 2D geometry).
 */
final class PointCloudMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uPointSize = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/point-cloud.vert");
        String fs = ShaderUtil.readResource("/shaders/point-cloud.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp       = Gl.glGetUniformLocation(program, "u_mvp");
        uPointSize = Gl.glGetUniformLocation(program, "u_pointSize");
        if (uMvp < 0)       throw new IllegalStateException("u_mvp uniform missing");
        if (uPointSize < 0) throw new IllegalStateException("u_pointSize uniform missing");
    }

    void bind(float[] mvp, float pointSizePx) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform1f(uPointSize, pointSizePx);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
