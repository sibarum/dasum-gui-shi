package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ONE_MINUS_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SRC_ALPHA;

/**
 * Top-level render coordinator. Holds one per-material accumulator (each
 * with its own VAO/VBO + vertex layout). {@link #submit(DrawCommand)}
 * dispatches by command variant; {@link #endFrame(float[])} flushes each
 * accumulator in render order — opaque solid fills first, then translucent
 * SDF text.
 * <p>
 * For M2c there are exactly two accumulators. Adding a new material means
 * adding a new accumulator + a new {@link DrawCommand} variant + a switch
 * arm here.
 */
public final class Batcher implements AutoCloseable {

    private final SolidFillAccumulator solidFill = new SolidFillAccumulator();
    private final MsdfTextAccumulator msdfText  = new MsdfTextAccumulator();
    private final ScissorStack scissor          = new ScissorStack();

    public void init() {
        solidFill.init();
        msdfText.init();
        Gl.glEnable(GL_BLEND);
        Gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Bind a new MSDF atlas for subsequent {@link DrawCommand.GlyphQuad}
     * submissions. If the atlas (or its distance range) is changing and
     * the text accumulator has pending glyphs from the previous atlas,
     * flush them first so they're drawn with the correct texture.
     * <p>
     * Solid-fill geometry is also flushed in that case to preserve
     * painter's-algorithm z-order between text and the solid quads that
     * were emitted alongside it. Without this, a text run from atlas A
     * would float above a later atlas-B background drawn before atlas-A
     * glyphs were committed.
     */
    public void setTextAtlas(Texture atlas, float distanceRange) {
        if (msdfText.willAtlasChange(atlas, distanceRange) && msdfText.hasPendingGeometry()) {
            // Need projection to flush — but setTextAtlas doesn't take it.
            // The caller (Render) supplies it via setTextAtlas(atlas, range, projection)
            // overload; this no-projection overload assumes the caller has
            // either drained pending text already or doesn't need cross-
            // atlas ordering this frame.
            throw new IllegalStateException(
                "setTextAtlas would drop " + msdfText + " pending glyphs of the previous atlas. " +
                "Use setTextAtlas(atlas, distanceRange, projection) when mixing atlases in a frame.");
        }
        msdfText.setAtlas(atlas, distanceRange);
    }

    /**
     * Atlas-swap variant that flushes pending text + solid-fill geometry
     * with {@code projection} before swapping. Use this when a frame can
     * mix multiple font groups (e.g. text label next to an icon).
     */
    public void setTextAtlas(Texture atlas, float distanceRange, float[] projection) {
        if (msdfText.willAtlasChange(atlas, distanceRange) && msdfText.hasPendingGeometry()) {
            // Flush solids first so painter's order is preserved across
            // the atlas swap, then the text accumulator finalizes its
            // own pending vertices against the OUTGOING atlas.
            solidFill.flush(projection);
            msdfText.flush(projection);
        }
        msdfText.setAtlas(atlas, distanceRange);
    }

    public ScissorStack scissor() { return scissor; }

    public void beginFrame(int framebufferHeightPx) {
        solidFill.beginFrame();
        msdfText.beginFrame();
        scissor.beginFrame(framebufferHeightPx);
    }

    public void submit(DrawCommand cmd) {
        switch (cmd) {
            case DrawCommand.ColoredTriangle t -> solidFill.submit(t);
            case DrawCommand.ColoredQuad q     -> solidFill.submit(q);
            case DrawCommand.GlyphQuad q       -> msdfText.submit(q);
        }
    }

    public void endFrame(float[] projection) {
        flush(projection);
    }

    /**
     * Force-flushes all accumulators. Call before changing global GL state
     * (scissor, viewport, blend) mid-frame so already-buffered geometry isn't
     * drawn under the new state. Stat counters keep accumulating across
     * multiple flushes within a single frame.
     */
    public void flush(float[] projection) {
        solidFill.flush(projection);
        msdfText.flush(projection);
    }

    public int drawCallsThisFrame() { return solidFill.drawCalls() + msdfText.drawCalls(); }
    public int verticesThisFrame()  { return solidFill.vertices()  + msdfText.vertices();  }

    @Override
    public void close() {
        msdfText.close();
        solidFill.close();
    }
}
