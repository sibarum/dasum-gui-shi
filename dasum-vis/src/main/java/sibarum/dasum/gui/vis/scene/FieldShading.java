package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.vis.math.Vec3;

/**
 * Bakes view-INDEPENDENT shading of a {@link ScalarField} surface into
 * per-point RGB — diffuse under a fixed light plus the same
 * direction-relative hemisphere AO the {@code vexelray.frag} CSG path
 * uses. This is exactly the surface-cache contract from the VexelRay
 * product vision: cache the view-independent terms (albedo · diffuse ·
 * AO), recompute view-dependent terms (specular, camera-anchored light)
 * live. Specular and soft shadows are deliberately omitted here — they're
 * the live terms — which is why a baked cloud orbits correctly.
 */
public final class FieldShading {

    private FieldShading() {}

    /**
     * @param positions xyz triples (surface points)
     * @param normals   xyz triples (unit normals), parallel to positions
     * @param count     number of points
     * @param base      surface base colour (linear rgb)
     * @param light     unit direction TOWARD the (fixed) key light
     * @param scale     field bounding-cube half-extent (AO probe scaling)
     * @return {@code count*3} linear RGB, one colour per point
     */
    public static float[] bakeColors(ScalarField f, float[] positions, float[] normals, int count,
                                     Vec3 base, Vec3 light, float scale) {
        float lx = light.x(), ly = light.y(), lz = light.z();
        float[] rgb = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int o = i * 3;
            float px = positions[o], py = positions[o + 1], pz = positions[o + 2];
            float nx = normals[o],  ny = normals[o + 1],  nz = normals[o + 2];

            float diff = Math.max(0f, nx * lx + ny * ly + nz * lz);
            float ao = hemisphereAO(f, px, py, pz, nx, ny, nz, scale);
            float lit = (0.30f + 1.05f * diff) * ao;   // matches the shader's combine
            rgb[o]     = Math.min(1f, base.x() * lit);
            rgb[o + 1] = Math.min(1f, base.y() * lit);
            rgb[o + 2] = Math.min(1f, base.z() * lit);
        }
        return rgb;
    }

    /** CPU port of vexelray.frag's hemisphereAO (direction-relative baseline). */
    private static float hemisphereAO(ScalarField f, float px, float py, float pz,
                                      float nx, float ny, float nz, float scale) {
        // Tangent frame around the normal.
        float ax, ay, az;
        if (Math.abs(ny) < 0.99f) { ax = 0f; ay = 1f; az = 0f; } else { ax = 1f; ay = 0f; az = 0f; }
        float tx = ny * az - nz * ay, ty = nz * ax - nx * az, tz = nx * ay - ny * ax;
        float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        tx /= tl; ty /= tl; tz /= tl;
        float bx = ny * tz - nz * ty, by = nz * tx - nx * tz, bz = nx * ty - ny * tx;

        float r = 0.07f * scale;
        final float CT = 0.573f, ST = 0.819f;
        float occ = 0f;
        for (int i = 0; i < 6; i++) {
            float a = 6.2831853f * i / 6f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float dx = nx * CT + (tx * ca + bx * sa) * ST;
            float dy = ny * CT + (ty * ca + by * sa) * ST;
            float dz = nz * CT + (tz * ca + bz * sa) * ST;
            float e = r * CT;
            float d = f.at(px + dx * r, py + dy * r, pz + dz * r);
            occ += clamp((e - d) / r, 0f, 1f);
        }
        float dn = f.at(px + nx * r, py + ny * r, pz + nz * r);
        occ += clamp((r - dn) / r, 0f, 1f);
        return clamp(1f - 1.4f * occ / 7f, 0.05f, 1f);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
