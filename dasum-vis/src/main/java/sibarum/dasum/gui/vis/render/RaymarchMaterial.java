package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.scene.RaymarchLayer;
import sibarum.dasum.gui.vis.scene.Uniform;

import java.util.HashMap;
import java.util.Map;

/**
 * GL program manager for {@link RaymarchLayer}: unlike the fixed-program
 * materials, this one compiles and caches a program <em>per distinct
 * fragment source</em>. The fixed {@code raymarch.vert} is linked with
 * each caller-supplied fragment shader; programs are cached by source
 * identity (the layer's structure hash) so identical shapes across the
 * scene share one program and compilation happens once, on the main
 * thread, at first sight.
 *
 * <p>System uniforms (camera / light / view mode) are pushed each frame
 * for whichever the program declares; every other declared uniform is
 * bound from the layer's {@link RaymarchLayer#uniforms()} map. Uniform
 * locations are memoized per program (an inactive/unused uniform resolves
 * to −1 and is silently skipped, which is expected — GLSL strips unused
 * uniforms).
 */
final class RaymarchMaterial implements AutoCloseable {

    private String vertSource;
    private final Map<String, Program> programs = new HashMap<>();

    private static final class Program {
        final int id;
        final Map<String, Integer> locs = new HashMap<>();
        Program(int id) { this.id = id; }
        int loc(String name) { return locs.computeIfAbsent(name, n -> Gl.glGetUniformLocation(id, n)); }
    }

    void init() {
        vertSource = ShaderUtil.readResource("/shaders/raymarch.vert");
    }

    /** Compile-once, cache-by-source. Main thread only (GL compile). */
    private Program programFor(String fragmentSource) {
        Program p = programs.get(fragmentSource);
        if (p == null) {
            p = new Program(ShaderUtil.buildProgram(vertSource, fragmentSource));
            programs.put(fragmentSource, p);
        }
        return p;
    }

    void bind(float[] mvp, RaymarchLayer layer, float[] eye, float[] forward,
              float[] lightDir, boolean ortho, int viewMode) {
        Program prog = programFor(layer.fragmentSource());
        Gl.glUseProgram(prog.id);

        // System uniforms — bound only if the shader declares them.
        int l;
        if ((l = prog.loc("u_mvp")) >= 0) Gl.glUniformMatrix4fv(l, false, mvp);
        if ((l = prog.loc("u_center")) >= 0)
            Gl.glUniform3f(l, layer.center().x(), layer.center().y(), layer.center().z());
        if ((l = prog.loc("u_halfExtent")) >= 0)
            Gl.glUniform3f(l, layer.halfExtent().x(), layer.halfExtent().y(), layer.halfExtent().z());
        if ((l = prog.loc("u_eye")) >= 0) Gl.glUniform3f(l, eye[0], eye[1], eye[2]);
        if ((l = prog.loc("u_forward")) >= 0) Gl.glUniform3f(l, forward[0], forward[1], forward[2]);
        if ((l = prog.loc("u_ortho")) >= 0) Gl.glUniform1i(l, ortho ? 1 : 0);
        if ((l = prog.loc("u_lightDir")) >= 0) Gl.glUniform3f(l, lightDir[0], lightDir[1], lightDir[2]);
        if ((l = prog.loc("u_viewMode")) >= 0) Gl.glUniform1i(l, viewMode);

        // Shape uniforms.
        for (Map.Entry<String, Uniform> e : layer.uniforms().entrySet()) {
            int loc = prog.loc(e.getKey());
            if (loc < 0) continue; // declared-but-unused, or absent — nothing to bind
            setUniform(loc, e.getValue());
        }
    }

    private static void setUniform(int loc, Uniform u) {
        switch (u) {
            case Uniform.Int1 v      -> Gl.glUniform1i(loc, v.value());
            case Uniform.Float1 v    -> Gl.glUniform1f(loc, v.value());
            case Uniform.Vec2 v      -> Gl.glUniform2f(loc, v.x(), v.y());
            case Uniform.Vec3f v     -> Gl.glUniform3f(loc, v.x(), v.y(), v.z());
            case Uniform.Vec4f v     -> Gl.glUniform4f(loc, v.x(), v.y(), v.z(), v.w());
            case Uniform.Vec4Array v -> Gl.glUniform4fv(loc, v.values().length / 4, v.values());
            case Uniform.Mat4 v      -> Gl.glUniformMatrix4fv(loc, false, v.m());
        }
    }

    @Override
    public void close() {
        for (Program p : programs.values()) {
            if (p.id != 0) Gl.glDeleteProgram(p.id);
        }
        programs.clear();
    }
}
