package sibarum.dasum.gui.core.nav;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.DynamicChildren;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.graph.GraphSurfaceChildren;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Automatic navigation engine. Given a destination — by {@link NavRegistry} ID
 * or directly by component reference — drives the UI to reach it:
 * <ol>
 *   <li>switch every ancestor {@link Component.Tabs} to the panel containing the
 *       target,</li>
 *   <li>then focus the target, which (via {@link FocusState#set}) scrolls every
 *       ancestor {@link Component.Scroll} so it's visible.</li>
 * </ol>
 *
 * <p><b>Why the focus step is deferred.</b> After switching a tab, the newly
 * shown panel hasn't been laid out yet this frame, so its rect is unknown and
 * scroll-into-view would no-op. {@link #navigate(Component)} therefore switches
 * tabs, records a {@code pendingTarget}, and invalidates; {@link #completePending}
 * — wired into {@link LatestLayout} as an after-store callback — focuses the
 * target on the first stored layout where its rect exists. This handles the
 * already-active-tab and cross-tab cases uniformly (one-frame latency).
 *
 * <p>Resolution always walks the <em>current</em> tree (descending into all tab
 * panels, not just the active one), so IDs survive the record-rebuild churn.
 */
public final class Navigator {

    /**
     * Frames a pending navigation waits for its target to lay out before giving
     * up. Guards against a destination that's tagged/registered but never
     * actually rendered (e.g. behind a collapsed section) pinning state forever.
     */
    private static final int MAX_PENDING_FRAMES = 8;

    private static Component pendingTarget = null;
    private static int pendingFrames = 0;

    static {
        // Lazy: this runs the first time Navigator is touched (i.e. the first
        // navigate() call), which is also the only thing that can set a pending
        // target — so the hook is always present before it's needed.
        LatestLayout.addAfterStore(Navigator::completePending);
    }

    private Navigator() {}

    /** Resolve {@code id} against the live tree and navigate to it. No-op if unknown. */
    public static void navigate(String id) {
        if (id == null) return;
        Component root = LatestLayout.root();
        if (root == null) return;
        Component target = findById(root, id);
        if (target != null) navigate(target);
    }

    /** Switch ancestor tabs toward {@code target}, then focus + scroll it into view. */
    public static void navigate(Component target) {
        if (target == null) return;
        Component root = LatestLayout.root();
        if (root != null) {
            for (Component step : pathTo(root, target)) {
                if (step instanceof Component.Tabs tabs) {
                    switchTabToward(tabs, root, target);
                }
            }
        }
        pendingTarget = target;
        pendingFrames = 0;
        Invalidator.invalidate();
    }

    /**
     * Set {@code tabs}' active index to the panel whose content subtree contains
     * {@code target}, if it isn't already. Identity-based containment.
     */
    private static void switchTabToward(Component.Tabs tabs, Component root, Component target) {
        List<Component.Tabs.TabPanel> panels = tabs.tabs();
        for (int p = 0; p < panels.size(); p++) {
            Component content = panels.get(p).content();
            if (content != null && contains(content, target)) {
                Integer cur = tabs.activeIndex().get();
                if (cur == null || cur != p) tabs.activeIndex().set(p);
                return;
            }
        }
    }

    /** Called after each {@link LatestLayout#store}; completes a pending navigation once laid out. */
    static void completePending() {
        if (pendingTarget == null) return;
        LayoutResult lr = LatestLayout.result();
        if (lr != null && lr.rectOf(pendingTarget) != null) {
            Component t = pendingTarget;
            pendingTarget = null;
            pendingFrames = 0;
            FocusState.set(t); // focuses + scrolls every ancestor Scroll into view
            return;
        }
        if (++pendingFrames > MAX_PENDING_FRAMES) {
            pendingTarget = null;
            pendingFrames = 0;
        }
    }

    // ---------- structural tree walk (descends into ALL tab panels) ----------

    private static Component findById(Component c, String id) {
        if (c == null) return null;
        if (id.equals(NavId.idOf(c))) return c;
        for (Component child : childrenOf(c)) {
            Component found = findById(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean contains(Component c, Component target) {
        if (c == target) return true;
        for (Component child : childrenOf(c)) {
            if (contains(child, target)) return true;
        }
        return false;
    }

    /** Ancestor path root→target (inclusive), or empty if unreachable. Identity-based. */
    private static List<Component> pathTo(Component root, Component target) {
        List<Component> out = new ArrayList<>();
        if (findPath(root, target, out)) return out;
        out.clear();
        return out;
    }

    private static boolean findPath(Component c, Component target, List<Component> path) {
        path.add(c);
        if (c == target) return true;
        for (Component child : childrenOf(c)) {
            if (findPath(child, target, path)) return true;
        }
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * Effective children for traversal. Unlike
     * {@link sibarum.dasum.gui.core.layout.HitTest}'s child walk — which follows
     * only the <em>active</em> tab — this descends into <b>every</b> tab panel,
     * so a destination in a not-yet-open tab is still discoverable. Mirrors
     * {@link sibarum.dasum.gui.core.component.Components}' lifecycle walk.
     */
    private static List<Component> childrenOf(Component c) {
        return switch (c) {
            case Component.Box b          -> DynamicChildren.effectiveChildren(b);
            case Component.Flex f         -> DynamicChildren.effectiveChildren(f);
            case Component.Scroll s       -> s.child() == null ? List.of() : List.of(s.child());
            case Component.Tabs t         -> tabContents(t);
            case Component.GraphSurface g -> GraphSurfaceChildren.all(g);
            case Component.Text t         -> List.of();
            case Component.Checkbox cb    -> List.of();
            case Component.Radio<?> r     -> List.of();
            case Component.Slider sl      -> List.of();
            case Component.SceneView pc   -> List.of();
            case Component.DataTable dt   -> List.of();
        };
    }

    private static List<Component> tabContents(Component.Tabs t) {
        List<Component> out = new ArrayList<>(t.tabs().size());
        for (Component.Tabs.TabPanel panel : t.tabs()) {
            if (panel.content() != null) out.add(panel.content());
        }
        return out;
    }
}
