package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

/**
 * GL program for {@link sibarum.dasum.gui.vis.scene.ImageLayer} drawing:
 * a textured world-space quad (vertex layout pos3 + uv2, stride 20
 * bytes) sampled from texture unit 0, with a per-layer opacity uniform.
 */
final class ImageMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uTexture = -1;
    private int uOpacity = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/image.vert");
        String fs = ShaderUtil.readResource("/shaders/image.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp     = Gl.glGetUniformLocation(program, "u_mvp");
        uTexture = Gl.glGetUniformLocation(program, "u_texture");
        uOpacity = Gl.glGetUniformLocation(program, "u_opacity");
        if (uMvp < 0)     throw new IllegalStateException("u_mvp uniform missing");
        if (uTexture < 0) throw new IllegalStateException("u_texture uniform missing");
        if (uOpacity < 0) throw new IllegalStateException("u_opacity uniform missing");
    }

    /** Caller binds the texture to unit 0 before drawing. */
    void bind(float[] mvp, float opacity) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform1i(uTexture, 0);
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
