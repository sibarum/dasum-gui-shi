package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

public final class SolidFillMaterial implements Material {

    private static final String VERT_RESOURCE = "/shaders/solid-fill.vert";
    private static final String FRAG_RESOURCE = "/shaders/solid-fill.frag";

    private int program = 0;
    private int uProjection = -1;

    @Override
    public void init() {
        String vs = ShaderUtil.readResource(VERT_RESOURCE);
        String fs = ShaderUtil.readResource(FRAG_RESOURCE);
        program = ShaderUtil.buildProgram(vs, fs);
        uProjection = Gl.glGetUniformLocation(program, "u_projection");
        if (uProjection < 0) {
            throw new IllegalStateException("u_projection uniform not found in solid-fill shader");
        }
    }

    @Override
    public void bind(float[] projection) {
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uProjection, false, projection);
    }

    @Override
    public void close() {
        if (program != 0) {
            Gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
