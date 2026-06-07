package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.scene.VexelRayLayer;

/**
 * GL program for {@link VexelRayLayer}: the R1 fixed-function sphere
 * tracer. Geometry is the layer's bounding cube (pos3, scaled/centred by
 * uniforms); everything else — field selection, parameters, camera rays,
 * shading colour — travels as uniforms, so the program compiles once at
 * init and never again. The fragment stage writes {@code gl_FragDepth}
 * from the hit point, which is what lets uploaded-geometry layers
 * depth-compose against the computed surface.
 */
final class VexelRayMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uCenter = -1;
    private int uScale = -1;
    private int uEye = -1;
    private int uForward = -1;
    private int uOrtho = -1;
    private int uLightDir = -1;
    private int uFieldType = -1;
    private int uParams = -1;
    private int uColor = -1;
    private int uMaxSteps = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/vexelray.vert");
        String fs = ShaderUtil.readResource("/shaders/vexelray.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp       = Gl.glGetUniformLocation(program, "u_mvp");
        uCenter    = Gl.glGetUniformLocation(program, "u_center");
        uScale     = Gl.glGetUniformLocation(program, "u_scale");
        uEye       = Gl.glGetUniformLocation(program, "u_eye");
        uForward   = Gl.glGetUniformLocation(program, "u_forward");
        uLightDir  = Gl.glGetUniformLocation(program, "u_lightDir");
        uOrtho     = Gl.glGetUniformLocation(program, "u_ortho");
        uFieldType = Gl.glGetUniformLocation(program, "u_fieldType");
        uParams    = Gl.glGetUniformLocation(program, "u_params");
        uColor     = Gl.glGetUniformLocation(program, "u_color");
        uMaxSteps  = Gl.glGetUniformLocation(program, "u_maxSteps");
        if (uMvp < 0 || uCenter < 0 || uScale < 0 || uEye < 0 || uForward < 0
                || uLightDir < 0 || uOrtho < 0 || uFieldType < 0 || uParams < 0
                || uColor < 0 || uMaxSteps < 0) {
            throw new IllegalStateException("vexelray shader missing required uniforms");
        }
    }

    void bind(float[] mvp, VexelRayLayer layer, float[] eye, float[] forward,
              float[] lightDir, boolean ortho) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform3f(uCenter, layer.center().x(), layer.center().y(), layer.center().z());
        Gl.glUniform1f(uScale, layer.scale());
        Gl.glUniform3f(uEye, eye[0], eye[1], eye[2]);
        Gl.glUniform3f(uForward, forward[0], forward[1], forward[2]);
        Gl.glUniform3f(uLightDir, lightDir[0], lightDir[1], lightDir[2]);
        Gl.glUniform1i(uOrtho, ortho ? 1 : 0);
        Gl.glUniform1i(uFieldType, layer.field().ordinal());
        float[] p = layer.params();
        Gl.glUniform4f(uParams, p[0], p[1], p[2], p[3]);
        Gl.glUniform4f(uColor,
            layer.color().r(), layer.color().g(), layer.color().b(),
            layer.color().a() * layer.opacity());
        Gl.glUniform1i(uMaxSteps, layer.maxSteps());
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
