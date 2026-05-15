package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.reactive.Property;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identity-keyed registry of action-event callbacks for components —
 * click, focus, blur. Parallel to {@link HoverState} / {@link FocusState}
 * / {@link TextStates}: components stay pure-data records; behavior
 * lives in this sidecar.
 * <p>
 * Click events bubble from leaf to root; a handler may return {@code true}
 * to consume the click and stop further bubbling. The {@link Runnable}
 * overload never consumes — register a {@link ClickHandler} explicitly to
 * stop the bubble. Focus and blur fire on the directly-targeted component
 * only (no bubbling).
 * <p>
 * Stateful widgets (checkbox, radio, slider, text input) carry their
 * value as a {@link sibarum.dasum.gui.core.reactive.Property} field —
 * subscribe to the property for value-change notifications instead of
 * registering a handler here.
 */
public final class Handlers {

    @FunctionalInterface
    public interface ClickHandler {
        /** Return {@code true} to consume the click and stop bubbling. */
        boolean onClick();
    }

    private static final Map<Component, ClickHandler> CLICK = new IdentityHashMap<>();
    private static final Map<Component, Runnable>     FOCUS = new IdentityHashMap<>();
    private static final Map<Component, Runnable>     BLUR  = new IdentityHashMap<>();

    private Handlers() {}

    public static void onClick(Component c, ClickHandler h) { CLICK.put(c, h); }

    public static void onClick(Component c, Runnable r) {
        CLICK.put(c, () -> { r.run(); return false; });
    }

    public static void onFocus(Component c, Runnable r) { FOCUS.put(c, r); }
    public static void onBlur (Component c, Runnable r) { BLUR .put(c, r); }

    /**
     * Register a context-menu provider for {@code c}. Right-clicks resolved
     * to {@code c} (or its descendants without their own provider) will
     * invoke {@code p} to produce the menu items. Pass a provider that
     * returns {@link List#of()} — or use {@link #disableContextMenu} — to
     * suppress the default menu on a selectable Text component.
     */
    public static void onContextMenu(Component c, ContextMenuProvider p) {
        ContextMenuStates.put(c, p);
    }

    /**
     * Suppress the context menu for {@code c}, including the built-in
     * Text default. Equivalent to registering a provider that returns
     * an empty list.
     */
    public static void disableContextMenu(Component c) {
        ContextMenuStates.put(c, event -> List.of());
    }

    /** Remove every handler registered against {@code c}. */
    public static void clearAll(Component c) {
        CLICK.remove(c);
        FOCUS.remove(c);
        BLUR .remove(c);
        ContextMenuStates.remove(c);
    }

    // ---------- dispatch ----------

    /**
     * Fire a click. Walks the ancestor chain from {@code leaf} up to
     * {@code root}, invoking each registered handler. Stops if a handler
     * returns {@code true}.
     */
    public static void fireClick(Component leaf, Component root) {
        if (leaf == null || root == null) return;
        List<Component> path = HitTest.pathTo(root, leaf); // root → leaf
        for (int i = path.size() - 1; i >= 0; i--) {
            ClickHandler h = CLICK.get(path.get(i));
            if (h != null && h.onClick()) return;
        }
    }

    /**
     * Activate a widget — runs the variant's built-in behavior (toggle for
     * Checkbox, select for Radio), then bubbles a click through the
     * ancestor chain so app-registered handlers fire as well. Called by
     * mouse release and by keyboard activation (Space / Enter on a
     * focused widget).
     */
    public static void activate(Component target, Component root) {
        if (target == null || root == null) return;
        if (target instanceof Component.Checkbox cb) {
            cb.value().set(!Boolean.TRUE.equals(cb.value().get()));
        } else if (target instanceof Component.Radio<?> r) {
            selectRadio(r);
        }
        fireClick(target, root);
    }

    private static <T> void selectRadio(Component.Radio<T> r) {
        Property<T> group = r.group();
        group.set(r.value());
    }

    public static void fireFocus(Component c) {
        if (c == null) return;
        Runnable r = FOCUS.get(c);
        if (r != null) r.run();
    }

    public static void fireBlur(Component c) {
        if (c == null) return;
        Runnable r = BLUR.get(c);
        if (r != null) r.run();
    }
}
