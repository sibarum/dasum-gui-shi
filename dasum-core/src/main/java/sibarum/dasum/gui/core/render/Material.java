package sibarum.dasum.gui.core.render;

/**
 * A bundle of GL state (program + uniforms + texture bindings). There is one
 * implementation, {@link UnifiedMaterial} — a single program that draws flat
 * fills, rounded/bordered rects, and MSDF glyphs — so the whole UI renders in
 * one order-preserving stream (see {@link Batcher}).
 */
public interface Material extends AutoCloseable {

    /** One-time GL resource setup; call after a GL context is current. */
    void init();

    /** Make this material the active one and push frame-uniform values. */
    void bind(float[] projection);

    /** Release GL resources. Idempotent. */
    @Override
    void close();
}
