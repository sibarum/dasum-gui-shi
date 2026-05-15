package sibarum.dasum.gui.core.text;

/**
 * Atlas-wide parameters reported in the msdf-atlas-gen JSON header.
 *
 * @param type           e.g. {@code "msdf"}
 * @param distanceRange  SDF distance range in pixels (used by the fragment shader)
 * @param emSize         font em size in atlas pixels (per-glyph render size)
 * @param width          atlas image width in pixels
 * @param height         atlas image height in pixels
 * @param yOriginBottom  {@code true} if atlas Y origin is at the bottom (default for msdf-atlas-gen)
 */
public record AtlasInfo(String type, float distanceRange, float emSize,
                        int width, int height, boolean yOriginBottom) {}
