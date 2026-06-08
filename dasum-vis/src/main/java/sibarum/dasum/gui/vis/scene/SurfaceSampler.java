package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.vis.math.Vec3;

/**
 * Samples the zero-level surface of a {@link ScalarField} into a point
 * cloud — the CPU half of the "bake an SDF into splats" experiment, and a
 * first cut at surface-probe generation. Strategy: sample a regular grid
 * over the bounds, keep cells whose value is within a shell of the
 * surface, Newton-project each kept point onto the surface, and emit
 * position + normal.
 *
 * <p>Round-dot splats leave gaps between points unless dense and
 * over-sized — that coverage-vs-count tradeoff is exactly what this
 * experiment is meant to expose (and the reason real probes want oriented
 * disc surfels). One-time bake; not for per-frame use.
 */
public final class SurfaceSampler {

    public record Result(float[] positions, float[] normals, int count) {}

    private SurfaceSampler() {}

    /**
     * @param f      the field to sample
     * @param min    bounds min corner
     * @param max    bounds max corner
     * @param res    grid resolution per axis
     * @param jitter cell-fraction of pseudo-random offset [0,1] to break
     *               axis-aligned banding (deterministic from grid index)
     */
    public static Result sample(ScalarField f, Vec3 min, Vec3 max, int res, float jitter) {
        float cx = (max.x() - min.x()) / res;
        float cy = (max.y() - min.y()) / res;
        float cz = (max.z() - min.z()) / res;
        float cell = Math.max(cx, Math.max(cy, cz));
        float shell = cell * 1.5f;             // keep points near the surface
        float h = cell * 0.25f;                // normal-tap offset
        float eps = cell * 0.05f;              // post-projection acceptance

        float[] pos = new float[1024 * 3];
        float[] nrm = new float[1024 * 3];
        int count = 0;

        for (int ix = 0; ix < res; ix++) {
            for (int iy = 0; iy < res; iy++) {
                for (int iz = 0; iz < res; iz++) {
                    float jx = jitter * (hash(ix, iy, iz, 0) - 0.5f);
                    float jy = jitter * (hash(ix, iy, iz, 1) - 0.5f);
                    float jz = jitter * (hash(ix, iy, iz, 2) - 0.5f);
                    float x = min.x() + (ix + 0.5f + jx) * cx;
                    float y = min.y() + (iy + 0.5f + jy) * cy;
                    float z = min.z() + (iz + 0.5f + jz) * cz;

                    if (Math.abs(f.at(x, y, z)) > shell) continue; // not near surface

                    // Newton-project onto the surface along the gradient.
                    for (int s = 0; s < 4; s++) {
                        float d = f.at(x, y, z);
                        Vec3 n = CsgField.normal(f, x, y, z, h);
                        x -= d * n.x();
                        y -= d * n.y();
                        z -= d * n.z();
                    }
                    if (Math.abs(f.at(x, y, z)) > eps) continue; // didn't converge

                    if (count * 3 + 3 > pos.length) {
                        pos = grow(pos);
                        nrm = grow(nrm);
                    }
                    Vec3 n = CsgField.normal(f, x, y, z, h);
                    int o = count * 3;
                    pos[o] = x; pos[o + 1] = y; pos[o + 2] = z;
                    nrm[o] = n.x(); nrm[o + 1] = n.y(); nrm[o + 2] = n.z();
                    count++;
                }
            }
        }
        return new Result(trim(pos, count * 3), trim(nrm, count * 3), count);
    }

    private static float[] grow(float[] a) {
        float[] b = new float[a.length * 2];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    private static float[] trim(float[] a, int len) {
        if (a.length == len) return a;
        float[] b = new float[len];
        System.arraycopy(a, 0, b, 0, len);
        return b;
    }

    /** Cheap deterministic hash → [0,1), so the bake is reproducible. */
    private static float hash(int x, int y, int z, int c) {
        long m = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ (c * 2654435761L);
        m ^= (m >>> 13); m *= 0x5bd1e995L; m ^= (m >>> 15);
        return (m & 0xFFFFFFL) / (float) 0x1000000;
    }
}
