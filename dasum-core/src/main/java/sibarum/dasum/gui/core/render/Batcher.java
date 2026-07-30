package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ONE_MINUS_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SRC_ALPHA;

/**
 * Top-level render coordinator. Holds ONE geometry accumulator + material (the
 * {@link UnifiedAccumulator} / {@code unified.*} shader): every
 * {@link DrawCommand} — flat fill, rounded/bordered rect, MSDF glyph — is
 * appended to a single buffer in submission (painter's) order and drawn in one
 * stream.
 *
 * <p><b>Ordering is correct by construction.</b> There are no longer separate
 * flat / rounded / glyph buckets flushed in a fixed order, so the old
 * cross-bucket hazard — "every rounded fill draws on top of every flat fill",
 * which silently hid flat carets/selections behind rounded frames — cannot
 * happen. Whatever is submitted later draws later. A mid-frame {@link #flush}
 * (before a scissor/viewport change) or an atlas swap just draws the stream so
 * far and continues; order across those flushes is preserved because they are
 * sequential.
 */
public final class Batcher implements AutoCloseable {

    private final UnifiedAccumulator geometry = new UnifiedAccumulator();
    private final ScissorStack scissor        = new ScissorStack();

    public void init() {
        geometry.init();
        Gl.glEnable(GL_BLEND);
        Gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Bind a new MSDF atlas for subsequent {@link DrawCommand.GlyphQuad}s. This
     * no-projection variant refuses to silently drop pending geometry across a
     * real atlas change (it has no projection to flush with); callers mixing
     * atlases in a frame use {@link #setTextAtlas(Texture, float, float[])}.
     */
    public void setTextAtlas(Texture atlas, float distanceRange) {
        if (geometry.willAtlasChange(atlas, distanceRange) && geometry.hasPendingGeometry()) {
            throw new IllegalStateException(
                "setTextAtlas would drop pending geometry of the previous atlas. " +
                "Use setTextAtlas(atlas, distanceRange, projection) when mixing atlases in a frame.");
        }
        geometry.setAtlas(atlas, distanceRange);
    }

    /**
     * Atlas-swap variant that flushes the pending stream with {@code projection}
     * before swapping, so painter's order is preserved across the swap (the
     * geometry drawn so far — including flat/rounded fragments, which don't
     * sample — is committed under the outgoing atlas, then glyphs continue with
     * the new one).
     */
    public void setTextAtlas(Texture atlas, float distanceRange, float[] projection) {
        if (geometry.willAtlasChange(atlas, distanceRange) && geometry.hasPendingGeometry()) {
            geometry.flush(projection);
        }
        geometry.setAtlas(atlas, distanceRange);
    }

    public ScissorStack scissor() { return scissor; }

    public void beginFrame(int framebufferHeightPx) {
        geometry.beginFrame();
        scissor.beginFrame(framebufferHeightPx);
    }

    public void submit(DrawCommand cmd) {
        switch (cmd) {
            case DrawCommand.ColoredTriangle t -> geometry.submit(t);
            case DrawCommand.ColoredQuad q     -> geometry.submit(q);
            case DrawCommand.RoundedQuad q     -> geometry.submit(q);
            case DrawCommand.GlyphQuad q       -> geometry.submit(q);
        }
    }

    public void endFrame(float[] projection) {
        flush(projection);
    }

    /**
     * Force-flush the stream. Call before changing global GL state (scissor,
     * viewport, blend) mid-frame so already-buffered geometry is drawn under the
     * old state. Stat counters accumulate across multiple flushes within a frame.
     */
    public void flush(float[] projection) {
        geometry.flush(projection);
    }

    public int drawCallsThisFrame() { return geometry.drawCalls(); }
    public int verticesThisFrame()  { return geometry.vertices(); }

    @Override
    public void close() {
        geometry.close();
    }
}
