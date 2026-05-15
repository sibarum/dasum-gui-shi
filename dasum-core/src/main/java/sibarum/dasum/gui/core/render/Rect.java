package sibarum.dasum.gui.core.render;

/**
 * Axis-aligned rectangle. Field semantics depend on the coordinate system in
 * use — for screen rects (Y-down) {@code top} is the smaller Y; for atlas
 * bounds in a yOrigin="bottom" image, {@code top} is the larger Y.
 */
public record Rect(float left, float bottom, float right, float top) {}
