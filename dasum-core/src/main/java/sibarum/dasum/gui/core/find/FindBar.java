package sibarum.dasum.gui.core.find;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.input.TextInputController;
import sibarum.dasum.gui.core.input.TextState;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.input.TextStyle;
import sibarum.dasum.gui.core.input.TextStyleStates;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.Anchor;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.Icon;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global in-editor "Find" bar, modelled on IntelliJ's editor search. Open
 * with {@link #open()} (typically bound to Ctrl+F) while a selectable
 * {@link Component.Text} is focused; a small non-modal panel floats over the
 * top-right of that text area with an auto-focused query field.
 * <p>
 * As the query changes, every case-insensitive match in the target is
 * highlighted (via {@link TextStyleStates#setBackground background ranges});
 * the <em>current</em> match is shown as a selection and scrolled into view.
 * <ul>
 *   <li>Typing filters live.</li>
 *   <li>Enter / Down → next match; Shift+Enter / Up → previous match; both
 *       wrap around.</li>
 *   <li>Esc (handled by the generic overlay pop) or the ✕ cell closes the bar
 *       and clears the highlights.</li>
 * </ul>
 * The panel drives the target by component identity — it never takes focus
 * away from the query field. State survives rebuilds because the query
 * {@code Component.Text} instance is reused.
 */
public final class FindBar {

    private static final Color PANEL_BG   = new Color(0.10f, 0.12f, 0.16f, 1f);
    private static final Color INPUT_BG   = new Color(0.13f, 0.16f, 0.21f, 1f);
    private static final Color CELL_BG    = new Color(0.16f, 0.19f, 0.25f, 1f);
    private static final Color FG         = new Color(0.92f, 0.94f, 0.97f, 1f);
    private static final Color FG_DIM     = new Color(0.62f, 0.66f, 0.72f, 1f);
    private static final Color BORDER_BG  = new Color(0.30f, 0.55f, 0.85f, 0.9f);
    private static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);
    /** Fill drawn behind every non-current match (amber, IntelliJ-ish). */
    private static final Color MATCH_BG   = new Color(0.72f, 0.55f, 0.10f, 0.55f);

    private static final float PANEL_WIDTH_EM = 26f;

    /**
     * App-supplied icon glyphs for the bar's controls, drawn from a
     * registered icon {@link sibarum.dasum.gui.core.text.FontGroup} (e.g.
     * lucide). {@code dasum-core} carries no icon glyphs of its own — same
     * separation as {@link Component.Tabs.OverflowGlyph} — so an app that
     * wants proper chevrons/close/search glyphs calls {@link #configureIcons}
     * once at startup. When unset, the controls fall back to ASCII glyphs
     * that live in the default text atlas. Any codepoint may be {@code -1} to
     * force the ASCII fallback for that one control.
     *
     * @param fontGroup registered icon font-group name (e.g. {@link Icon#DEFAULT_FONT_GROUP})
     * @param prevCp    "previous match" glyph (e.g. chevron-up)
     * @param nextCp    "next match" glyph (e.g. chevron-down)
     * @param closeCp   "close" glyph (e.g. x)
     * @param searchCp  leading magnifier glyph inside the query field, or {@code -1} for none
     */
    public record IconSpec(String fontGroup, int prevCp, int nextCp, int closeCp, int searchCp) {}

    private static IconSpec icons = null;

    private static Component.Text queryInput = null;
    private static boolean listenerAttached = false;

    private static Component.Text target = null;
    private static List<int[]> matches = new ArrayList<>();
    private static int current = -1;
    /** Caret offset in the target when Find opened — anchors "first match". */
    private static int searchOrigin = 0;

    private static Component currentRoot = null;
    private static final List<Component> currentCells = new ArrayList<>();
    private static boolean open = false;

    private FindBar() {}

    // ---------- public API ----------

    /**
     * Register the icon glyphs used for the prev / next / close controls and
     * the leading search glyph. Call once at startup after the icon font is
     * registered; without it the bar renders ASCII fallbacks. See {@link IconSpec}.
     */
    public static void configureIcons(IconSpec spec) {
        icons = spec;
    }

    /**
     * Open the Find bar for the currently focused text area. No-op unless
     * focus is a selectable {@link Component.Text}, or the bar is already open.
     */
    public static void open() {
        if (open) return;
        if (!(FocusState.focused() instanceof Component.Text t) || !t.selectable()) return;

        ensureQueryInput();
        target = t;

        // Seed the query from the target's current selection, IntelliJ-style.
        TextState ts = TextStates.of(target);
        String seed = "";
        if (ts.hasSelection()) {
            String content = TextStates.contentOf(target);
            int s = Math.max(0, Math.min(ts.selectionStart(), content.length()));
            int e = Math.max(0, Math.min(ts.selectionEnd(), content.length()));
            seed = content.substring(s, e);
            // Don't seed a multi-line blob into a single-line search field.
            if (seed.indexOf('\n') >= 0) seed = "";
        }
        searchOrigin = ts.caretIndex;

        // Set content while still closed so the change listener stays quiet
        // (it guards on `open`); we compute the initial state explicitly below.
        TextStates.setContent(queryInput, seed);

        open = true;
        recompute();
        buildTree();
        OverlayStack.push(new OverlayStack.Overlay(currentRoot, anchorFor(target), false, FindBar::onDismiss));
    }

    public static void close() {
        if (!open) return;
        OverlayStack.pop(); // fires onDismiss
    }

    public static boolean isOpen() {
        return open;
    }

    /**
     * Intercept navigation keys while the bar is open. Enter / Down → next
     * match, Shift+Enter / Up → previous, wrapping. Returns {@code true} when
     * consumed; call this before the text-input key handler so Enter doesn't
     * insert a newline and the arrows don't move the query caret. Esc is left
     * to the generic overlay pop.
     */
    public static boolean handleKey(int key, boolean shift) {
        if (!open) return false;
        switch (key) {
            case Glfw.GLFW_KEY_ENTER -> { step(shift ? -1 : +1); return true; }
            case Glfw.GLFW_KEY_DOWN  -> { step(+1); return true; }
            case Glfw.GLFW_KEY_UP    -> { step(-1); return true; }
            default -> { return false; }
        }
    }

    // ---------- matching (pure, unit-tested) ----------

    /**
     * Non-overlapping, case-insensitive occurrences of {@code query} in
     * {@code content}, each as a {@code [start, end)} char-index pair, in
     * document order. Empty/blank query or content yields an empty list.
     */
    public static List<int[]> findMatches(String content, String query) {
        List<int[]> out = new ArrayList<>();
        if (content == null || query == null || content.isEmpty() || query.isEmpty()) return out;
        String hay = content.toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int idx = hay.indexOf(needle, from);
            if (idx < 0) break;
            out.add(new int[]{idx, idx + needle.length()});
            from = idx + needle.length();
        }
        return out;
    }

    // ---------- internals ----------

    private static void ensureQueryInput() {
        if (queryInput != null) return;
        queryInput = new Component.Text(
            "", FontGroups.DEFAULT, Em.of(1.0f), FG,
            null, null, Em.of(0.35f),
            null, true,
            true, true, true, false, 1
        );
        if (!listenerAttached) {
            TextStates.onContentChange(queryInput, q -> {
                if (open) {
                    recompute();
                    rebuild();
                }
            });
            listenerAttached = true;
        }
    }

    /** Recompute matches for the current query, re-highlight, reveal current. */
    private static void recompute() {
        matches = findMatches(TextStates.contentOf(target), TextStates.contentOf(queryInput));
        current = pickInitialMatch();
        applyHighlights();
        moveSelectionToCurrent();
    }

    private static int pickInitialMatch() {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i)[0] >= searchOrigin) return i;
        }
        return matches.isEmpty() ? -1 : 0;
    }

    private static void step(int dir) {
        if (matches.isEmpty()) return;
        int n = matches.size();
        current = ((current + dir) % n + n) % n;
        applyHighlights();
        moveSelectionToCurrent();
        rebuild();
    }

    /** Publish background fills for every match except the current one. */
    private static void applyHighlights() {
        if (target == null) return;
        List<TextStyle> bg = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            if (i == current) continue; // current is shown via the selection fill
            int[] m = matches.get(i);
            bg.add(new TextStyle(m[0], m[1], MATCH_BG));
        }
        TextStyleStates.setBackground(target, bg);
    }

    private static void moveSelectionToCurrent() {
        if (target == null) return;
        if (matches.isEmpty() || current < 0) {
            int c = TextStates.of(target).caretIndex;
            TextInputController.selectRange(target, c, c); // collapse — no stale selection
        } else {
            int[] m = matches.get(current);
            TextInputController.selectRange(target, m[0], m[1]);
        }
    }

    private static void rebuild() {
        buildTree();
        if (open && OverlayStack.isActive()) {
            OverlayStack.replaceTopmost(currentRoot);
        }
    }

    private static void onDismiss() {
        open = false;
        if (target != null) TextStyleStates.clearRanges(target);
        for (Component cell : currentCells) Handlers.clearAll(cell);
        currentCells.clear();
        matches = new ArrayList<>();
        current = -1;
        target = null;
        Invalidator.invalidate();
    }

    // ---------- layout ----------

    /**
     * Anchor the panel's top-right at the target's top-right corner. Falls
     * back to {@link Anchor#CENTER} if the target has no laid-out rect yet.
     */
    private static Anchor anchorFor(Component.Text t) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return Anchor.CENTER;
        PixelRect rect = lr.rectOf(t);
        if (rect == null) return Anchor.CENTER;
        // Anchor.At is relative to the viewport's top-left; subtract the
        // root rect origin so we work in viewport-local pixels.
        PixelRect viewport = (LatestLayout.root() != null) ? lr.rectOf(LatestLayout.root()) : null;
        float ox = (viewport != null) ? viewport.x() : 0f;
        float oy = (viewport != null) ? viewport.y() : 0f;
        float ppe = EmContext.pixelsPerEm();
        float panelWidthPx = PANEL_WIDTH_EM * ppe;
        float rightLocalPx = (rect.right() - ox);
        float topLocalPx = (rect.y() - oy);
        // computeOverlayRect clamps the panel back inside the viewport, so a
        // negative emX (panel wider than the space left of the right edge) is
        // fine — it just pins the panel against the right edge.
        Em emX = Em.of((rightLocalPx - panelWidthPx) / ppe);
        Em emY = Em.of(topLocalPx / ppe);
        return Anchor.at(emX, emY);
    }

    private static void buildTree() {
        for (Component cell : currentCells) Handlers.clearAll(cell);
        currentCells.clear();

        String query = TextStates.contentOf(queryInput);
        String countText;
        if (query.isEmpty())        countText = "";
        else if (matches.isEmpty()) countText = "No results";
        else                        countText = (current + 1) + "/" + matches.size();

        // Query field, wrapped in a filled cell so it reads as an input box,
        // with an optional leading search glyph.
        List<Component> inputChildren = new ArrayList<>();
        if (icons != null && icons.searchCp() >= 0) {
            inputChildren.add(Icon.of(icons.searchCp(), icons.fontGroup(), Em.of(0.9f), FG_DIM));
        }
        inputChildren.add(queryInput);
        Component.Flex inputBox = new Component.Flex(
            null, Em.AUTO, Em.of(0.15f), INPUT_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            inputChildren,
            false, 1
        );

        Component.Text count = new Component.Text(
            countText, FontGroups.DEFAULT, Em.of(0.85f),
            matches.isEmpty() && !query.isEmpty() ? FG_DIM : FG,
            null, null, Em.of(0.2f),
            null, false,
            false, false, false, false, 0
        );

        Component prev  = cell(iconLabel(icons != null ? icons.prevCp()  : -1, "<"), () -> step(-1), true);
        Component next  = cell(iconLabel(icons != null ? icons.nextCp()  : -1, ">"), () -> step(+1), true);
        Component close = cell(iconLabel(icons != null ? icons.closeCp() : -1, "x"), FindBar::close, false);

        Component.Flex dialog = new Component.Flex(
            Em.of(PANEL_WIDTH_EM), Em.AUTO, Em.of(0.4f), PANEL_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.4f),
            List.of(inputBox, count, prev, next, close),
            false, 0
        );

        // Thin border halo via an outer Flex, matching the palette dialogs.
        currentRoot = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.125f), BORDER_BG,
            Direction.COLUMN, JustifyContent.CENTER, AlignItems.STRETCH, Em.ZERO,
            List.of(dialog),
            false, 0
        );
    }

    /** A control glyph: the app-configured icon codepoint, or an ASCII fallback. */
    private static Component iconLabel(int cp, String ascii) {
        if (cp >= 0 && icons != null) {
            return Icon.of(cp, icons.fontGroup(), Em.of(0.95f), FG);
        }
        return new Component.Text(
            ascii, FontGroups.DEFAULT, Em.of(0.95f), FG,
            null, null, Em.ZERO,
            null, false,
            false, false, false, false, 0
        );
    }

    private static Component cell(Component label, Runnable onClick, boolean keepQueryFocus) {
        Component.Flex cell = new Component.Flex(
            Em.of(1.7f), Em.of(1.7f), Em.of(0.1f), CELL_BG,
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(label),
            true, 0
        );
        Handlers.onClick(cell, () -> {
            onClick.run();
            // A press focuses whatever it hits; for the nav cells hand focus
            // back to the query field so the user can keep typing. (The close
            // cell pops the overlay, which restores focus to the target.)
            if (keepQueryFocus) FocusState.set(queryInput);
        });
        currentCells.add(cell);
        return cell;
    }
}
