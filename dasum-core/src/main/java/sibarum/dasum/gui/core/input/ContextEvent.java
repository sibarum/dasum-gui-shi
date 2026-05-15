package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * Payload passed to a {@link ContextMenuProvider} when a right-click resolves
 * to its owning component. All exposed coordinates are in em; the pixel
 * coordinates from the GLFW callback are converted once at the dispatcher
 * boundary in {@link ContextMenuController}.
 *
 * @param target       the component the provider was registered against —
 *                     the innermost match on the hit path
 * @param deepestHit   the deepest hit-tested component at the click position;
 *                     may be {@code target} itself or a descendant. A provider
 *                     attached to a parent (e.g. a Node) can branch on whether
 *                     {@code deepestHit} is a particular child (e.g. a Port)
 * @param hitPath      hit path from root to {@code deepestHit} (root → leaf)
 * @param cursorEmX    cursor x in em, relative to the window's top-left
 * @param cursorEmY    cursor y in em, relative to the window's top-left
 * @param modifiers    GLFW modifier bitfield at the moment of right-click
 * @param windowHandle handle to the active GLFW window — needed by providers
 *                     that perform clipboard operations
 */
public record ContextEvent(
    Component target,
    Component deepestHit,
    List<Component> hitPath,
    float cursorEmX,
    float cursorEmY,
    int modifiers,
    MemorySegment windowHandle
) {

    /**
     * Cursor x in em, relative to the top-left of {@code ancestor}. Returns
     * {@link Float#NaN} when no layout is available or {@code ancestor}
     * isn't in the laid-out tree.
     */
    public float localEmX(Component ancestor) {
        PixelRect r = ancestorRect(ancestor);
        if (r == null) return Float.NaN;
        float pxPerEm = EmContext.pixelsPerEm();
        if (pxPerEm <= 0f) return Float.NaN;
        return cursorEmX - r.x() / pxPerEm;
    }

    /** Cursor y in em, relative to the top-left of {@code ancestor}. */
    public float localEmY(Component ancestor) {
        PixelRect r = ancestorRect(ancestor);
        if (r == null) return Float.NaN;
        float pxPerEm = EmContext.pixelsPerEm();
        if (pxPerEm <= 0f) return Float.NaN;
        return cursorEmY - r.y() / pxPerEm;
    }

    private static PixelRect ancestorRect(Component ancestor) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return null;
        return lr.rectOf(ancestor);
    }
}
