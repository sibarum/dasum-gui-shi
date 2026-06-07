package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.Invalidator;

/**
 * Mutable scroll offset for one ScrollContainer instance. Layout fills in
 * the max bounds once per frame via {@link #updateBoundsPx}; the wheel
 * handler and scrollbar drag call {@link #scrollBy} / {@link #scrollByPx}
 * which clamp to those bounds and invalidate.
 * <p>
 * State is stored in pixels — that's the unit layout produces — but the
 * primary public API is em. Sibling {@code *Px} methods exist for
 * framework callers that already have pixels (the wheel handler, the
 * scrollbar-drag controller, {@code TextInputController}'s
 * scroll-into-view, and {@code Layout} itself).
 */
public final class ScrollPosition {

    // Pixel state (private — apps use the em / px accessors below).
    private float xPx = 0f;
    private float yPx = 0f;
    private float maxXPx = 0f;
    private float maxYPx = 0f;

    // ---------- read (em primary, px sibling) ----------

    /** Current x scroll offset, in em. */
    public float emX()    { return xPx    / EmContext.pixelsPerEm(); }
    /** Current y scroll offset, in em. */
    public float emY()    { return yPx    / EmContext.pixelsPerEm(); }
    /** Maximum x scroll offset (content overflow on the x-axis), in em. */
    public float emMaxX() { return maxXPx / EmContext.pixelsPerEm(); }
    /** Maximum y scroll offset (content overflow on the y-axis), in em. */
    public float emMaxY() { return maxYPx / EmContext.pixelsPerEm(); }

    /** Current x scroll offset, in pixels. */
    public float pxX()    { return xPx; }
    /** Current y scroll offset, in pixels. */
    public float pxY()    { return yPx; }
    /** Maximum x scroll offset, in pixels. */
    public float pxMaxX() { return maxXPx; }
    /** Maximum y scroll offset, in pixels. */
    public float pxMaxY() { return maxYPx; }

    // ---------- mutate ----------

    /**
     * Scroll by an em-typed delta. Primary public API.
     * @return true iff the offset actually changed (see {@link #scrollByPx}).
     */
    public boolean scrollBy(float emDx, float emDy) {
        float pxPerEm = EmContext.pixelsPerEm();
        return scrollByPx(emDx * pxPerEm, emDy * pxPerEm);
    }

    /**
     * Scroll by a pixel-typed delta. Sibling for callers that already have
     * pixels.
     * @return true iff the offset actually changed — false when already
     *         clamped at the limit on every requested axis. The wheel
     *         handler uses this to bubble an unconsumed event to the next
     *         scroll container out.
     */
    public boolean scrollByPx(float pxDx, float pxDy) {
        float nx = clamp(xPx + pxDx, 0f, maxXPx);
        float ny = clamp(yPx + pxDy, 0f, maxYPx);
        if (nx == xPx && ny == yPx) return false;
        xPx = nx;
        yPx = ny;
        Invalidator.invalidate();
        return true;
    }

    /**
     * Framework-internal: set the maximum scroll bounds in pixels.
     * Called by {@code Layout} once per frame from the (pixel-typed)
     * layout output.
     */
    public void updateBoundsPx(float newMaxXPx, float newMaxYPx) {
        maxXPx = Math.max(0f, newMaxXPx);
        maxYPx = Math.max(0f, newMaxYPx);
        // Clamp current position to new bounds (e.g. content shrunk under us).
        xPx = clamp(xPx, 0f, maxXPx);
        yPx = clamp(yPx, 0f, maxYPx);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
