package sibarum.dasum.gui.core.render;

/**
 * Render primitive emitted by components and consumed by the batcher.
 * Components never call GL directly — they emit {@code DrawCommand}s.
 * <p>
 * Variants in this sealed set determine which material accumulator inside
 * {@link Batcher} consumes the command. Adding a variant requires:
 * <ol>
 *   <li>extending the {@code permits} clause</li>
 *   <li>extending {@link Batcher#submit(DrawCommand)}'s switch</li>
 *   <li>typically a new material + accumulator inside dasum-core</li>
 * </ol>
 */
public sealed interface DrawCommand
    permits DrawCommand.ColoredTriangle, DrawCommand.ColoredQuad, DrawCommand.GlyphQuad {

    record ColoredTriangle(Vec2 a, Vec2 b, Vec2 c, Color cA, Color cB, Color cC) implements DrawCommand {}

    /**
     * Solid-color axis-aligned rectangle in screen pixels (Y-down). Two
     * triangles, four vertices duplicated to six for {@code glDrawArrays}
     * with the same per-vertex color across all six.
     */
    record ColoredQuad(float x, float y, float width, float height, Color color) implements DrawCommand {}

    /**
     * A textured quad aligned to screen axes. Used for MSDF glyphs (atlas-bound
     * texture) and, in time, for any rectangular textured primitive.
     *
     * @param x      screen-space top-left X in pixels (Y-down)
     * @param y      screen-space top-left Y in pixels (Y-down)
     * @param width  screen-space width in pixels (positive)
     * @param height screen-space height in pixels (positive)
     * @param uv     atlas UV rect with V-up convention; {@code uv.bottom() < uv.top()}
     * @param color  tint multiplied against the sampled SDF coverage
     */
    record GlyphQuad(float x, float y, float width, float height, Rect uv, Color color) implements DrawCommand {}
}
