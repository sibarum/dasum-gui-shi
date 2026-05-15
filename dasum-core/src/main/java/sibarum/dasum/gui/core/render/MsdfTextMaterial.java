package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

public final class MsdfTextMaterial implements Material {

    private static final String VERT_RESOURCE = "/shaders/msdf-text.vert";
    private static final String FRAG_RESOURCE = "/shaders/msdf-text.frag";

    private int program = 0;
    private int uProjection = -1;
    private int uAtlas = -1;
    private int uDistanceRange = -1;

    private Texture atlas;
    private float distanceRange = 4f;

    @Override
    public void init() {
        String vs = ShaderUtil.readResource(VERT_RESOURCE);
        String fs = ShaderUtil.readResource(FRAG_RESOURCE);
        program = ShaderUtil.buildProgram(vs, fs);
        uProjection    = Gl.glGetUniformLocation(program, "u_projection");
        uAtlas         = Gl.glGetUniformLocation(program, "u_atlas");
        uDistanceRange = Gl.glGetUniformLocation(program, "u_distanceRange");
        if (uProjection < 0 || uAtlas < 0 || uDistanceRange < 0) {
            throw new IllegalStateException("MSDF shader missing required uniforms");
        }
    }

    public void setAtlas(Texture atlas, float distanceRange) {
        this.atlas = atlas;
        this.distanceRange = distanceRange;
    }

    @Override
    public void bind(float[] projection) {
        if (atlas == null) throw new IllegalStateException("MsdfTextMaterial has no atlas bound");
        Gl.glUseProgram(program);
        Gl.glUniformMatrix4fv(uProjection, false, projection);
        atlas.bind(0);
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
