package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

/**
 * GL program for Lambert-shaded {@link sibarum.dasum.gui.vis.scene.TriangleLayer}s that carry
 * per-vertex normals (a surface mesh). Vertex layout pos3 + color3 + normal3 (stride 36 bytes).
 * Shades the interpolated per-vertex colour by a two-sided Lambert term against a world-space key
 * light, with an ambient floor — so a plotted surface reads as 3D form (light/dark by facing) while
 * its colormap still carries height. The unlit sibling is {@link FlatMaterial}.
 */
final class LitMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uOpacity = -1;
    private int uLight = -1;
    private int uAmbient = -1;

    /** Ambient floor: the darkest a fully back-facing patch gets (keeps it readable, not black). */
    private static final float AMBIENT = 0.35f;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/lit.vert");
        String fs = ShaderUtil.readResource("/shaders/lit.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp     = Gl.glGetUniformLocation(program, "u_mvp");
        uOpacity = Gl.glGetUniformLocation(program, "u_opacity");
        uLight   = Gl.glGetUniformLocation(program, "u_light");
        uAmbient = Gl.glGetUniformLocation(program, "u_ambient");
        if (uMvp < 0)     throw new IllegalStateException("u_mvp uniform missing");
        if (uOpacity < 0) throw new IllegalStateException("u_opacity uniform missing");
        if (uLight < 0)   throw new IllegalStateException("u_light uniform missing");
        if (uAmbient < 0) throw new IllegalStateException("u_ambient uniform missing");
    }

    /** @param light world-space unit direction toward the key light (from {@code keyLight(cam)}). */
    void bind(float[] mvp, float opacity, float[] light) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform1f(uOpacity, opacity);
        Gl.glUniform3f(uLight, light[0], light[1], light[2]);
        Gl.glUniform1f(uAmbient, AMBIENT);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
