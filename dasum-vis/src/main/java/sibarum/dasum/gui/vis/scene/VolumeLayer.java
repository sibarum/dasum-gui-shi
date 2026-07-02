package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.vis.math.Vec3;

/**
 * A volumetric layer: a scalar/vector field sampled on an {@code nx*ny*nz} grid and raymarched as
 * a 3D texture. Each voxel is RGBA (row-major, x fastest: {@code i = ((z*ny + y)*nx + x)*4}) —
 * the renderer uploads it to a {@code GL_RGBA32F} 3D texture and marches camera rays through the
 * box {@code [center - halfExtent, center + halfExtent]}, sampling with trilinear filtering so a
 * coarse grid reads as a continuous field.
 *
 * <p>The renderer's shader treats RGB as emitted colour and A as density, accumulating
 * {@code sum += rgb*a} along the ray (emissive) — so the field's structure glows, tinted by
 * whatever the producer encoded (e.g. gradient direction). {@code maxSteps} bounds the march.
 */
public record VolumeLayer(
    float[] rgba,
    int nx, int ny, int nz,
    Vec3 center,
    Vec3 halfExtent,
    int maxSteps,
    BlendMode blend,
    float opacity
) implements Layer {

    public VolumeLayer {
        if (rgba == null) throw new IllegalArgumentException("rgba != null");
        if (nx < 2 || ny < 2 || nz < 2) throw new IllegalArgumentException("grid dims >= 2");
        if ((long) nx * ny * nz * 4 != rgba.length) {
            throw new IllegalArgumentException("rgba length must be nx*ny*nz*4");
        }
        if (center == null || halfExtent == null) throw new IllegalArgumentException("center/halfExtent != null");
        if (maxSteps < 1) throw new IllegalArgumentException("maxSteps >= 1");
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /** Convenience: ADDITIVE emissive glow, full opacity, 96 march steps. */
    public VolumeLayer(float[] rgba, int nx, int ny, int nz, Vec3 center, Vec3 halfExtent) {
        this(rgba, nx, ny, nz, center, halfExtent, 96, BlendMode.ADDITIVE, 1f);
    }

    public VolumeLayer withOpacity(float o)  { return new VolumeLayer(rgba, nx, ny, nz, center, halfExtent, maxSteps, blend, o); }
    public VolumeLayer withBlend(BlendMode b){ return new VolumeLayer(rgba, nx, ny, nz, center, halfExtent, maxSteps, b, opacity); }
    public VolumeLayer withMaxSteps(int n)   { return new VolumeLayer(rgba, nx, ny, nz, center, halfExtent, n, blend, opacity); }
}
