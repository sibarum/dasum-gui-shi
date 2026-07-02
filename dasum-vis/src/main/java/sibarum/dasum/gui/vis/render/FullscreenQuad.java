package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_STATIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TRIANGLES;

/**
 * A reusable NDC full-screen quad (two triangles, {@code pos2} at location 0) for post-process
 * passes. The shader derives its UV as {@code pos*0.5 + 0.5}. Main/GL-thread only.
 */
final class FullscreenQuad implements AutoCloseable {

    private static final float[] VERTS = {
            -1f, -1f,   1f, -1f,   -1f,  1f,   // tri 1
             1f, -1f,   1f,  1f,   -1f,  1f,   // tri 2
    };

    private int vao = 0;
    private int vbo = 0;

    void init() {
        if (vao != 0) return;
        vao = Gl.glGenVertexArray();
        vbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(vao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        Gl.glBufferData(GL_ARRAY_BUFFER, VERTS, GL_STATIC_DRAW);
        Gl.glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);
        Gl.glEnableVertexAttribArray(0);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
    }

    void draw() {
        Gl.glBindVertexArray(vao);
        Gl.glDrawArrays(GL_TRIANGLES, 0, 6);
        Gl.glBindVertexArray(0);
    }

    @Override
    public void close() {
        if (vbo != 0) { Gl.glDeleteBuffer(vbo); vbo = 0; }
        if (vao != 0) { Gl.glDeleteVertexArray(vao); vao = 0; }
    }
}
