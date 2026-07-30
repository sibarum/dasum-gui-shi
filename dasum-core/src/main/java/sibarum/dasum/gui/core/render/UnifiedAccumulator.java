package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DYNAMIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TRIANGLES;

/**
 * The single UI geometry accumulator. Every {@link DrawCommand} — flat quad /
 * triangle, rounded rect, MSDF glyph — is converted to fat vertices and appended
 * to ONE buffer in submission (painter's) order, then drawn with the one
 * {@link UnifiedMaterial}. Because there is a single ordered stream, cross-
 * primitive z-order is correct by construction: there are no separate buckets to
 * flush out of order (the old flat-vs-rounded-vs-glyph ordering bug is gone).
 *
 * <p>Fat vertex (stride {@value #FLOATS_PER_VERTEX} floats): pos {@code vec2}
 * (0), kind {@code float} (1), color {@code vec4} (2), coord {@code vec2} (3),
 * halfSize {@code vec2} (4), radii {@code vec4} (5), border {@code vec4} (6),
 * borderWidth {@code float} (7), edgePx {@code float} (8). Unused slots per kind
 * are zero.
 *
 * <p>Atlas: glyphs sample the currently-bound MSDF atlas. {@link #setAtlas}
 * flushes the pending stream first when the atlas changes (drawing what came
 * before under the outgoing atlas — flat/rounded fragments don't sample, so this
 * is harmless for them), preserving order across the swap. Folding the atlases
 * into a {@code sampler2DArray} to remove even these flushes (a literal
 * one-draw-call frame) is the next stage.
 */
final class UnifiedAccumulator {

    // 0:kind? no — layout: pos(2) kind(1) color(4) coord(2) halfSize(2) radii(4) border(4) borderWidth(1) edgePx(1)
    private static final int FLOATS_PER_VERTEX = 21;
    private static final int VERTEX_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int INITIAL_VERTICES = 4096;

    private static final float KIND_FLAT = 0f;
    private static final float KIND_ROUNDED = 1f;
    private static final float KIND_GLYPH = 2f;

    private final UnifiedMaterial material = new UnifiedMaterial();
    private int vao = 0;
    private int vbo = 0;

    private float[] cpuBuffer = new float[INITIAL_VERTICES * FLOATS_PER_VERTEX];
    private int vertexCount = 0;
    private int gpuCapacityVertices = INITIAL_VERTICES;

    private int drawCalls = 0;
    private int vertices = 0;

    private Texture currentAtlas = null;
    private float   currentDistanceRange = 0f;

    void init() {
        material.init();
        vao = Gl.glGenVertexArray();
        vbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(vao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) gpuCapacityVertices * VERTEX_BYTES, GL_DYNAMIC_DRAW);

        attrib(0, 2, 0);    // pos
        attrib(1, 1, 2);    // kind
        attrib(2, 4, 3);    // color
        attrib(3, 2, 7);    // coord
        attrib(4, 2, 9);    // halfSize
        attrib(5, 4, 11);   // radii
        attrib(6, 4, 15);   // border
        attrib(7, 1, 19);   // borderWidth
        attrib(8, 1, 20);   // edgePx

        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
    }

    private static void attrib(int loc, int size, int floatOffset) {
        Gl.glVertexAttribPointer(loc, size, GL_FLOAT, false, VERTEX_BYTES, (long) floatOffset * Float.BYTES);
        Gl.glEnableVertexAttribArray(loc);
    }

    void beginFrame() {
        vertexCount = 0;
        drawCalls = 0;
        vertices = 0;
        // Clear cached atlas identity so the first setAtlas of the frame always
        // registers as a change (mirrors the old text accumulator).
        currentAtlas = null;
        currentDistanceRange = 0f;
    }

    // ---- atlas control (glyphs) ----

    boolean willAtlasChange(Texture atlas, float distanceRange) {
        return atlas != currentAtlas || distanceRange != currentDistanceRange;
    }

    boolean hasPendingGeometry() { return vertexCount > 0; }

    /** Bind an atlas for subsequent glyphs; the {@link Batcher} flushes first on a real change. */
    void setAtlas(Texture atlas, float distanceRange) {
        material.setAtlas(atlas, distanceRange);
        currentAtlas = atlas;
        currentDistanceRange = distanceRange;
    }

    // ---- submission (all four primitives → the one stream) ----

    void submit(DrawCommand.ColoredQuad q) {
        ensureCapacity(6);
        float x0 = q.x(), y0 = q.y(), x1 = x0 + q.width(), y1 = y0 + q.height();
        Color c = q.color();
        flat(x0, y0, c); flat(x1, y0, c); flat(x1, y1, c);
        flat(x0, y0, c); flat(x1, y1, c); flat(x0, y1, c);
    }

    void submit(DrawCommand.ColoredTriangle t) {
        ensureCapacity(3);
        flat(t.a().x(), t.a().y(), t.cA());
        flat(t.b().x(), t.b().y(), t.cB());
        flat(t.c().x(), t.c().y(), t.cC());
    }

    void submit(DrawCommand.RoundedQuad q) {
        ensureCapacity(6);
        float x0 = q.x(), y0 = q.y(), x1 = x0 + q.width(), y1 = y0 + q.height();
        float hx = q.width() * 0.5f, hy = q.height() * 0.5f;
        rounded(q, x0, y0, -hx, -hy, hx, hy);
        rounded(q, x1, y0,  hx, -hy, hx, hy);
        rounded(q, x1, y1,  hx,  hy, hx, hy);
        rounded(q, x0, y0, -hx, -hy, hx, hy);
        rounded(q, x1, y1,  hx,  hy, hx, hy);
        rounded(q, x0, y1, -hx,  hy, hx, hy);
    }

    void submit(DrawCommand.GlyphQuad q) {
        ensureCapacity(6);
        float x0 = q.x(), y0 = q.y(), x1 = x0 + q.width(), y1 = y0 + q.height();
        float uL = q.uv().left(), uR = q.uv().right(), vB = q.uv().bottom(), vT = q.uv().top();
        Color c = q.color();
        float e = q.edgePx();
        glyph(x0, y0, uL, vT, c, e);
        glyph(x1, y0, uR, vT, c, e);
        glyph(x1, y1, uR, vB, c, e);
        glyph(x0, y0, uL, vT, c, e);
        glyph(x1, y1, uR, vB, c, e);
        glyph(x0, y1, uL, vB, c, e);
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

    int drawCalls() { return drawCalls; }
    int vertices() { return vertices; }

    void close() {
        if (vbo != 0) { Gl.glDeleteBuffer(vbo); vbo = 0; }
        if (vao != 0) { Gl.glDeleteVertexArray(vao); vao = 0; }
        material.close();
    }

    // ---- per-vertex writers ----

    private void flat(float px, float py, Color c) {
        appendVertex(px, py, KIND_FLAT, c, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, Color.TRANSPARENT, 0f, 0f);
    }

    private void rounded(DrawCommand.RoundedQuad q, float px, float py, float lx, float ly, float hx, float hy) {
        appendVertex(px, py, KIND_ROUNDED, q.fill(), lx, ly, hx, hy,
            q.rTL(), q.rTR(), q.rBR(), q.rBL(), q.borderColor(), q.borderWidthPx(), 0f);
    }

    private void glyph(float px, float py, float u, float v, Color c, float edgePx) {
        appendVertex(px, py, KIND_GLYPH, c, u, v, 0f, 0f, 0f, 0f, 0f, 0f, Color.TRANSPARENT, 0f, edgePx);
    }

    private void appendVertex(float px, float py, float kind, Color color,
                              float cx, float cy, float hx, float hy,
                              float rTL, float rTR, float rBR, float rBL,
                              Color border, float borderWidth, float edgePx) {
        int off = vertexCount * FLOATS_PER_VERTEX;
        cpuBuffer[off     ] = px;
        cpuBuffer[off +  1] = py;
        cpuBuffer[off +  2] = kind;
        cpuBuffer[off +  3] = color.r();
        cpuBuffer[off +  4] = color.g();
        cpuBuffer[off +  5] = color.b();
        cpuBuffer[off +  6] = color.a();
        cpuBuffer[off +  7] = cx;
        cpuBuffer[off +  8] = cy;
        cpuBuffer[off +  9] = hx;
        cpuBuffer[off + 10] = hy;
        cpuBuffer[off + 11] = rTL;
        cpuBuffer[off + 12] = rTR;
        cpuBuffer[off + 13] = rBR;
        cpuBuffer[off + 14] = rBL;
        cpuBuffer[off + 15] = border.r();
        cpuBuffer[off + 16] = border.g();
        cpuBuffer[off + 17] = border.b();
        cpuBuffer[off + 18] = border.a();
        cpuBuffer[off + 19] = borderWidth;
        cpuBuffer[off + 20] = edgePx;
        vertexCount++;
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
}
