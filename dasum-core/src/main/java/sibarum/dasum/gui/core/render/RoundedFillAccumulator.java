package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DYNAMIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TRIANGLES;

/**
 * Per-material batch accumulator for anti-aliased rounded rectangles.
 * Structurally identical to {@link SolidFillAccumulator} but with a fatter
 * vertex (stride 76 bytes): pos {@code vec2} (loc 0), local {@code vec2}
 * (loc 1), halfSize {@code vec2} (loc 2), radii {@code vec4} (loc 3), fill
 * {@code vec4} (loc 4), border {@code vec4} (loc 5), borderWidth {@code float}
 * (loc 6). Owns its own VAO/VBO; orchestrated by {@link Batcher}.
 */
final class RoundedFillAccumulator {

    private static final int FLOATS_PER_VERTEX = 19;
    private static final int VERTEX_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    /** Starting capacity; buffer grows geometrically on overflow. */
    private static final int INITIAL_VERTICES = 1024;

    private final RoundedFillMaterial material = new RoundedFillMaterial();
    private int vao = 0;
    private int vbo = 0;

    private float[] cpuBuffer = new float[INITIAL_VERTICES * FLOATS_PER_VERTEX];
    private int vertexCount = 0;
    private int gpuCapacityVertices = INITIAL_VERTICES;

    private int drawCalls = 0;
    private int vertices = 0;

    void init() {
        material.init();
        vao = Gl.glGenVertexArray();
        vbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(vao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) gpuCapacityVertices * VERTEX_BYTES, GL_DYNAMIC_DRAW);

        Gl.glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_BYTES, 0L);
        Gl.glEnableVertexAttribArray(0);
        Gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_BYTES, 2L * Float.BYTES);
        Gl.glEnableVertexAttribArray(1);
        Gl.glVertexAttribPointer(2, 2, GL_FLOAT, false, VERTEX_BYTES, 4L * Float.BYTES);
        Gl.glEnableVertexAttribArray(2);
        Gl.glVertexAttribPointer(3, 4, GL_FLOAT, false, VERTEX_BYTES, 6L * Float.BYTES);
        Gl.glEnableVertexAttribArray(3);
        Gl.glVertexAttribPointer(4, 4, GL_FLOAT, false, VERTEX_BYTES, 10L * Float.BYTES);
        Gl.glEnableVertexAttribArray(4);
        Gl.glVertexAttribPointer(5, 4, GL_FLOAT, false, VERTEX_BYTES, 14L * Float.BYTES);
        Gl.glEnableVertexAttribArray(5);
        Gl.glVertexAttribPointer(6, 1, GL_FLOAT, false, VERTEX_BYTES, 18L * Float.BYTES);
        Gl.glEnableVertexAttribArray(6);

        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
    }

    void beginFrame() {
        vertexCount = 0;
        drawCalls = 0;
        vertices = 0;
    }

    void submit(DrawCommand.RoundedQuad q) {
        ensureCapacity(6);
        float x0 = q.x();
        float y0 = q.y();
        float x1 = x0 + q.width();
        float y1 = y0 + q.height();
        float hx = q.width() * 0.5f;
        float hy = q.height() * 0.5f;

        // Two triangles: TL, TR, BR and TL, BR, BL. Local coords are the
        // signed corner offsets from the rect center (px, Y-down), so the
        // interpolated fragment 'local' is the SDF sample point.
        appendVertex(q, x0, y0, -hx, -hy, hx, hy); // TL
        appendVertex(q, x1, y0,  hx, -hy, hx, hy); // TR
        appendVertex(q, x1, y1,  hx,  hy, hx, hy); // BR

        appendVertex(q, x0, y0, -hx, -hy, hx, hy); // TL
        appendVertex(q, x1, y1,  hx,  hy, hx, hy); // BR
        appendVertex(q, x0, y1, -hx,  hy, hx, hy); // BL
    }

    void flush(float[] projection) {
        if (vertexCount == 0) return;

        int used = vertexCount * FLOATS_PER_VERTEX;
        float[] slice = new float[used];
        System.arraycopy(cpuBuffer, 0, slice, 0, used);

        Gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (vertexCount > gpuCapacityVertices) {
            Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) vertexCount * VERTEX_BYTES, GL_DYNAMIC_DRAW);
            gpuCapacityVertices = vertexCount;
        }
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

    private void ensureCapacity(int add) {
        int needed = (vertexCount + add) * FLOATS_PER_VERTEX;
        if (needed <= cpuBuffer.length) return;
        int newLen = cpuBuffer.length;
        while (newLen < needed) newLen *= 2;
        float[] grown = new float[newLen];
        System.arraycopy(cpuBuffer, 0, grown, 0, vertexCount * FLOATS_PER_VERTEX);
        cpuBuffer = grown;
    }

    int drawCalls() { return drawCalls; }
    int vertices() { return vertices; }

    private void appendVertex(DrawCommand.RoundedQuad q, float px, float py,
                              float lx, float ly, float hx, float hy) {
        int off = vertexCount * FLOATS_PER_VERTEX;
        Color fill = q.fill();
        Color border = q.borderColor();
        cpuBuffer[off     ] = px;
        cpuBuffer[off +  1] = py;
        cpuBuffer[off +  2] = lx;
        cpuBuffer[off +  3] = ly;
        cpuBuffer[off +  4] = hx;
        cpuBuffer[off +  5] = hy;
        cpuBuffer[off +  6] = q.rTL();
        cpuBuffer[off +  7] = q.rTR();
        cpuBuffer[off +  8] = q.rBR();
        cpuBuffer[off +  9] = q.rBL();
        cpuBuffer[off + 10] = fill.r();
        cpuBuffer[off + 11] = fill.g();
        cpuBuffer[off + 12] = fill.b();
        cpuBuffer[off + 13] = fill.a();
        cpuBuffer[off + 14] = border.r();
        cpuBuffer[off + 15] = border.g();
        cpuBuffer[off + 16] = border.b();
        cpuBuffer[off + 17] = border.a();
        cpuBuffer[off + 18] = q.borderWidthPx();
        vertexCount++;
    }

    void close() {
        if (vbo != 0) { Gl.glDeleteBuffer(vbo); vbo = 0; }
        if (vao != 0) { Gl.glDeleteVertexArray(vao); vao = 0; }
        material.close();
    }
}
