package sibarum.dasum.gui.vis.plot;

/**
 * A 2D grid of complex samples — the data source for a false-colour map.
 * Indexing is row-major with {@code y = 0} the <b>top</b> row, matching
 * {@code ImageLayer}'s pixel convention so {@link FieldRaster} can walk
 * rows straight through without flipping.
 *
 * <p>Supply a field either lazily from a function ({@link #of}) or from a
 * pre-computed interleaved buffer ({@link Array}). Implementations must be
 * pure and thread-safe; a worker may rasterize the field off the GLFW thread.
 */
public interface ComplexField2D {

    int width();
    int height();

    /** Write {@code [re, im]} of the sample at {@code (x, y)} into {@code out2}. */
    void sample(int x, int y, float[] out2);

    /** Lazy per-pixel sampler. */
    @FunctionalInterface
    interface Sampler {
        void sample(int x, int y, float[] out2);
    }

    /** A field computed on demand by {@code sampler}. */
    static ComplexField2D of(int width, int height, Sampler sampler) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width/height > 0");
        if (sampler == null) throw new IllegalArgumentException("sampler != null");
        return new ComplexField2D() {
            public int width()  { return width; }
            public int height() { return height; }
            public void sample(int x, int y, float[] out2) { sampler.sample(x, y, out2); }
        };
    }

    /**
     * A field backed by an interleaved {@code [re, im]} buffer, row-major,
     * top row first ({@code reIm.length == width * height * 2}).
     */
    record Array(int width, int height, float[] reIm) implements ComplexField2D {
        public Array {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("width/height > 0");
            if (reIm == null || reIm.length != width * height * 2) {
                throw new IllegalArgumentException("reIm length must be width * height * 2");
            }
        }

        public void sample(int x, int y, float[] out2) {
            int i = (y * width + x) * 2;
            out2[0] = reIm[i];
            out2[1] = reIm[i + 1];
        }

        /** Materialize a lazy {@code sampler} into a backing buffer. */
        public static Array from(int width, int height, Sampler sampler) {
            float[] reIm = new float[width * height * 2];
            float[] tmp = new float[2];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    sampler.sample(x, y, tmp);
                    int i = (y * width + x) * 2;
                    reIm[i] = tmp[0];
                    reIm[i + 1] = tmp[1];
                }
            }
            return new Array(width, height, reIm);
        }
    }
}
