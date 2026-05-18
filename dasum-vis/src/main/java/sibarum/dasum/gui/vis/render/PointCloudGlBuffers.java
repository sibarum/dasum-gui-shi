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
 * Per-component GPU vertex-buffer cache for point-cloud rendering. Lives
 * on the GLFW main thread; the only cross-thread interaction is the
 * pending-delete queue, which is drained on the main thread at the start
 * of each render pass.
 * <p>
 * The renderer compares each component's current snapshot reference
 * against the last one it uploaded; if they match, the existing VBO is
 * reused without re-upload. This is what makes a worker republishing at
 * thousands of Hz cheap: the main thread only ever does one upload per
 * displayed frame regardless of publish rate.
 */
final class PointCloudGlBuffers {

    /** Stride: vec3 worldPos + vec3 color = 6 floats per vertex. */
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int VERTEX_BYTES = FLOATS_PER_VERTEX * Float.BYTES;

    static final class Entry {
        int vao = 0;
        int vbo = 0;
        int capacityVertices = 0;
        int uploadedPointCount = 0;
        /** Identity of the last snapshot uploaded — equality skips re-upload. */
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
     * Ensure a GPU buffer exists for {@code c} and is in sync with
     * {@code snapshot}. Returns the {@link Entry} carrying VAO + the
     * vertex count to draw, or {@code null} if the snapshot is empty.
     * Main-thread only.
     */
    Entry ensure(Component c, PointCloudSnapshot snapshot) {
        if (snapshot == null || snapshot.pointCount() == 0) return null;

        Entry e = entries.computeIfAbsent(c, k -> createEntry());

        // Skip re-upload if the snapshot reference is what we already uploaded.
        if (e.lastUploadedSnapshot == snapshot) return e;

        float[] verts = buildVertexBuffer(snapshot);
        int vertexCount = snapshot.pointCount();

        Gl.glBindBuffer(GL_ARRAY_BUFFER, e.vbo);
        if (vertexCount > e.capacityVertices) {
            // Reallocate the GPU buffer (orphan the old store, allocate fresh).
            Gl.glBufferDataNull(GL_ARRAY_BUFFER, (long) vertexCount * VERTEX_BYTES, GL_DYNAMIC_DRAW);
            e.capacityVertices = vertexCount;
        }
        Gl.glBufferSubData(GL_ARRAY_BUFFER, 0L, verts);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);

        e.uploadedPointCount = vertexCount;
        e.lastUploadedSnapshot = snapshot;
        return e;
    }

    private static float[] buildVertexBuffer(PointCloudSnapshot snap) {
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

    private static Entry createEntry() {
        Entry e = new Entry();
        e.vao = Gl.glGenVertexArray();
        e.vbo = Gl.glGenBuffer();
        Gl.glBindVertexArray(e.vao);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, e.vbo);
        Gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_BYTES, 0L);
        Gl.glEnableVertexAttribArray(0);
        Gl.glVertexAttribPointer(1, 3, GL_FLOAT, false, VERTEX_BYTES, 3L * Float.BYTES);
        Gl.glEnableVertexAttribArray(1);
        Gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        Gl.glBindVertexArray(0);
        return e;
    }

    private static void deleteEntry(Entry e) {
        if (e.vbo != 0) { Gl.glDeleteBuffer(e.vbo); e.vbo = 0; }
        if (e.vao != 0) { Gl.glDeleteVertexArray(e.vao); e.vao = 0; }
    }
}
