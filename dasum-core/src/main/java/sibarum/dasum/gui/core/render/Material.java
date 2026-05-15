package sibarum.dasum.gui.core.render;

/**
 * A bundle of GL state (program + uniforms + blend mode + texture bindings)
 * that the batcher groups draw commands by. For M2a there is one material:
 * {@link SolidFillMaterial}. Future variants will include SDF text and
 * textured quad materials.
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
