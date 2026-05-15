package sibarum.dasum.gui.core.layout;

import sibarum.dasum.gui.core.component.Component;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Output of one {@link Layout#compute} pass. Maps each component instance
 * (by identity, not value) to its computed screen-pixel rectangle. The
 * identity-based map preserves "two separate Box instances with identical
 * content are different components" — important once transient state
 * (hover, focus) attaches to identity in M5.
 */
public record LayoutResult(Map<Component, PixelRect> rects) {

    public LayoutResult {
        // Defensive: ensure caller passed an identity-comparing map.
        if (!(rects instanceof IdentityHashMap<?, ?>)) {
            // Wrap, don't fail — but layout currently always passes IdentityHashMap.
            IdentityHashMap<Component, PixelRect> copy = new IdentityHashMap<>();
            copy.putAll(rects);
            rects = copy;
        }
    }

    public PixelRect rectOf(Component c) { return rects.get(c); }
}
