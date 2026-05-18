package sibarum.dasum.gui.vis.pointcloud;

/**
 * Immutable point-cloud frame — what the renderer draws when a particular
 * {@code Component.PointCloud} is on screen. Snapshots are constructed by
 * the consumer (typically a worker thread building data from a neural
 * network's runtime state) and atomically published via
 * {@link PointCloudStates#publish(sibarum.dasum.gui.core.component.Component, PointCloudSnapshot)}.
 *
 * <p><b>Invariants:</b>
 * <ul>
 *   <li>{@code dimensionality >= 1}</li>
 *   <li>{@code pointCount * dimensionality == positions.length}</li>
 *   <li>If {@code colors != null}: {@code colors.length == pointCount * 3} (RGB linear, 0..1)</li>
 *   <li>If {@code labels != null}: {@code labels.length == pointCount}</li>
 *   <li>If {@code projection != null}: each entry is in [0, dimensionality);
 *       length 2 or 3 (selects the dims used as screen X / Y / [Z]).</li>
 * </ul>
 *
 * <p><b>Thread-safety contract:</b> after a snapshot is passed to
 * {@code publish}, the calling thread MUST NOT mutate any of its backing
 * arrays. The renderer reads them on the GLFW main thread without locking.
 * The simplest way to honour this is to allocate fresh arrays per
 * snapshot; for higher-throughput cases a per-component double-buffer pool
 * would be a future optimization.
 *
 * @param dimensionality length of each point's position vector (N)
 * @param pointCount     number of points
 * @param positions      row-major: point {@code i} dim {@code d} is at {@code positions[i*N + d]}
 * @param colors         optional per-point RGB; {@code null} means "use default colour"
 * @param labels         optional per-point label (for future hover tooltip); not rendered in MVP
 * @param projection     which dims map to screen X/Y/[Z]; {@code null} = first 2 (ortho) or 3 (perspective)
 */
public record PointCloudSnapshot(
    int dimensionality,
    int pointCount,
    float[] positions,
    float[] colors,
    String[] labels,
    int[] projection
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
    }

    /** Convenience for the common 3D case with the first 3 dims projected. */
    public static PointCloudSnapshot of3D(float[] positions) {
        return new PointCloudSnapshot(3, positions.length / 3, positions, null, null, null);
    }

    /** Convenience for 2D scatter. */
    public static PointCloudSnapshot of2D(float[] positions) {
        return new PointCloudSnapshot(2, positions.length / 2, positions, null, null, null);
    }
}
