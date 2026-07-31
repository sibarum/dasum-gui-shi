package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

/**
 * GL program for {@link sibarum.dasum.gui.vis.scene.TextLayer} drawing:
 * MSDF glyphs in world space. Vertex layout is glyph-local
 * offset2 + uv2 (stride 16 bytes); the layer's anchor and orientation
 * basis arrive as uniforms so billboard re-orientation never touches
 * vertex data ({@code world = anchor + right*off.x + up*off.y}).
 * Fragment stage is the same median/screenPxRange MSDF logic as the
 * core 2D text shader; the caller binds the font group's atlas to
 * texture unit 0.
 */
final class SceneTextMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uAnchor = -1;
    private int uRight = -1;
    private int uUp = -1;
    private int uColor = -1;
    private int uOutlineColor = -1;
    private int uOutlineWidth = -1;
    private int uAtlas = -1;
    private int uDistanceRange = -1;
    private int uViewportPx = -1;
    private int uPixelMode = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/scene-text.vert");
        String fs = ShaderUtil.readResource("/shaders/scene-text.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp           = Gl.glGetUniformLocation(program, "u_mvp");
        uAnchor        = Gl.glGetUniformLocation(program, "u_anchor");
        uRight         = Gl.glGetUniformLocation(program, "u_right");
        uUp            = Gl.glGetUniformLocation(program, "u_up");
        uColor         = Gl.glGetUniformLocation(program, "u_color");
        uOutlineColor  = Gl.glGetUniformLocation(program, "u_outlineColor");
        uOutlineWidth  = Gl.glGetUniformLocation(program, "u_outlineWidth");
        uAtlas         = Gl.glGetUniformLocation(program, "u_atlas");
        uDistanceRange = Gl.glGetUniformLocation(program, "u_distanceRange");
        uViewportPx    = Gl.glGetUniformLocation(program, "u_viewportPx");
        uPixelMode     = Gl.glGetUniformLocation(program, "u_pixelMode");
        if (uMvp < 0 || uAnchor < 0 || uRight < 0 || uUp < 0
                || uColor < 0 || uOutlineColor < 0 || uOutlineWidth < 0
                || uAtlas < 0 || uDistanceRange < 0
                || uViewportPx < 0 || uPixelMode < 0) {
            throw new IllegalStateException("scene-text shader missing required uniforms");
        }
    }

    /**
     * Caller binds the atlas texture to unit 0 before drawing.
     *
     * @param basis 6 floats: right xyz, up xyz (see CameraMath.viewBasis)
     */
    void bind(float[] mvp, float ax, float ay, float az, float[] basis,
              Color color, float opacity, float distanceRange,
              Color outlineColor, float outlineWidth,
              float viewportW, float viewportH, boolean pixelMode) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform3f(uAnchor, ax, ay, az);
        Gl.glUniform3f(uRight, basis[0], basis[1], basis[2]);
        Gl.glUniform3f(uUp,    basis[3], basis[4], basis[5]);
        Gl.glUniform2f(uViewportPx, viewportW, viewportH);
        Gl.glUniform1i(uPixelMode, pixelMode ? 1 : 0);
        Gl.glUniform4f(uColor, color.r(), color.g(), color.b(), color.a() * opacity);
        // Outline alpha is premultiplied by the layer opacity too, so a faded label fades its corona.
        Gl.glUniform4f(uOutlineColor, outlineColor.r(), outlineColor.g(), outlineColor.b(),
                       outlineColor.a() * opacity);
        Gl.glUniform1f(uOutlineWidth, outlineWidth);
        Gl.glUniform1i(uAtlas, 0);
        Gl.glUniform1f(uDistanceRange, distanceRange);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
