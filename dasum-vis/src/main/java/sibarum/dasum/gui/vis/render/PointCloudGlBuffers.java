package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.vis.pointcloud.PointCloudSnapshot;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static sibarum.dasum.gui.natives.gl.Gl.GL_ARRAY_BUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DYNAMIC_DRAW;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;

/**
 * Per-component GPU vertex-buffer cache for point-cloud + line-segment
 * rendering. Lives on the GLFW main thread; the only cross-thread
 * interaction is the pending-delete queue, which is drained on the main
 * thread at the start of each render pass.
 * <p>
 * The renderer compares each component's current snapshot reference
 * against the last one it uploaded; if they match, the existing VBOs are
 * reused without re-upload. This is what makes a worker republishing at
 * thousands of Hz cheap: the main thread only ever does one upload per
 * displayed frame regardless of publish rate. The point-layer and
 * line-layer buffers share the same snapshot identity for upload-skip
 * tracking — a new snapshot triggers re-upload of both layers, but
 * neither layer pays an upload cost when republishing the same snapshot.
 */
final class PointCloudGlBuffers {

    /** Stride: vec3 worldPos + vec3 color = 6 floats per vertex. */
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int VERTEX_BYTES = FLOATS_PER_VERTEX * Float.BYTES;

    static final class Entry {
        // Point layer.
        int pointVao = 0;
        int pointVbo = 0;
        int pointCapacityVertices = 0;
        int uploadedPointCount = 0;

        // Line layer. Each segment contributes 2 vertices.
        int lineVao = 0;
        int lineVbo = 0;
        int lineCapacityVertices = 0;
        int uploadedLineVertexCount = 0;

        /** Identity of the last snapshot uploaded — equality skips re-upload of both layers. */
        PointCloudSnapshot lastUploadedSnapshot = null;
    }

    private final Map<Component, Entry> entries = new IdentityHashMap<>();
    private final Queue<Component> pendingDelete = new ConcurrentLinkedQueue<>();

    /**
     * Schedule a component's GL resources for deletion at the next
     * render pass. Safe to call from any thread (the underlying queue
     * is concurrent). Used by the cleaner registered with
     * {@code Components.registerCleaner}.
     */
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
     * Ensure GPU buffers exist for {@code c} and are in sync with
     * {@code snapshot}. Returns the {@link Entry} carrying both layers'
     * VAOs + vertex counts, or {@code null} if the snapshot is entirely
     * empty (no points AND no segments). Main-thread only.
     */
    Entry ensure(Component c, PointCloudSnapshot snapshot) {
        if (snapshot == null) return null;
        if (snapshot.pointCount() == 0 && snapshot.segmentCount() == 0) return null;

        Entry e = entries.computeIfAbsent(c, k -> createEntry());

        // Skip re-upload if the snapshot reference is what we already uploaded.
        if (e.lastUploadedSnapshot == snapshot) return e;

        // ---- point layer ----
        if (snapshot.pointCount() > 0) {
            float[] verts = buildPointVertexBuffer(snapshot);
            int vertexCount = snapshot.pointCount();
            Gl.glBindBuffer(GL_ARRAY_BUFFER, e.pointVbo);
            if (vertexCount > e.pointCapacityVertices) {
                Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) vertexCount * VERTEX_BYTES, GL_DYNAMIC_DRAW);
                e.pointCapacityVertices = vertexCount;
            }
            Gl.glBufferSubData(GL_ARRAY_BUFFER, 0L, verts);
            Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
            e.uploadedPointCount = vertexCount;
        } else {
            e.uploadedPointCount = 0;
        }

        // ---- line layer ----
        if (snapshot.segmentCount() > 0) {
            float[] lineVerts = buildLineVertexBuffer(snapshot);
            int vertexCount = snapshot.segmentCount() * 2; // 2 vertices per segment
            Gl.glBindBuffer(GL_ARRAY_BUFFER, e.lineVbo);
            if (vertexCount > e.lineCapacityVertices) {
                Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) vertexCount * VERTEX_BYTES, GL_DYNAMIC_DRAW);
                e.lineCapacityVertices = vertexCount;
            }
            Gl.glBufferSubData(GL_ARRAY_BUFFER, 0L, lineVerts);
            Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
            e.uploadedLineVertexCount = vertexCount;
        } else {
            e.uploadedLineVertexCount = 0;
        }

        e.lastUploadedSnapshot = snapshot;
        return e;
    }

    private static float[] buildPointVertexBuffer(PointCloudSnapshot snap) {
        int n = snap.pointCount();
        int dim = snap.dimensionality();
        int[] proj = snap.projection();
        int dx = proj != null ? proj[0] : 0;
        int dy = proj != null ? proj[1] : (dim >= 2 ? 1 : 0);
        int dz;
        if (proj != null && proj.length == 3) {
            dz = proj[2];
        } else if (proj == null && dim >= 3) {
            dz = 2;
        } else {
            dz = -1;
        }

        float[] positions = snap.positions();
        float[] colors = snap.colors();
        float[] out = new float[n * FLOATS_PER_VERTEX];
        for (int i = 0; i < n; i++) {
            int base = i * dim;
            int off  = i * FLOATS_PER_VERTEX;
            out[off    ] = positions[base + dx];
            out[off + 1] = positions[base + dy];
            out[off + 2] = dz >= 0 ? positions[base + dz] : 0f;
            if (colors != null) {
                out[off + 3] = colors[i * 3    ];
                out[off + 4] = colors[i * 3 + 1];
                out[off + 5] = colors[i * 3 + 2];
            } else {
                out[off + 3] = 0.85f;
                out[off + 4] = 0.90f;
                out[off + 5] = 1.00f;
            }
        }
        return out;
    }

    /**
     * Build a per-vertex (pos + colour) buffer for line segments. Each
     * segment produces TWO vertices in the output (endpoint A, then B).
     * Uses the same dim-projection rules as points so a 2D snapshot's
     * segments render at z=0 and a 4D snapshot projects through the
     * snapshot's {@code projection} array consistently.
     */
    private static float[] buildLineVertexBuffer(PointCloudSnapshot snap) {
        int segCount = snap.segmentCount();
        int dim = snap.dimensionality();
        int[] proj = snap.projection();
        int dx = proj != null ? proj[0] : 0;
        int dy = proj != null ? proj[1] : (dim >= 2 ? 1 : 0);
        int dz;
        if (proj != null && proj.length == 3)      dz = proj[2];
        else if (proj == null && dim >= 3)          dz = 2;
        else                                        dz = -1;

        float[] eps = snap.segmentEndpoints();
        float[] cols = snap.segmentColors();
        int outVerts = segCount * 2;
        float[] out = new float[outVerts * FLOATS_PER_VERTEX];

        // Each segment occupies 2*dim consecutive floats in eps.
        // Endpoint A at offset [seg*2*dim + 0..dim-1], endpoint B at [seg*2*dim + dim..2*dim-1].
        for (int s = 0; s < segCount; s++) {
            int segBase = s * 2 * dim;
            for (int endpoint = 0; endpoint < 2; endpoint++) {
                int srcBase = segBase + endpoint * dim;
                int vertIdx = s * 2 + endpoint;
                int off = vertIdx * FLOATS_PER_VERTEX;
                out[off    ] = eps[srcBase + dx];
                out[off + 1] = eps[srcBase + dy];
                out[off + 2] = dz >= 0 ? eps[srcBase + dz] : 0f;
                if (cols != null) {
                    int cBase = vertIdx * 3;
                    out[off + 3] = cols[cBase    ];
                    out[off + 4] = cols[cBase + 1];
                    out[off + 5] = cols[cBase + 2];
                } else {
                    out[off + 3] = 0.85f;
                    out[off + 4] = 0.90f;
                    out[off + 5] = 1.00f;
                }
            }
        }
        return out;
    }

    private static Entry createEntry() {
        Entry e = new Entry();
        // Point VAO / VBO.
        e.pointVao = Gl.glGenVertexArray();
        e.pointVbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(e.pointVao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, e.pointVbo);
        Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_BYTES, 0L);
        Gl.glEnableVertexAttribArray(0);
        Gl.glVertexAttribPointer(1, 3, GL_FLOAT, false, VERTEX_BYTES, 3L * Float.BYTES);
        Gl.glEnableVertexAttribArray(1);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);

        // Line VAO / VBO. Same attribute layout as points so the same vertex
        // shader works — we just bind a different program (LineSegmentMaterial)
        // before drawing GL_LINES, so the point's round-dot frag-discard
        // doesn't apply.
        e.lineVao = Gl.glGenVertexArray();
        e.lineVbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(e.lineVao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, e.lineVbo);
        Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_BYTES, 0L);
        Gl.glEnableVertexAttribArray(0);
        Gl.glVertexAttribPointer(1, 3, GL_FLOAT, false, VERTEX_BYTES, 3L * Float.BYTES);
        Gl.glEnableVertexAttribArray(1);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
        return e;
    }

    private static void deleteEntry(Entry e) {
        if (e.pointVbo != 0) { Gl.glDeleteBuffer(e.pointVbo); e.pointVbo = 0; }
        if (e.pointVao != 0) { Gl.glDeleteVertexArray(e.pointVao); e.pointVao = 0; }
        if (e.lineVbo != 0)  { Gl.glDeleteBuffer(e.lineVbo);  e.lineVbo  = 0; }
        if (e.lineVao != 0)  { Gl.glDeleteVertexArray(e.lineVao);  e.lineVao  = 0; }
    }
}
