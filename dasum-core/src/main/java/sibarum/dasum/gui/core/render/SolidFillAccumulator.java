package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DYNAMIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TRIANGLES;

/**
 * Per-material batch accumulator for flat-shaded triangles. Vertex layout
 * (stride 24 bytes): pos {@code vec2} at location 0, color {@code vec4} at
 * location 1. Owns its own VAO/VBO; orchestrated by {@link Batcher}.
 */
final class SolidFillAccumulator {

    private static final int FLOATS_PER_VERTEX = 6;
    private static final int VERTEX_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int MAX_VERTICES = 4096;

    private final SolidFillMaterial material = new SolidFillMaterial();
    private int vao = 0;
    private int vbo = 0;

    private final float[] cpuBuffer = new float[MAX_VERTICES * FLOATS_PER_VERTEX];
    private int vertexCount = 0;

    private int drawCalls = 0;
    private int vertices = 0;

    void init() {
        material.init();
        vao = Gl.glGenVertexArray();
        vbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(vao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) MAX_VERTICES * VERTEX_BYTES, GL_DYNAMIC_DRAW);

        Gl.glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_BYTES, 0L);
        Gl.glEnableVertexAttribArray(0);
        Gl.glVertexAttribPointer(1, 4, GL_FLOAT, false, VERTEX_BYTES, 2L * Float.BYTES);
        Gl.glEnableVertexAttribArray(1);

        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
    }

    void beginFrame() {
        vertexCount = 0;
        drawCalls = 0;
        vertices = 0;
    }

    void submit(DrawCommand.ColoredTriangle t) {
        if (vertexCount + 3 > MAX_VERTICES) {
            throw new IllegalStateException("SolidFillAccumulator buffer overflow");
        }
        appendVertex(t.a(), t.cA());
        appendVertex(t.b(), t.cB());
        appendVertex(t.c(), t.cC());
    }

    void submit(DrawCommand.ColoredQuad q) {
        if (vertexCount + 6 > MAX_VERTICES) {
            throw new IllegalStateException("SolidFillAccumulator buffer overflow");
        }
        float x0 = q.x();
        float y0 = q.y();
        float x1 = x0 + q.width();
        float y1 = y0 + q.height();
        Color c = q.color();

        appendVertex(new Vec2(x0, y0), c);
        appendVertex(new Vec2(x1, y0), c);
        appendVertex(new Vec2(x1, y1), c);

        appendVertex(new Vec2(x0, y0), c);
        appendVertex(new Vec2(x1, y1), c);
        appendVertex(new Vec2(x0, y1), c);
    }

    void flush(float[] projection) {
        if (vertexCount == 0) return;

        int used = vertexCount * FLOATS_PER_VERTEX;
        float[] slice = new float[used];
        System.arraycopy(cpuBuffer, 0, slice, 0, used);

        Gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        Gl.glBufferSubData(GL_ARRAY_BUFFER, 0L, slice);

        material.bind(projection);
        Gl.glBindVertexArray(vao);
        Gl.glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        Gl.glBindVertexArray(0);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);

        drawCalls++;
        vertices += vertexCount;
        vertexCount = 0;
    }

    int drawCalls() { return drawCalls; }
    int vertices() { return vertices; }

    private void appendVertex(Vec2 pos, Color color) {
        int off = vertexCount * FLOATS_PER_VERTEX;
        cpuBuffer[off    ] = pos.x();
        cpuBuffer[off + 1] = pos.y();
        cpuBuffer[off + 2] = color.r();
        cpuBuffer[off + 3] = color.g();
        cpuBuffer[off + 4] = color.b();
        cpuBuffer[off + 5] = color.a();
        vertexCount++;
    }

    void close() {
        if (vbo != 0) { Gl.glDeleteBuffer(vbo); vbo = 0; }
        if (vao != 0) { Gl.glDeleteVertexArray(vao); vao = 0; }
        material.close();
    }
}
