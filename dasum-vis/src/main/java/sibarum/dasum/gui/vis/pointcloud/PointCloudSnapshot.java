package sibarum.dasum.gui.vis.pointcloud;

/**
 * Immutable point-cloud frame — what the renderer draws when a particular
 * {@code Component.PointCloud} is on screen. Snapshots are constructed by
 * the consumer (typically a worker thread building data from a neural
 * network's runtime state) and atomically published via
 * {@link PointCloudStates#publish(sibarum.dasum.gui.core.component.Component, PointCloudSnapshot)}.
 *
 * <p>A snapshot carries two independent layers:
 * <ul>
 *   <li><b>Points</b> — scatter data, drawn as round screen-space dots.</li>
 *   <li><b>Line segments</b> — straight 3D segments with per-endpoint
 *       colours, rasterized to a per-fragment gradient. Useful for
 *       coordinate axes, wireframe shapes, edges of a graph, vector
 *       fields, etc. Both layers share one viewport / camera; a snapshot
 *       can carry one, the other, or both.</li>
 * </ul>
 *
 * <p><b>Invariants:</b>
 * <ul>
 *   <li>{@code dimensionality >= 1}</li>
 *   <li>{@code pointCount * dimensionality == positions.length}</li>
 *   <li>If {@code colors != null}: {@code colors.length == pointCount * 3} (RGB linear, 0..1)</li>
 *   <li>If {@code labels != null}: {@code labels.length == pointCount}</li>
 *   <li>If {@code projection != null}: each entry is in [0, dimensionality);
 *       length 2 or 3 (selects the dims used as screen X / Y / [Z]).</li>
 *   <li>{@code segmentCount >= 0}</li>
 *   <li>If {@code segmentCount > 0}: {@code segmentEndpoints != null} and
 *       {@code segmentEndpoints.length == segmentCount * 2 * dimensionality}.
 *       Each segment occupies {@code 2 * dimensionality} consecutive floats —
 *       endpoint A's dims followed by endpoint B's dims.</li>
 *   <li>If {@code segmentColors != null}: length {@code == segmentCount * 2 * 3}
 *       (RGB per endpoint, linear, 0..1). {@code null} means "use default colour
 *       for every segment endpoint."</li>
 * </ul>
 *
 * <p><b>Thread-safety contract:</b> after a snapshot is passed to
 * {@code publish}, the calling thread MUST NOT mutate any of its backing
 * arrays. The renderer reads them on the GLFW main thread without locking.
 * The simplest way to honour this is to allocate fresh arrays per
 * snapshot; for higher-throughput cases a per-component double-buffer pool
 * would be a future optimization.
 *
 * @param dimensionality    length of each point's position vector (N)
 * @param pointCount        number of points
 * @param positions         row-major: point {@code i} dim {@code d} is at {@code positions[i*N + d]}
 * @param colors            optional per-point RGB; {@code null} means "use default colour"
 * @param labels            optional per-point label (for future hover tooltip); not rendered in MVP
 * @param projection        which dims map to screen X/Y/[Z]; {@code null} = first 2 (ortho) or 3 (perspective)
 * @param segmentCount      number of line segments (0 = no line layer)
 * @param segmentEndpoints  row-major: segment {@code i}'s endpoint A is at
 *                          {@code segmentEndpoints[i*2*N + d]} for dim {@code d},
 *                          endpoint B at {@code [i*2*N + N + d]}. {@code null}
 *                          allowed iff {@code segmentCount == 0}.
 * @param segmentColors     optional row-major: segment {@code i}'s endpoint-A RGB at
 *                          {@code [i*2*3 .. i*2*3 + 2]}, endpoint-B RGB at
 *                          {@code [i*2*3 + 3 .. i*2*3 + 5]}. {@code null} = default colour.
 */
public record PointCloudSnapshot(
    int dimensionality,
    int pointCount,
    float[] positions,
    float[] colors,
    String[] labels,
    int[] projection,
    int segmentCount,
    float[] segmentEndpoints,
    float[] segmentColors
) {

    public PointCloudSnapshot {
        if (dimensionality < 1)            throw new IllegalArgumentException("dimensionality >= 1");
        if (pointCount < 0)                throw new IllegalArgumentException("pointCount >= 0");
        if (positions == null)             throw new IllegalArgumentException("positions != null");
        if (positions.length != (long) dimensionality * pointCount) {
            throw new IllegalArgumentException(
                "positions length " + positions.length
                + " != dimensionality * pointCount (" + dimensionality + " * " + pointCount + ")");
        }
        if (colors != null && colors.length != 3 * pointCount) {
            throw new IllegalArgumentException("colors length must be 3 * pointCount or null");
        }
        if (labels != null && labels.length != pointCount) {
            throw new IllegalArgumentException("labels length must be pointCount or null");
        }
        if (projection != null) {
            if (projection.length != 2 && projection.length != 3) {
                throw new IllegalArgumentException("projection length must be 2 or 3");
            }
            for (int d : projection) {
                if (d < 0 || d >= dimensionality) {
                    throw new IllegalArgumentException("projection dim out of range: " + d);
                }
            }
        }
        if (segmentCount < 0) throw new IllegalArgumentException("segmentCount >= 0");
        if (segmentCount > 0) {
            if (segmentEndpoints == null) {
                throw new IllegalArgumentException("segmentEndpoints != null when segmentCount > 0");
            }
            long expected = (long) segmentCount * 2L * (long) dimensionality;
            if (segmentEndpoints.length != expected) {
                throw new IllegalArgumentException(
                    "segmentEndpoints length " + segmentEndpoints.length
                    + " != segmentCount * 2 * dimensionality (" + segmentCount + " * 2 * " + dimensionality + ")");
            }
        }
        if (segmentColors != null && segmentColors.length != segmentCount * 2 * 3) {
            throw new IllegalArgumentException("segmentColors length must be segmentCount * 2 * 3 or null");
        }
    }

    /**
     * Backward-compat constructor — no line segments. Pre-line-segment
     * callers keep working unchanged.
     */
    public PointCloudSnapshot(int dimensionality, int pointCount,
                              float[] positions, float[] colors,
                              String[] labels, int[] projection) {
        this(dimensionality, pointCount, positions, colors, labels, projection,
             0, null, null);
    }

    /** Convenience for the common 3D case with the first 3 dims projected. */
    public static PointCloudSnapshot of3D(float[] positions) {
        return new PointCloudSnapshot(3, positions.length / 3, positions, null, null, null);
    }

    /** Convenience for 2D scatter. */
    public static PointCloudSnapshot of2D(float[] positions) {
        return new PointCloudSnapshot(2, positions.length / 2, positions, null, null, null);
    }

    /**
     * Return a copy of this snapshot with the given line segments attached.
     * Useful when constructing the points and lines in separate code paths
     * (e.g. point data from a worker, axis overlay from app code).
     * Dimensionality must match the snapshot's; the constructor validates.
     */
    public PointCloudSnapshot withSegments(int segmentCount, float[] segmentEndpoints, float[] segmentColors) {
        return new PointCloudSnapshot(
            dimensionality, pointCount, positions, colors, labels, projection,
            segmentCount, segmentEndpoints, segmentColors
        );
    }
}
