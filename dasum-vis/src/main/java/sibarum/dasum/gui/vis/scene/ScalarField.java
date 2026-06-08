package sibarum.dasum.gui.vis.scene;

/**
 * A signed-distance (or any scalar) field sampled on the CPU:
 * {@code at(x, y, z)} returns the field value at a world point. The seed
 * of the CPU-side field evaluator the collision / surface-probe roadmap
 * needs — for now it powers surface sampling (baking an SDF into a splat
 * point cloud). Negative = inside, 0 = surface, positive = outside.
 */
@FunctionalInterface
public interface ScalarField {
    float at(float x, float y, float z);
}
