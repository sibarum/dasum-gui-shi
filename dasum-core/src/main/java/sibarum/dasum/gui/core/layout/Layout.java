package sibarum.dasum.gui.core.layout;

import sibarum.dasum.gui.core.graph.GraphSurfacePositions;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.input.ScrollPosition;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.text.TextMetrics;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * First pass per dirty frame. Walks the component tree, converts em sizes
 * to pixels via {@link sibarum.dasum.gui.core.em.EmContext}, and produces
 * a {@link LayoutResult} mapping every component to its screen-pixel rect.
 * <p>
 * Box-with-children centers each child in the parent's interior (used for
 * the M4-style nested-container demos).
 * <p>
 * Flex containers run a single-line flex algorithm:
 * <ol>
 *   <li>Compute intrinsic main-axis size per child plus flex-grow weight.</li>
 *   <li>If there's leftover main-axis space and any grow weights, distribute
 *       that leftover proportionally — overriding {@code justify-content}.</li>
 *   <li>Otherwise use {@code justify-content} to position the rigid group.</li>
 *   <li>Position each child on the cross axis per {@code align-items},
 *       with {@code STRETCH} overriding the child's cross-axis intrinsic.</li>
 * </ol>
 * No wrap, no flex-shrink (sum-exceeds-available is clamped silently),
 * no flex-basis distinct from main-axis size. Those land if needed later.
 */
public final class Layout {

    private Layout() {}

    public static LayoutResult compute(Component root, PixelRect viewport) {
        Map<Component, PixelRect> rects = new IdentityHashMap<>();
        PixelRect rootRect = computeRootRect(root, viewport);
        layoutInto(root, rootRect, rects);
        return new LayoutResult(rects);
    }

    /**
     * Lay out {@code root} as if its outer rect is exactly {@code rect} —
     * used by the overlay layer to position popups at anchored locations
     * in the viewport.
     */
    public static LayoutResult computeWithin(Component root, PixelRect rect) {
        Map<Component, PixelRect> rects = new IdentityHashMap<>();
        layoutInto(root, rect, rects);
        return new LayoutResult(rects);
    }

    // ---------- Flex axis resolution ----------

    /**
     * Resolve the requested axis of a {@link Component.Flex} into pixels.
     * {@code null} returns {@code fillFallback}; {@link Em#AUTO} resolves
     * to {@link #fitAxisSize}; an explicit em converts via {@code toPixels}.
     */
    public static float resolveFlexAxis(Component.Flex f, boolean width, float fillFallback) {
        Em e = width ? f.width() : f.height();
        if (e == null) return fillFallback;
        if (e.isAuto()) return fitAxisSize(f, width);
        return e.toPixels();
    }

    /**
     * Fit-content size for a Flex on the requested axis. Sum of children's
     * intrinsic main extents along the flex's main axis; max of children's
     * intrinsic cross extents along its cross axis. Plus gaps (main only)
     * and padding (both axes).
     */
    public static float fitAxisSize(Component.Flex flex, boolean width) {
        boolean row = flex.direction() == Direction.ROW;
        boolean isMain = (width && row) || (!width && !row);
        return isMain ? fitContentMain(flex) : fitContentCross(flex);
    }

    private static float fitContentMain(Component.Flex flex) {
        boolean row = flex.direction() == Direction.ROW;
        float total = 0f;
        List<Component> kids = sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(flex);
        int n = kids.size();
        for (Component child : kids) {
            total += intrinsicMain(child, row);
        }
        if (n > 1) total += flex.gap().toPixels() * (n - 1);
        total += 2f * flex.padding().toPixels();
        return total;
    }

    private static float fitContentCross(Component.Flex flex) {
        boolean row = flex.direction() == Direction.ROW;
        float maxCross = 0f;
        for (Component child : sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(flex)) {
            float c = intrinsicCross(child, row);
            if (c > maxCross) maxCross = c;
        }
        return maxCross + 2f * flex.padding().toPixels();
    }

    /** Where does the root live within the viewport? */
    private static PixelRect computeRootRect(Component root, PixelRect viewport) {
        return switch (root) {
            case Component.Box box -> {
                float w = box.width().toPixels();
                float h = box.height().toPixels();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Flex flex -> {
                float w = resolveFlexAxis(flex, true,  viewport.width());
                float h = resolveFlexAxis(flex, false, viewport.height());
                yield new PixelRect(viewport.x(), viewport.y(), w, h);
            }
            case Component.Scroll s -> {
                float w = s.width()  != null ? s.width().toPixels()  : viewport.width();
                float h = s.height() != null ? s.height().toPixels() : viewport.height();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Text t -> {
                float w = TextMetrics.widthPixels(t, TextStates.contentOf(t));
                float h = TextMetrics.heightPixels(t, TextStates.contentOf(t));
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Checkbox cb -> {
                float s = cb.size().toPixels();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - s) * 0.5f,
                    viewport.y() + (viewport.height() - s) * 0.5f,
                    s, s
                );
            }
            case Component.Radio<?> r -> {
                float s = r.size().toPixels();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - s) * 0.5f,
                    viewport.y() + (viewport.height() - s) * 0.5f,
                    s, s
                );
            }
            case Component.Slider sl -> {
                float w = sl.horizontal() ? sl.length().toPixels()    : sl.thickness().toPixels();
                float h = sl.horizontal() ? sl.thickness().toPixels() : sl.length().toPixels();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Tabs t -> {
                float w = t.width()  != null ? t.width().toPixels()  : viewport.width();
                float h = t.height() != null ? t.height().toPixels() : viewport.height();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.GraphSurface cv -> {
                float w = cv.width()  != null ? cv.width().toPixels()  : viewport.width();
                float h = cv.height() != null ? cv.height().toPixels() : viewport.height();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.PointCloud pc -> {
                float w = pc.width()  != null ? pc.width().toPixels()  : viewport.width();
                float h = pc.height() != null ? pc.height().toPixels() : viewport.height();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.DataTable dt -> {
                float w = dt.width()  != null ? dt.width().toPixels()  : viewport.width();
                float h = dt.height() != null ? dt.height().toPixels() : viewport.height();
                yield new PixelRect(
                    viewport.x() + (viewport.width()  - w) * 0.5f,
                    viewport.y() + (viewport.height() - h) * 0.5f,
                    w, h
                );
            }
        };
    }

    private static void layoutInto(Component c, PixelRect rect, Map<Component, PixelRect> rects) {
        rects.put(c, rect);
        switch (c) {
            case Component.Box box      -> layoutBoxChildren(box, rect, rects);
            case Component.Flex flex    -> layoutFlex(flex, rect, rects);
            case Component.Scroll s     -> layoutScroll(s, rect, rects);
            case Component.Text t       -> { /* leaf — no children to lay out */ }
            case Component.Checkbox cb  -> { /* leaf */ }
            case Component.Radio<?> r   -> { /* leaf */ }
            case Component.Slider sl    -> { /* leaf */ }
            case Component.Tabs tabs    -> layoutTabs(tabs, rect, rects);
            case Component.GraphSurface cv    -> layoutGraphSurface(cv, rect, rects);
            case Component.PointCloud pc -> { /* leaf — point data is in PointCloudStates, not children */ }
            case Component.DataTable dt -> { /* leaf — rows/cells are virtualized inside DataTableRenderer */ }
        }
    }

    private static void layoutGraphSurface(Component.GraphSurface surface, PixelRect rect, Map<Component, PixelRect> rects) {
        float pxPerEm = EmContext.pixelsPerEm();
        // Walk declared + dynamically-added children so nodes added via
        // GraphSurfaceChildren.add (e.g. from the command palette) get laid out.
        for (Component child : sibarum.dasum.gui.core.graph.GraphSurfaceChildren.all(surface)) {
            GraphSurfacePositions.Pos pos = GraphSurfacePositions.of(surface, child);
            float childW = intrinsicAxisSize(child, true);
            float childH = intrinsicAxisSize(child, false);
            PixelRect childRect = new PixelRect(
                rect.x() + pos.emX() * pxPerEm,
                rect.y() + pos.emY() * pxPerEm,
                childW, childH
            );
            layoutInto(child, childRect, rects);
        }
    }

    private static void layoutTabs(Component.Tabs tabs, PixelRect rect, Map<Component, PixelRect> rects) {
        Component active = tabs.activeContent();
        if (active == null) return;
        float headerH = tabs.headerHeight().toPixels();
        float pad     = tabs.contentPadding().toPixels();
        PixelRect contentArea = new PixelRect(
            rect.x() + pad,
            rect.y() + headerH + pad,
            Math.max(0f, rect.width()  - 2f * pad),
            Math.max(0f, rect.height() - headerH - 2f * pad)
        );
        // Active panel fills the content area unless it has its own larger extent.
        float childW = childExtentOrFill(active, true,  contentArea.width());
        float childH = childExtentOrFill(active, false, contentArea.height());
        PixelRect childRect = new PixelRect(contentArea.x(), contentArea.y(), childW, childH);
        layoutInto(active, childRect, rects);
    }

    private static void layoutScroll(Component.Scroll scroll, PixelRect rect, Map<Component, PixelRect> rects) {
        Component child = scroll.child();
        if (child == null) return;

        PixelRect interior = insetByPadding(rect, scroll.padding());
        // Child fills the interior on any axis where the child has no
        // explicit extent (e.g. a Flex with width=null). Where the child
        // has an explicit larger size, that size is used and overflow scrolls.
        float childW = childExtentOrFill(child, true,  interior.width());
        float childH = childExtentOrFill(child, false, interior.height());

        ScrollPosition pos = ScrollStates.of(scroll);
        pos.updateBoundsPx(childW - interior.width(), childH - interior.height());

        PixelRect childRect = new PixelRect(
            interior.x() - pos.pxX(),
            interior.y() - pos.pxY(),
            childW, childH
        );
        layoutInto(child, childRect, rects);
    }

    private static float childExtentOrFill(Component c, boolean width, float fillExtent) {
        // For Flex / Scroll / Tabs children, the explicit em size is a
        // *minimum* — they always fill at least the parent's interior. That
        // lets a fixed-width layout shrink-wrap to the viewport when the
        // viewport is wider, but overflow (and scroll) when narrower.
        return switch (c) {
            case Component.Box  b -> width ? b.width().toPixels() : b.height().toPixels();
            case Component.Flex f -> Math.max(resolveFlexAxis(f, width, 0f), fillExtent);
            case Component.Scroll s -> {
                Em e = width ? s.width() : s.height();
                float explicit = e != null ? e.toPixels() : 0f;
                yield Math.max(explicit, fillExtent);
            }
            case Component.Tabs t -> {
                Em e = width ? t.width() : t.height();
                float explicit = e != null ? e.toPixels() : 0f;
                yield Math.max(explicit, fillExtent);
            }
            case Component.Text te  -> width ? TextMetrics.widthPixels(te, TextStates.contentOf(te)) : TextMetrics.heightPixels(te, TextStates.contentOf(te));
            case Component.Checkbox cb -> cb.size().toPixels();
            case Component.Radio<?> r  -> r.size().toPixels();
            case Component.Slider sl   -> width
                ? (sl.horizontal() ? sl.length().toPixels()    : sl.thickness().toPixels())
                : (sl.horizontal() ? sl.thickness().toPixels() : sl.length().toPixels());
            case Component.GraphSurface cv -> {
                Em e = width ? cv.width() : cv.height();
                float explicit = e != null ? e.toPixels() : 0f;
                yield Math.max(explicit, fillExtent);
            }
            case Component.PointCloud pc -> {
                Em e = width ? pc.width() : pc.height();
                float explicit = e != null ? e.toPixels() : 0f;
                yield Math.max(explicit, fillExtent);
            }
            case Component.DataTable dt -> {
                Em e = width ? dt.width() : dt.height();
                float explicit = e != null ? e.toPixels() : 0f;
                yield Math.max(explicit, fillExtent);
            }
        };
    }

    private static float intrinsicAxisSize(Component c, boolean width) {
        return switch (c) {
            case Component.Box  b -> width ? b.width().toPixels()  : b.height().toPixels();
            case Component.Flex f -> resolveFlexAxis(f, width, 0f);
            case Component.Scroll s -> {
                Em e = width ? s.width() : s.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.Tabs t -> {
                Em e = width ? t.width() : t.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.Text t   -> width ? TextMetrics.widthPixels(t, TextStates.contentOf(t)) : TextMetrics.heightPixels(t, TextStates.contentOf(t));
            case Component.Checkbox cb -> cb.size().toPixels();
            case Component.Radio<?> r  -> r.size().toPixels();
            case Component.Slider sl   -> width
                ? (sl.horizontal() ? sl.length().toPixels()    : sl.thickness().toPixels())
                : (sl.horizontal() ? sl.thickness().toPixels() : sl.length().toPixels());
            case Component.GraphSurface cv -> {
                Em e = width ? cv.width() : cv.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.PointCloud pc -> {
                Em e = width ? pc.width() : pc.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.DataTable dt -> {
                Em e = width ? dt.width() : dt.height();
                yield e != null ? e.toPixels() : 0f;
            }
        };
    }

    private static PixelRect insetByPadding(PixelRect rect, Em padding) {
        float pad = padding.toPixels();
        return new PixelRect(
            rect.x() + pad,
            rect.y() + pad,
            Math.max(0f, rect.width()  - 2f * pad),
            Math.max(0f, rect.height() - 2f * pad)
        );
    }

    private static void layoutBoxChildren(Component.Box box, PixelRect rect, Map<Component, PixelRect> rects) {
        PixelRect interior = insetByPadding(rect, box.padding());
        for (Component child : sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(box)) {
            PixelRect childRect = centerChildIn(child, interior);
            layoutInto(child, childRect, rects);
        }
    }

    private static PixelRect centerChildIn(Component child, PixelRect interior) {
        return switch (child) {
            case Component.Box b -> {
                float w = b.width().toPixels();
                float h = b.height().toPixels();
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Flex f -> {
                float w = resolveFlexAxis(f, true,  interior.width());
                float h = resolveFlexAxis(f, false, interior.height());
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Scroll s -> {
                float w = s.width()  != null ? s.width().toPixels()  : interior.width();
                float h = s.height() != null ? s.height().toPixels() : interior.height();
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Text t -> {
                float w = TextMetrics.widthPixels(t, TextStates.contentOf(t));
                float h = TextMetrics.heightPixels(t, TextStates.contentOf(t));
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Checkbox cb -> {
                float s = cb.size().toPixels();
                yield new PixelRect(
                    interior.x() + (interior.width()  - s) * 0.5f,
                    interior.y() + (interior.height() - s) * 0.5f,
                    s, s
                );
            }
            case Component.Radio<?> r -> {
                float s = r.size().toPixels();
                yield new PixelRect(
                    interior.x() + (interior.width()  - s) * 0.5f,
                    interior.y() + (interior.height() - s) * 0.5f,
                    s, s
                );
            }
            case Component.Slider sl -> {
                float w = sl.horizontal() ? sl.length().toPixels()    : sl.thickness().toPixels();
                float h = sl.horizontal() ? sl.thickness().toPixels() : sl.length().toPixels();
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.Tabs t -> {
                float w = t.width()  != null ? t.width().toPixels()  : interior.width();
                float h = t.height() != null ? t.height().toPixels() : interior.height();
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.GraphSurface cv -> {
                float w = cv.width()  != null ? cv.width().toPixels()  : interior.width();
                float h = cv.height() != null ? cv.height().toPixels() : interior.height();
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.PointCloud pc -> {
                float w = pc.width()  != null ? pc.width().toPixels()  : interior.width();
                float h = pc.height() != null ? pc.height().toPixels() : interior.height();
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
            case Component.DataTable dt -> {
                float w = dt.width()  != null ? dt.width().toPixels()  : interior.width();
                float h = dt.height() != null ? dt.height().toPixels() : interior.height();
                yield new PixelRect(
                    interior.x() + (interior.width()  - w) * 0.5f,
                    interior.y() + (interior.height() - h) * 0.5f,
                    w, h
                );
            }
        };
    }

    private static void layoutFlex(Component.Flex flex, PixelRect rect, Map<Component, PixelRect> rects) {
        PixelRect interior = insetByPadding(rect, flex.padding());
        List<Component> kids = sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(flex);
        if (kids.isEmpty()) return;

        boolean row = flex.direction() == Direction.ROW;
        float mainAvail  = row ? interior.width()  : interior.height();
        float crossAvail = row ? interior.height() : interior.width();
        float gap = flex.gap().toPixels();
        int n = kids.size();

        float[] mainSize = new float[n];
        int[]   grow     = new int[n];
        int totalGrow = 0;
        float usedMain = gap * Math.max(0, n - 1);
        for (int i = 0; i < n; i++) {
            mainSize[i] = intrinsicMain(kids.get(i), row);
            grow[i] = kids.get(i).flexGrow();
            totalGrow += grow[i];
            usedMain += mainSize[i];
        }

        float remaining = mainAvail - usedMain;
        float startMain = 0f;
        float extraGap = 0f;

        if (remaining > 0f && totalGrow > 0) {
            float perUnit = remaining / totalGrow;
            for (int i = 0; i < n; i++) mainSize[i] += grow[i] * perUnit;
        } else if (remaining > 0f) {
            switch (flex.justify()) {
                case START          -> startMain = 0f;
                case CENTER         -> startMain = remaining * 0.5f;
                case END            -> startMain = remaining;
                case SPACE_BETWEEN  -> { if (n > 1) extraGap = remaining / (n - 1); }
                case SPACE_AROUND   -> { extraGap = remaining / n; startMain = extraGap * 0.5f; }
                case SPACE_EVENLY   -> { extraGap = remaining / (n + 1); startMain = extraGap; }
            }
        }

        float originMain  = row ? interior.x() : interior.y();
        float originCross = row ? interior.y() : interior.x();
        float cur = originMain + startMain;

        for (int i = 0; i < n; i++) {
            Component child = kids.get(i);
            float childMain  = mainSize[i];
            float childCross = intrinsicCross(child, row);

            float crossOffset;
            if (flex.align() == AlignItems.STRETCH) {
                childCross = crossAvail;
                crossOffset = 0f;
            } else {
                crossOffset = switch (flex.align()) {
                    case START  -> 0f;
                    case CENTER -> (crossAvail - childCross) * 0.5f;
                    case END    -> crossAvail - childCross;
                    case STRETCH -> 0f;
                };
            }

            PixelRect childRect = row
                ? new PixelRect(cur, originCross + crossOffset, childMain, childCross)
                : new PixelRect(originCross + crossOffset, cur, childCross, childMain);

            layoutInto(child, childRect, rects);
            cur += childMain + gap + extraGap;
        }
    }

    private static float intrinsicMain(Component c, boolean row) {
        return switch (c) {
            case Component.Box  b -> row ? b.width().toPixels() : b.height().toPixels();
            case Component.Flex f -> resolveFlexAxis(f, row, 0f);
            case Component.Scroll s -> {
                Em e = row ? s.width() : s.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.Text t   -> row ? TextMetrics.widthPixels(t, TextStates.contentOf(t)) : TextMetrics.heightPixels(t, TextStates.contentOf(t));
            case Component.Checkbox cb -> cb.size().toPixels();
            case Component.Radio<?> r  -> r.size().toPixels();
            case Component.Slider sl   -> row
                ? (sl.horizontal() ? sl.length().toPixels()    : sl.thickness().toPixels())
                : (sl.horizontal() ? sl.thickness().toPixels() : sl.length().toPixels());
            case Component.Tabs t -> {
                Em e = row ? t.width() : t.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.GraphSurface cv -> {
                Em e = row ? cv.width() : cv.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.PointCloud pc -> {
                Em e = row ? pc.width() : pc.height();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.DataTable dt -> {
                Em e = row ? dt.width() : dt.height();
                yield e != null ? e.toPixels() : 0f;
            }
        };
    }

    private static float intrinsicCross(Component c, boolean row) {
        return switch (c) {
            case Component.Box  b -> row ? b.height().toPixels() : b.width().toPixels();
            case Component.Flex f -> resolveFlexAxis(f, !row, 0f);
            case Component.Scroll s -> {
                Em e = row ? s.height() : s.width();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.Text t   -> row ? TextMetrics.heightPixels(t, TextStates.contentOf(t)) : TextMetrics.widthPixels(t, TextStates.contentOf(t));
            case Component.Checkbox cb -> cb.size().toPixels();
            case Component.Radio<?> r  -> r.size().toPixels();
            case Component.Slider sl   -> row
                ? (sl.horizontal() ? sl.thickness().toPixels() : sl.length().toPixels())
                : (sl.horizontal() ? sl.length().toPixels()    : sl.thickness().toPixels());
            case Component.Tabs t -> {
                Em e = row ? t.height() : t.width();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.GraphSurface cv -> {
                Em e = row ? cv.height() : cv.width();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.PointCloud pc -> {
                Em e = row ? pc.height() : pc.width();
                yield e != null ? e.toPixels() : 0f;
            }
            case Component.DataTable dt -> {
                Em e = row ? dt.height() : dt.width();
                yield e != null ? e.toPixels() : 0f;
            }
        };
    }
}
