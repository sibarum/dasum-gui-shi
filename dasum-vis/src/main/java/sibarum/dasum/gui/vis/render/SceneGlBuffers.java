package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.render.Texture;
import sibarum.dasum.gui.core.render.Texture3D;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.GlyphData;
import sibarum.dasum.gui.core.render.Rect;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.scene.ImageLayer;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.PointLayer;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.TextLayer;
import sibarum.dasum.gui.vis.scene.TriangleLayer;
import sibarum.dasum.gui.vis.scene.VexelRayLayer;
import sibarum.dasum.gui.vis.scene.VolumeLayer;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DYNAMIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;

/**
 * Per-component GPU resource cache for scene layers. Lives on the GLFW
 * main thread; the only cross-thread interaction is the pending-delete
 * queue, drained at the start of each render pass.
 *
 * <p><b>Layer-granular identity skip:</b> slots are keyed by layer
 * <em>reference identity</em> within each component. Publishing a new
 * scene re-uploads only the layers whose references changed; untouched
 * layer instances keep their GPU resources.
 *
 * <p><b>Slot pooling:</b> slots evicted by a publish park in per-kind
 * free pools and are reused by the next new layer of the same kind —
 * including grown VBO capacity and (for image slots) the GL texture,
 * which is re-filled via {@code glTexSubImage2D} when dimensions match
 * rather than reallocated. A worker streaming image frames therefore
 * pays one sub-image upload per frame and zero GL object churn.
 *
 * <p>Vertex layouts by kind: POINT pos3+color3+size1 (28B); FLAT
 * pos3+color3 (24B) for lines and triangles; IMAGE pos3+uv2 (20B);
 * TEXT offset2+uv2 (16B — the anchor and orientation are uniforms).
 * Null layer colors fill with the framework default (0.85, 0.90, 1.00).
 */
final class SceneGlBuffers {

    private static final float DEF_R = 0.85f, DEF_G = 0.90f, DEF_B = 1.00f;

    enum Kind {
        POINT(7), FLAT(6), IMAGE(5), TEXT(4), VEXEL(3), VOLUME(3);

        final int floatsPerVertex;
        Kind(int f) { this.floatsPerVertex = f; }

        static Kind of(Layer l) {
            return switch (l) {
                case PointLayer p    -> POINT;
                case LineLayer ln    -> FLAT;
                case TriangleLayer t -> FLAT;
                case ImageLayer i    -> IMAGE;
                case TextLayer t     -> TEXT;
                case VexelRayLayer v -> VEXEL;
                case VolumeLayer v   -> VOLUME;
            };
        }
    }

    /**
     * Unit cube [-1, 1]³ as 12 triangles — the raymarch bounding volume.
     * Layer-independent: the shader scales/centres via uniforms, so every
     * VEXEL slot uploads these same 36 vertices.
     */
    private static final float[] UNIT_CUBE = buildUnitCube();

    private static float[] buildUnitCube() {
        float[][] c = {
            {-1,-1,-1}, {1,-1,-1}, {1,1,-1}, {-1,1,-1},
            {-1,-1, 1}, {1,-1, 1}, {1,1, 1}, {-1,1, 1},
        };
        int[][] quads = {
            {0,1,2,3},  // back  (z = -1)
            {5,4,7,6},  // front (z = +1)
            {4,0,3,7},  // left
            {1,5,6,2},  // right
            {3,2,6,7},  // top
            {4,5,1,0},  // bottom
        };
        float[] out = new float[36 * 3];
        int w = 0;
        for (int[] q : quads) {
            int[] order = {q[0], q[1], q[2], q[0], q[2], q[3]};
            for (int v : order) {
                out[w++] = c[v][0];
                out[w++] = c[v][1];
                out[w++] = c[v][2];
            }
        }
        return out;
    }

    static final class Slot {
        final Kind kind;
        int vao = 0;
        int vbo = 0;
        int capacityVertices = 0;
        int vertexCount = 0;
        // IMAGE slots only.
        Texture texture = null;
        boolean smooth = true;
        // VOLUME slots only.
        Texture3D volumeTexture = null;

        Slot(Kind kind) { this.kind = kind; }
    }

    static final class Entry {
        SceneSnapshot lastScene = null;
        Map<Layer, Slot> slots = new IdentityHashMap<>();
        final Map<Kind, ArrayDeque<Slot>> freePools = new EnumMap<>(Kind.class);

        Slot slot(Layer l) { return slots.get(l); }

        ArrayDeque<Slot> pool(Kind k) {
            return freePools.computeIfAbsent(k, kk -> new ArrayDeque<>());
        }
    }

    private final Map<Component, Entry> entries = new IdentityHashMap<>();
    private final Queue<Component> pendingDelete = new ConcurrentLinkedQueue<>();

    /** Safe from any thread — used by the detach cleaner. */
    void scheduleDelete(Component c) {
        pendingDelete.offer(c);
    }

    /** Main-thread only. Drain queued deletions before the render pass. */
    void drainPendingDeletes() {
        Component c;
        while ((c = pendingDelete.poll()) != null) {
            Entry e = entries.remove(c);
            if (e != null) deleteEntry(e);
        }
    }

    /**
     * Ensure GPU resources for {@code c} are in sync with {@code scene}.
     * Returns the entry whose {@link Entry#slot} resolves each layer.
     * Main-thread only.
     */
    Entry ensure(Component c, SceneSnapshot scene) {
        Entry e = entries.computeIfAbsent(c, k -> new Entry());
        if (e.lastScene == scene) return e;

        // Carry over slots whose layer instance survives into the new scene.
        Map<Layer, Slot> next = new IdentityHashMap<>();
        for (Layer l : scene.layers()) {
            Slot s = e.slots.remove(l);
            if (s != null) next.put(l, s);
        }
        // Whatever is left was evicted — park in the free pools for reuse.
        for (Slot s : e.slots.values()) {
            e.pool(s.kind).push(s);
        }
        e.slots.clear();

        // Upload layers that are new this scene.
        for (Layer l : scene.layers()) {
            if (next.containsKey(l)) continue;
            Kind kind = Kind.of(l);
            ArrayDeque<Slot> pool = e.pool(kind);
            Slot s = pool.isEmpty() ? createSlot(kind) : pool.pop();
            upload(s, l);
            next.put(l, s);
        }

        e.slots = next;
        e.lastScene = scene;
        return e;
    }

    // ---- upload ----

    private static void upload(Slot s, Layer layer) {
        float[] verts = switch (layer) {
            case PointLayer p    -> buildPointVertices(p);
            case LineLayer l     -> buildFlatVertices(l.endpoints(), l.colors());
            case TriangleLayer t -> buildFlatVertices(t.vertices(), t.colors());
            case ImageLayer img  -> { syncImageTexture(s, img); yield buildImageVertices(img); }
            case TextLayer txt   -> buildTextVertices(txt);
            case VexelRayLayer v -> UNIT_CUBE; // geometry is uniform-driven; cube is constant
            case VolumeLayer v   -> { syncVolumeTexture(s, v); yield UNIT_CUBE; }
        };
        int floatsPerVertex = s.kind.floatsPerVertex;
        int vertexCount = verts.length / floatsPerVertex;
        int vertexBytes = floatsPerVertex * Float.BYTES;

        Gl.glBindBuffer(GL_ARRAY_BUFFER, s.vbo);
        if (vertexCount > s.capacityVertices) {
            Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) vertexCount * vertexBytes, GL_DYNAMIC_DRAW);
            s.capacityVertices = vertexCount;
        }
        if (vertexCount > 0) {
            Gl.glBufferSubData(GL_ARRAY_BUFFER, 0L, verts);
        }
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        s.vertexCount = vertexCount;
    }

    /** Create / refill / refilter the image slot's texture as needed. */
    private static void syncImageTexture(Slot s, ImageLayer img) {
        if (s.texture != null
                && s.texture.width() == img.width() && s.texture.height() == img.height()) {
            s.texture.update(img.rgba());
        } else {
            if (s.texture != null) s.texture.close();
            s.texture = Texture.fromRgba(img.rgba(), img.width(), img.height());
            s.smooth = true; // fromRgba defaults to LINEAR
        }
        if (s.smooth != img.smooth()) {
            s.texture.setFilter(img.smooth());
            s.smooth = img.smooth();
        }
    }

    /** Upload the volume slot's RGBA32F 3D texture. Runs only on a layer identity change (the
     *  scene's upload skip), so a fresh texture per (re)published volume — replace any prior one. */
    private static void syncVolumeTexture(Slot s, VolumeLayer v) {
        if (s.volumeTexture != null) s.volumeTexture.close();
        s.volumeTexture = Texture3D.fromRgbaFloats(v.rgba(), v.nx(), v.ny(), v.nz());
    }

    private static float[] buildPointVertices(PointLayer p) {
        int n = p.pointCount();
        float[] pos = p.positions();
        float[] col = p.colors();
        float[] sizes = p.sizes();
        float defSize = p.defaultSizePx();
        float[] out = new float[n * Kind.POINT.floatsPerVertex];
        for (int i = 0; i < n; i++) {
            int off = i * Kind.POINT.floatsPerVertex;
            out[off    ] = pos[i * 3    ];
            out[off + 1] = pos[i * 3 + 1];
            out[off + 2] = pos[i * 3 + 2];
            if (col != null) {
                out[off + 3] = col[i * 3    ];
                out[off + 4] = col[i * 3 + 1];
                out[off + 5] = col[i * 3 + 2];
            } else {
                out[off + 3] = DEF_R;
                out[off + 4] = DEF_G;
                out[off + 5] = DEF_B;
            }
            out[off + 6] = sizes != null ? sizes[i] : defSize;
        }
        return out;
    }

    /** Lines and triangles share the layout: xyz passthrough + RGB/default. */
    private static float[] buildFlatVertices(float[] xyz, float[] col) {
        int n = xyz.length / 3;
        float[] out = new float[n * Kind.FLAT.floatsPerVertex];
        for (int i = 0; i < n; i++) {
            int off = i * Kind.FLAT.floatsPerVertex;
            out[off    ] = xyz[i * 3    ];
            out[off + 1] = xyz[i * 3 + 1];
            out[off + 2] = xyz[i * 3 + 2];
            if (col != null) {
                out[off + 3] = col[i * 3    ];
                out[off + 4] = col[i * 3 + 1];
                out[off + 5] = col[i * 3 + 2];
            } else {
                out[off + 3] = DEF_R;
                out[off + 4] = DEF_G;
                out[off + 5] = DEF_B;
            }
        }
        return out;
    }

    /**
     * Two triangles spanning the corner quad. Texture rows were uploaded
     * flipped ({@link Texture#fromRgba}: source top row at t=1), so the
     * TOP corners sample v=1 and the bottom corners v=0.
     */
    private static float[] buildImageVertices(ImageLayer img) {
        float[] c = img.corners(); // TL, TR, BR, BL (xyz each)
        return new float[]{
            // pos                    uv
            c[0], c[1], c[2],   0f, 1f,   // TL
            c[3], c[4], c[5],   1f, 1f,   // TR
            c[6], c[7], c[8],   1f, 0f,   // BR

            c[0], c[1], c[2],   0f, 1f,   // TL
            c[6], c[7], c[8],   1f, 0f,   // BR
            c[9], c[10], c[11], 0f, 0f,   // BL
        };
    }

    /**
     * Glyph quads in the layer's local frame: offsets in world units
     * relative to the anchor (baseline at y=0), UVs into the font
     * group's MSDF atlas. {@code GlyphData.planeBounds} is em-units
     * Y-up, which maps directly onto the world plane — no flip, unlike
     * the screen-space path in {@code GlyphLayout.build}.
     */
    private static float[] buildTextVertices(TextLayer txt) {
        FontGroup fg = FontGroups.getOrDefault(txt.fontGroup());
        float size = txt.heightWorld();
        float atlasW = fg.atlas().info().width();
        float atlasH = fg.atlas().info().height();
        String text = txt.text();

        // Pass 1: total advance for alignment.
        float total = 0f;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            GlyphData g = fg.atlas().glyph(cp);
            if (g != null) total += g.advance() * size;
            i += Character.charCount(cp);
        }
        float pen = switch (txt.align()) {
            case LEFT   -> 0f;
            case CENTER -> -total * 0.5f;
            case RIGHT  -> -total;
        };

        // Pass 2: emit quads (6 verts × offset2+uv2 per printable glyph).
        // Worst-case capacity: one quad per char; trim at the end.
        float[] out = new float[text.length() * 6 * Kind.TEXT.floatsPerVertex];
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            GlyphData g = fg.atlas().glyph(cp);
            if (g != null) {
                Rect pb = g.planeBounds();
                Rect ab = g.atlasBounds();
                if (pb != null && ab != null) {
                    float x0 = pen + pb.left()  * size;
                    float x1 = pen + pb.right() * size;
                    float y0 = pb.bottom() * size;
                    float y1 = pb.top()    * size;
                    float u0 = ab.left()   / atlasW;
                    float u1 = ab.right()  / atlasW;
                    float v0 = ab.bottom() / atlasH;
                    float v1 = ab.top()    / atlasH;

                    w = put(out, w, x0, y1, u0, v1); // top-left
                    w = put(out, w, x1, y1, u1, v1); // top-right
                    w = put(out, w, x1, y0, u1, v0); // bottom-right

                    w = put(out, w, x0, y1, u0, v1); // top-left
                    w = put(out, w, x1, y0, u1, v0); // bottom-right
                    w = put(out, w, x0, y0, u0, v0); // bottom-left
                }
                pen += g.advance() * size;
            }
            i += Character.charCount(cp);
        }
        if (w == out.length) return out;
        float[] trimmed = new float[w];
        System.arraycopy(out, 0, trimmed, 0, w);
        return trimmed;
    }

    private static int put(float[] out, int w, float x, float y, float u, float v) {
        out[w    ] = x;
        out[w + 1] = y;
        out[w + 2] = u;
        out[w + 3] = v;
        return w + 4;
    }

    // ---- GL object lifecycle ----

    private static Slot createSlot(Kind kind) {
        Slot s = new Slot(kind);
        int stride = kind.floatsPerVertex * Float.BYTES;
        s.vao = Gl.glGenVertexArray();
        s.vbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(s.vao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, s.vbo);
        switch (kind) {
            case POINT -> {
                Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
                Gl.glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
                Gl.glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
                Gl.glEnableVertexAttribArray(0);
                Gl.glEnableVertexAttribArray(1);
                Gl.glEnableVertexAttribArray(2);
            }
            case FLAT -> {
                Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
                Gl.glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
                Gl.glEnableVertexAttribArray(0);
                Gl.glEnableVertexAttribArray(1);
            }
            case IMAGE -> {
                Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
                Gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
                Gl.glEnableVertexAttribArray(0);
                Gl.glEnableVertexAttribArray(1);
            }
            case TEXT -> {
                Gl.glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
                Gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
                Gl.glEnableVertexAttribArray(0);
                Gl.glEnableVertexAttribArray(1);
            }
            case VEXEL, VOLUME -> {
                Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
                Gl.glEnableVertexAttribArray(0);
            }
        }
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
        return s;
    }

    private static void deleteSlot(Slot s) {
        if (s.vbo != 0) { Gl.glDeleteBuffer(s.vbo); s.vbo = 0; }
        if (s.vao != 0) { Gl.glDeleteVertexArray(s.vao); s.vao = 0; }
        if (s.texture != null) { s.texture.close(); s.texture = null; }
        if (s.volumeTexture != null) { s.volumeTexture.close(); s.volumeTexture = null; }
    }

    private static void deleteEntry(Entry e) {
        for (Slot s : e.slots.values()) deleteSlot(s);
        e.slots.clear();
        for (ArrayDeque<Slot> pool : e.freePools.values()) {
            while (!pool.isEmpty()) deleteSlot(pool.pop());
        }
    }

    /** Main-thread shutdown: release everything still tracked. */
    void close() {
        drainPendingDeletes();
        for (Entry e : entries.values()) deleteEntry(e);
        entries.clear();
    }
}
