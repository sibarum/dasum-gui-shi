package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.scene.VolumeLayer;

/**
 * GL program for {@link VolumeLayer}: a 3D-texture raymarcher. Geometry is the layer's bounding
 * box (a unit cube scaled/centred by uniforms, per-axis via {@code u_half}); the fragment stage
 * builds a camera ray, clips it to the box, and marches it, sampling the {@code sampler3D} volume
 * (bound to unit 0) and accumulating {@code rgb*a} emissively. Camera rays are constructed exactly
 * like {@link SdfMaterial}. The program compiles once at {@code init}.
 */
final class VolumeMaterial implements AutoCloseable {

    private int program = 0;
    private int uMvp = -1;
    private int uCenter = -1;
    private int uHalf = -1;
    private int uEye = -1;
    private int uForward = -1;
    private int uOrtho = -1;
    private int uMaxSteps = -1;
    private int uOpacity = -1;
    private int uVolume = -1;

    void init() {
        String vs = ShaderUtil.readResource("/shaders/volume.vert");
        String fs = ShaderUtil.readResource("/shaders/volume.frag");
        program = ShaderUtil.buildProgram(vs, fs);
        uMvp      = Gl.glGetUniformLocation(program, "u_mvp");
        uCenter   = Gl.glGetUniformLocation(program, "u_center");
        uHalf     = Gl.glGetUniformLocation(program, "u_half");
        uEye      = Gl.glGetUniformLocation(program, "u_eye");
        uForward  = Gl.glGetUniformLocation(program, "u_forward");
        uOrtho    = Gl.glGetUniformLocation(program, "u_ortho");
        uMaxSteps = Gl.glGetUniformLocation(program, "u_max_steps");
        uOpacity  = Gl.glGetUniformLocation(program, "u_opacity");
        uVolume   = Gl.glGetUniformLocation(program, "u_volume");
        if (uMvp < 0 || uCenter < 0 || uHalf < 0 || uEye < 0 || uForward < 0
                || uOrtho < 0 || uMaxSteps < 0 || uOpacity < 0 || uVolume < 0) {
            throw new IllegalStateException("volume shader missing required uniforms");
        }
    }

    /** The 3D texture must be bound to unit 0 before {@link #draw}. */
    void bind(float[] mvp, VolumeLayer layer, float[] eye, float[] forward, boolean ortho) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uMvp, false, mvp);
        Gl.glUniform3f(uCenter, layer.center().x(), layer.center().y(), layer.center().z());
        Gl.glUniform3f(uHalf, layer.halfExtent().x(), layer.halfExtent().y(), layer.halfExtent().z());
        Gl.glUniform3f(uEye, eye[0], eye[1], eye[2]);
        Gl.glUniform3f(uForward, forward[0], forward[1], forward[2]);
        Gl.glUniform1i(uOrtho, ortho ? 1 : 0);
        Gl.glUniform1i(uMaxSteps, layer.maxSteps());
        Gl.glUniform1f(uOpacity, layer.opacity());
        Gl.glUniform1i(uVolume, 0);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
