package sibarum.dasum.gui.core.overlay;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.TextState;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.Layout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Process-global stack of active overlays — modal dialogs, popups, the
 * Everything Menu. An overlay is a component subtree that renders above
 * the main UI, positioned in the viewport via an {@link Anchor}.
 * <p>
 * When any modal overlay is active, hit-testing and keyboard navigation
 * are routed exclusively through the topmost overlay's component tree —
 * see {@link #activeInputRoot}. Click-outside on a modal pops it.
 * <p>
 * Focus capture: pushing saves the currently-focused component and moves
 * focus to the first interactive descendant of the overlay. Popping
 * restores the saved focus.
 */
public final class OverlayStack {

    /**
     * @param component  root of the overlay's subtree
     * @param anchor     positioning hint relative to the viewport
     * @param modal      blocks input + draws a backdrop when true
     * @param onDismiss  optional callback fired when the overlay pops
     */
    public record Overlay(Component component, Anchor anchor, boolean modal, Runnable onDismiss) {
        public Overlay(Component component, Anchor anchor, boolean modal) {
            this(component, anchor, modal, null);
        }
    }

    private record Entry(Overlay overlay, Component savedFocus) {}

    private static final List<Entry> stack = new ArrayList<>();

    private OverlayStack() {}

    // ---------- mutation ----------

    public static void push(Overlay o) {
        Component saved = FocusState.focused();
        // Snapshot the saved-focus's selection BEFORE the focus swap.
        // FocusState.set collapses a selectable Text's selection on blur,
        // which would otherwise wipe out the very selection a right-click
        // context menu's Copy/Cut depend on. We restore it below.
        boolean snapSelection = (saved instanceof Component.Text st && st.selectable());
        int savedAnchor = 0, savedCaret = 0;
        if (snapSelection) {
            TextState ts = TextStates.of(saved);
            savedAnchor = ts.selectionAnchor;
            savedCaret  = ts.caretIndex;
        }
        stack.add(new Entry(o, saved));
        // Auto-focus the first interactive descendant so keyboard input lands inside.
        List<Component> interactives = HitTest.collectInteractive(o.component());
        if (!interactives.isEmpty()) FocusState.set(interactives.get(0));
        else FocusState.clear();
        // Re-apply the snapshot — the previous FocusState.set just collapsed it.
        // On pop, the inverse FocusState.set runs with previous = an overlay
        // component (not a Text), so the collapse-on-blur doesn't fire and
        // this restored selection is what the about-to-run action will see.
        if (snapSelection) {
            TextState ts = TextStates.of(saved);
            ts.selectionAnchor = savedAnchor;
            ts.caretIndex      = savedCaret;
        }
        Invalidator.invalidate();
    }

    public static void pop() {
        if (stack.isEmpty()) return;
        Entry e = stack.remove(stack.size() - 1);
        if (e.overlay().onDismiss() != null) e.overlay().onDismiss().run();
        // Restore prior focus (may be null).
        FocusState.set(e.savedFocus());
        Invalidator.invalidate();
    }

    public static void popAll() {
        while (!stack.isEmpty()) pop();
    }

    /**
     * Swap the topmost overlay's component without touching focus or
     * running its dismiss callback — used by dynamic overlays (Everything
     * Menu, etc.) that need to rebuild their tree as state changes.
     */
    public static void replaceTopmost(Component newComponent) {
        if (stack.isEmpty()) return;
        Entry old = stack.get(stack.size() - 1);
        Overlay updated = new Overlay(newComponent, old.overlay().anchor(), old.overlay().modal(), old.overlay().onDismiss());
        stack.set(stack.size() - 1, new Entry(updated, old.savedFocus()));
        Invalidator.invalidate();
    }

    // ---------- queries ----------

    public static boolean isActive() {
        return !stack.isEmpty();
    }

    public static boolean anyModal() {
        for (Entry e : stack) if (e.overlay().modal()) return true;
        return false;
    }

    public static Overlay topmost() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1).overlay();
    }

    public static List<Overlay> active() {
        List<Overlay> out = new ArrayList<>(stack.size());
        for (Entry e : stack) out.add(e.overlay());
        return out;
    }

    /**
     * Returns the topmost overlay's component when the stack is non-empty,
     * otherwise {@code mainRoot}. Used by hit-testers (scrollbar, tabs,
     * regular components) to scope input dispatch to the modal layer.
     */
    public static Component activeInputRoot(Component mainRoot) {
        return stack.isEmpty() ? mainRoot : stack.get(stack.size() - 1).overlay().component();
    }

    // ---------- layout ----------

    /**
     * Compute layouts for every overlay in the stack and merge them into
     * the given map. Each overlay is positioned in the viewport via its
     * anchor; components inside the overlay are then laid out within
     * that rect.
     */
    public static void layoutInto(Map<Component, PixelRect> rects, PixelRect viewport) {
        for (Entry e : stack) {
            PixelRect outer = computeOverlayRect(e.overlay(), viewport);
            LayoutResult lr = Layout.computeWithin(e.overlay().component(), outer);
            rects.putAll(lr.rects());
        }
    }

    private static PixelRect computeOverlayRect(Overlay o, PixelRect viewport) {
        float w, h;
        switch (o.component()) {
            case Component.Box b -> { w = b.width().toPixels();  h = b.height().toPixels(); }
            case Component.Flex f -> {
                w = Layout.resolveFlexAxis(f, true,  viewport.width()  * 0.5f);
                h = Layout.resolveFlexAxis(f, false, viewport.height() * 0.5f);
            }
            case Component.Scroll s -> {
                w = (s.width()  != null) ? s.width().toPixels()  : viewport.width()  * 0.5f;
                h = (s.height() != null) ? s.height().toPixels() : viewport.height() * 0.5f;
            }
            case Component.Tabs t -> {
                w = (t.width()  != null) ? t.width().toPixels()  : viewport.width()  * 0.5f;
                h = (t.height() != null) ? t.height().toPixels() : viewport.height() * 0.5f;
            }
            default -> {
                w = viewport.width() * 0.5f;
                h = viewport.height() * 0.5f;
            }
        }
        return switch (o.anchor()) {
            case Anchor.Center c -> new PixelRect(
                viewport.x() + (viewport.width()  - w) * 0.5f,
                viewport.y() + (viewport.height() - h) * 0.5f,
                w, h
            );
            case Anchor.At at -> {
                // Place top-left at the requested em offset, then clamp so
                // the overlay stays fully inside the viewport — keeps a
                // cursor-anchored popup from being clipped off the right
                // or bottom edge.
                float px = viewport.x() + at.emX().toPixels();
                float py = viewport.y() + at.emY().toPixels();
                if (px + w > viewport.x() + viewport.width())  px = viewport.x() + viewport.width()  - w;
                if (py + h > viewport.y() + viewport.height()) py = viewport.y() + viewport.height() - h;
                if (px < viewport.x()) px = viewport.x();
                if (py < viewport.y()) py = viewport.y();
                yield new PixelRect(px, py, w, h);
            }
        };
    }

    // ---------- click-outside support ----------

    /** Returns true if the cursor sits outside the topmost overlay's outer rect. */
    public static boolean isOutsideTopmost(LayoutResult lr, float x, float y) {
        if (stack.isEmpty() || lr == null) return false;
        Component top = stack.get(stack.size() - 1).overlay().component();
        PixelRect r = lr.rectOf(top);
        if (r == null) return false;
        return !r.contains(x, y);
    }

}
