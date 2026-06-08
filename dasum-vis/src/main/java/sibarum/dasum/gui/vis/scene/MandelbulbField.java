package sibarum.dasum.gui.vis.scene;

/**
 * CPU Mandelbulb field — a faithful port of {@code sdMandelbulb} in
 * {@code vexelray.frag}, implementing the dual-output
 * {@link ComplexityField} contract: the triplex distance estimate, and
 * the normalized escape-iteration count as the view-independent
 * complexity signal that drives surface-probe density.
 *
 * <p><b>Dual source of truth:</b> mirrors the shader by hand. Keep the
 * two in lockstep until the external shader-codegen layer generates both
 * from one description.
 */
public final class MandelbulbField implements ComplexityField {

    private final float power;
    private final int iters;

    public MandelbulbField(float power, int iters) {
        this.power = power;
        this.iters = Math.max(1, iters);
    }

    @Override
    public float at(float x, float y, float z) {
        float zx = x, zy = y, zz = z;
        float dr = 1f, r = 0f;
        for (int i = 0; i < iters; i++) {
            r = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
            if (r > 2f) break;
            float theta = (float) Math.acos(clamp(zz / Math.max(r, 1e-9f), -1f, 1f));
            float phi = (float) Math.atan2(zy, zx);
            dr = (float) Math.pow(r, power - 1f) * power * dr + 1f;
            float zr = (float) Math.pow(r, power);
            theta *= power;
            phi *= power;
            float st = (float) Math.sin(theta);
            zx = zr * st * (float) Math.cos(phi) + x;
            zy = zr * st * (float) Math.sin(phi) + y;
            zz = zr * (float) Math.cos(theta) + z;
        }
        return 0.5f * (float) Math.log(Math.max(r, 1e-9f)) * r / dr;
    }

    @Override
    public float complexityAt(float x, float y, float z) {
        float zx = x, zy = y, zz = z;
        float r = 0f;
        int esc = iters;
        for (int i = 0; i < iters; i++) {
            r = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
            if (r > 2f) { esc = i; break; }
            float theta = (float) Math.acos(clamp(zz / Math.max(r, 1e-9f), -1f, 1f));
            float phi = (float) Math.atan2(zy, zx);
            float zr = (float) Math.pow(r, power);
            theta *= power;
            phi *= power;
            float st = (float) Math.sin(theta);
            zx = zr * st * (float) Math.cos(phi) + x;
            zy = zr * st * (float) Math.sin(phi) + y;
            zz = zr * (float) Math.cos(theta) + z;
        }
        return (float) esc / iters;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
