package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

/**
 * GL program + uniforms for the line-segment shader. Sibling of
 * {@link PointCloudMaterial} — same vertex attribute layout (vec3 pos +
 * vec3 colour) so a single VAO definition works for either, but separate
 * shader because the point fragment shader's round-dot discard would
 * clip every fragment of a line (gl_PointCoord is undefined / zero in
 * line rasterization).
 */
final class LineSegmentMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/line-segment.vert");
        String fs = ShaderUtil.readResource("/shaders/line-segment.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp = Gl.glGetUniformLocation(program, "u_mvp");
        if (uMvp < 0) throw new IllegalStateException("u_mvp uniform missing");
    }

    void bind(float[] mvp) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
