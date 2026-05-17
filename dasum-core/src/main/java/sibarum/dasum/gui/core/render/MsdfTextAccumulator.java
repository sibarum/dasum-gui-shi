package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DYNAMIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TRIANGLES;

/**
 * Per-material batch accumulator for MSDF text glyphs. Vertex layout
 * (stride 32 bytes): pos {@code vec2} at location 0, uv {@code vec2} at
 * location 1, color {@code vec4} at location 2.
 * <p>
 * Each {@link DrawCommand.GlyphQuad} emits two triangles (6 vertices) since
 * we don't use an index buffer yet — adding EBOs is a later optimization
 * once batch sizes warrant it.
 */
final class MsdfTextAccumulator {

    private static final int FLOATS_PER_VERTEX = 8;
    private static final int VERTEX_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int VERTICES_PER_QUAD = 6;
    /** Starting capacity; the buffer grows geometrically on overflow. */
    private static final int INITIAL_QUADS = 1024;
    private static final int INITIAL_VERTICES = INITIAL_QUADS * VERTICES_PER_QUAD;

    private final MsdfTextMaterial material = new MsdfTextMaterial();
    private int vao = 0;
    private int vbo = 0;

    private float[] cpuBuffer = new float[INITIAL_VERTICES * FLOATS_PER_VERTEX];
    private int vertexCount = 0;
    /** Current GL-side capacity in vertices. Grown on flush when the
     *  CPU side accumulated more than fits. */
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
        Gl.glVertexAttribPointer(2, 4, GL_FLOAT, false, VERTEX_BYTES, 4L * Float.BYTES);
        Gl.glEnableVertexAttribArray(2);

        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
    }

    void setAtlas(Texture atlas, float distanceRange) {
        material.setAtlas(atlas, distanceRange);
    }

    void beginFrame() {
        vertexCount = 0;
        drawCalls = 0;
        vertices = 0;
    }

    void submit(DrawCommand.GlyphQuad q) {
        ensureCapacity(VERTICES_PER_QUAD);
        float x0 = q.x();
        float y0 = q.y();
        float x1 = x0 + q.width();
        float y1 = y0 + q.height();
        float uL = q.uv().left();
        float uR = q.uv().right();
        float vB = q.uv().bottom();
        float vT = q.uv().top();
        Color c = q.color();

        appendVertex(x0, y0, uL, vT, c);
        appendVertex(x1, y0, uR, vT, c);
        appendVertex(x1, y1, uR, vB, c);

        appendVertex(x0, y0, uL, vT, c);
        appendVertex(x1, y1, uR, vB, c);
        appendVertex(x0, y1, uL, vB, c);
    }

    void flush(float[] projection) {
        if (vertexCount == 0) return;

        int used = vertexCount * FLOATS_PER_VERTEX;
        float[] slice = new float[used];
        System.arraycopy(cpuBuffer, 0, slice, 0, used);

        Gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (vertexCount > gpuCapacityVertices) {
            // Reallocate the VBO to fit. glBufferData (the *Null* helper)
            // orphans the previous allocation atomically; the bound VAO's
            // vertex-attribute pointers remain valid because they index
            // by offset into whichever buffer is currently bound.
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

    /**
     * Grow {@link #cpuBuffer} geometrically when {@code add} more
     * vertices wouldn't fit. The GL-side resize happens lazily in
     * {@link #flush}.
     */
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

    private void appendVertex(float x, float y, float u, float v, Color c) {
        int off = vertexCount * FLOATS_PER_VERTEX;
        cpuBuffer[off    ] = x;
        cpuBuffer[off + 1] = y;
        cpuBuffer[off + 2] = u;
        cpuBuffer[off + 3] = v;
        cpuBuffer[off + 4] = c.r();
        cpuBuffer[off + 5] = c.g();
        cpuBuffer[off + 6] = c.b();
        cpuBuffer[off + 7] = c.a();
        vertexCount++;
    }

    void close() {
        if (vbo != 0) { Gl.glDeleteBuffer(vbo); vbo = 0; }
        if (vao != 0) { Gl.glDeleteVertexArray(vao); vao = 0; }
        material.close();
    }
}
