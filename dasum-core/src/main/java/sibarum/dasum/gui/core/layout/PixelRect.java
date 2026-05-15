package sibarum.dasum.gui.core.layout;

/**
 * Axis-aligned rectangle in screen pixels, Y-down origin at top-left.
 * Distinct from {@link sibarum.dasum.gui.core.render.Rect} (which uses a
 * Y-up convention for atlas UVs) — separate type avoids cross-coordinate
 * confusion at API boundaries.
 */
public record PixelRect(float x, float y, float width, float height) {

    public float right()  { return x + width; }
    public float bottom() { return y + height; }

    public boolean contains(float px, float py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
}
