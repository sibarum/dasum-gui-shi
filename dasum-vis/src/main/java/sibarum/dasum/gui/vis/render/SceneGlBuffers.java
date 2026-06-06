package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.PointLayer;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.TriangleLayer;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DYNAMIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;

/**
 * Per-component GPU vertex-buffer cache for scene layers. Lives on the
 * GLFW main thread; the only cross-thread interaction is the
 * pending-delete queue, drained at the start of each render pass.
 *
 * <p><b>Layer-granular identity skip:</b> slots are keyed by layer
 * <em>reference identity</em> within each component. Publishing a new
 * scene re-uploads only the layers whose references changed; untouched
 * layer instances keep their VBOs. A worker republishing at high
 * frequency therefore pays one upload per <em>changed layer</em> per
 * displayed frame, not per publish.
 *
 * <p><b>Slot pooling:</b> slots evicted by a publish (their layer is no
 * longer in the scene) park in a per-component free pool keyed by vertex
 * layout, and the next new layer of the same kind reuses the VAO/VBO and
 * its grown capacity. This preserves the original buffers' steady-state
 * behavior for the publish-fresh-layer-every-frame worker pattern: no
 * per-frame GL object churn.
 *
 * <p>Vertex layouts: point layers are pos3+color3+size1 (stride 28,
 * size at attribute location 2); line/triangle layers are pos3+color3
 * (stride 24). Null layer colors fill with the framework default
 * (0.85, 0.90, 1.00).
 */
final class SceneGlBuffers {

    private static final float DEF_R = 0.85f, DEF_G = 0.90f, DEF_B = 1.00f;
    private static final int POINT_FLOATS = 7;
    private static final int FLAT_FLOATS  = 6;

    static final class Slot {
        final boolean pointLayout;
        int vao = 0;
        int vbo = 0;
        int capacityVertices = 0;
        int vertexCount = 0;

        Slot(boolean pointLayout) { this.pointLayout = pointLayout; }
    }

    static final class Entry {
        SceneSnapshot lastScene = null;
        Map<Layer, Slot> slots = new IdentityHashMap<>();
        final ArrayDeque<Slot> freePoint = new ArrayDeque<>();
        final ArrayDeque<Slot> freeFlat  = new ArrayDeque<>();

        Slot slot(Layer l) { return slots.get(l); }
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
     * Ensure GPU buffers for {@code c} are in sync with {@code scene}.
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
            (s.pointLayout ? e.freePoint : e.freeFlat).push(s);
        }
        e.slots.clear();

        // Upload layers that are new this scene.
        for (Layer l : scene.layers()) {
            if (next.containsKey(l)) continue;
            boolean point = l instanceof PointLayer;
            ArrayDeque<Slot> pool = point ? e.freePoint : e.freeFlat;
            Slot s = pool.isEmpty() ? createSlot(point) : pool.pop();
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
        };
        int floatsPerVertex = s.pointLayout ? POINT_FLOATS : FLAT_FLOATS;
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

    private static float[] buildPointVertices(PointLayer p) {
        int n = p.pointCount();
        float[] pos = p.positions();
        float[] col = p.colors();
        float[] sizes = p.sizes();
        float defSize = p.defaultSizePx();
        float[] out = new float[n * POINT_FLOATS];
        for (int i = 0; i < n; i++) {
            int off = i * POINT_FLOATS;
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
        float[] out = new float[n * FLAT_FLOATS];
        for (int i = 0; i < n; i++) {
            int off = i * FLAT_FLOATS;
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

    // ---- GL object lifecycle ----

    private static Slot createSlot(boolean pointLayout) {
        Slot s = new Slot(pointLayout);
        int stride = (pointLayout ? POINT_FLOATS : FLAT_FLOATS) * Float.BYTES;
        s.vao = Gl.glGenVertexArray();
        s.vbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(s.vao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, s.vbo);
        Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        Gl.glEnableVertexAttribArray(0);
        Gl.glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        Gl.glEnableVertexAttribArray(1);
        if (pointLayout) {
            Gl.glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
            Gl.glEnableVertexAttribArray(2);
        }
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
        return s;
    }

    private static void deleteSlot(Slot s) {
        if (s.vbo != 0) { Gl.glDeleteBuffer(s.vbo); s.vbo = 0; }
        if (s.vao != 0) { Gl.glDeleteVertexArray(s.vao); s.vao = 0; }
    }

    private static void deleteEntry(Entry e) {
        for (Slot s : e.slots.values()) deleteSlot(s);
        e.slots.clear();
        while (!e.freePoint.isEmpty()) deleteSlot(e.freePoint.pop());
        while (!e.freeFlat.isEmpty())  deleteSlot(e.freeFlat.pop());
    }

    /** Main-thread shutdown: release everything still tracked. */
    void close() {
        drainPendingDeletes();
        for (Entry e : entries.values()) deleteEntry(e);
        entries.clear();
    }
}
