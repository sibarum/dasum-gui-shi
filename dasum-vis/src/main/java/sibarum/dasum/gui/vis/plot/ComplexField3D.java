package sibarum.dasum.gui.vis.plot;

/**
 * A 3D volume of complex samples that yields 2D {@link ComplexField2D}
 * slices for false-colour rendering. A slider scrubs the slice index along
 * one {@link Axis3} while the other two span the slice image. Slices are
 * cheap views over this volume (no copy) — {@link FieldRaster} reads through
 * them on demand.
 *
 * <p>Indexing is x→width, y→height, z→depth, with the {@code y = 0} row at
 * the top of a slice (consistent with {@link ComplexField2D}).
 */
public interface ComplexField3D {

    int width();
    int height();
    int depth();

    /** Write {@code [re, im]} of the sample at {@code (x, y, z)} into {@code out2}. */
    void sample(int x, int y, int z, float[] out2);

    /** Which axis the slice index runs along (the axis held constant). */
    enum Axis3 {
        /** Constant X → slice spans (Z across, Y up); {@code count = width}. */
        X,
        /** Constant Y → slice spans (X across, Z up); {@code count = height}. */
        Y,
        /** Constant Z → slice spans (X across, Y up); {@code count = depth}. */
        Z
    }

    /** Number of slices available along {@code axis}. */
    default int sliceCount(Axis3 axis) {
        return switch (axis) {
            case X -> width();
            case Y -> height();
            case Z -> depth();
        };
    }

    /** A non-copying 2D view of the volume at {@code index} along {@code axis}. */
    default ComplexField2D slice(Axis3 axis, int index) {
        int max = sliceCount(axis) - 1;
        int idx = Math.max(0, Math.min(max, index));
        return switch (axis) {
            case Z -> ComplexField2D.of(width(), height(), (sx, sy, out) -> sample(sx, sy, idx, out));
            case Y -> ComplexField2D.of(width(), depth(),  (sx, sy, out) -> sample(sx, idx, sy, out));
            case X -> ComplexField2D.of(depth(), height(), (sx, sy, out) -> sample(idx, sy, sx, out));
        };
    }

    /** Lazy per-voxel sampler. */
    @FunctionalInterface
    interface Sampler {
        void sample(int x, int y, int z, float[] out2);
    }

    /** A volume computed on demand by {@code sampler}. */
    static ComplexField3D of(int width, int height, int depth, Sampler sampler) {
        if (width <= 0 || height <= 0 || depth <= 0) throw new IllegalArgumentException("dims > 0");
        if (sampler == null) throw new IllegalArgumentException("sampler != null");
        return new ComplexField3D() {
            public int width()  { return width; }
            public int height() { return height; }
            public int depth()  { return depth; }
            public void sample(int x, int y, int z, float[] out2) { sampler.sample(x, y, z, out2); }
        };
    }

    /**
     * A volume backed by an interleaved {@code [re, im]} buffer, ordered
     * z-major then row-major ({@code index = ((z*height + y)*width + x)*2}).
     */
    record Array(int width, int height, int depth, float[] reIm) implements ComplexField3D {
        public Array {
            if (width <= 0 || height <= 0 || depth <= 0) throw new IllegalArgumentException("dims > 0");
            long need = (long) width * height * depth * 2;
            if (reIm == null || reIm.length != need) {
                throw new IllegalArgumentException("reIm length must be width * height * depth * 2");
            }
        }

        public void sample(int x, int y, int z, float[] out2) {
            int i = ((z * height + y) * width + x) * 2;
            out2[0] = reIm[i];
            out2[1] = reIm[i + 1];
        }

        public static Array from(int width, int height, int depth, Sampler sampler) {
            float[] reIm = new float[width * height * depth * 2];
            float[] tmp = new float[2];
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        sampler.sample(x, y, z, tmp);
                        int i = ((z * height + y) * width + x) * 2;
                        reIm[i] = tmp[0];
                        reIm[i + 1] = tmp[1];
                    }
                }
            }
            return new Array(width, height, depth, reIm);
        }
    }
}
