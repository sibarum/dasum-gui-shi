package sibarum.dasum.gui.core.overlay;

import sibarum.dasum.gui.core.em.Em;

/**
 * Positioning hint for an {@link OverlayStack.Overlay}. Two variants:
 * <ul>
 *   <li>{@link Center} — centered in the viewport. Use the
 *       {@link #CENTER} singleton.</li>
 *   <li>{@link At} — top-left of the overlay placed at the given em
 *       coordinates relative to the viewport's top-left. The overlay
 *       is clamped to stay within the viewport.</li>
 * </ul>
 * Em coordinates throughout — pixel conversion happens inside the layout
 * step in {@link OverlayStack#layoutInto}.
 */
public sealed interface Anchor permits Anchor.Center, Anchor.At {

    /** Singleton centered anchor. */
    Anchor CENTER = new Center();

    record Center() implements Anchor {}

    record At(Em emX, Em emY) implements Anchor {}

    /** Convenience factory for an em-positioned anchor. */
    static Anchor at(Em emX, Em emY) { return new At(emX, emY); }
}
