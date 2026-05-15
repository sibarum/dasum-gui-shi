package sibarum.dasum.gui.core.command;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.overlay.Anchor;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.theme.Theme;
import sibarum.dasum.gui.core.theme.Variant;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal command palette built on the overlay layer. Open with
 * {@link #open()} (typically bound to Ctrl+Space); the modal contains a
 * query input auto-focused for typing and a scrollable list of matching
 * commands from the {@link CommandRegistry}.
 * <p>
 * Keyboard: typing filters; Up/Down moves the selection (wraps);
 * Enter activates the selected command and dismisses; ESC dismisses
 * without running anything; click a row to activate it. The query input
 * keeps its caret state across rebuilds because its Component identity
 * is reused on every refresh.
 */
public final class EverythingMenu {

    private static final Color DIALOG_BG  = new Color(0.10f, 0.12f, 0.16f, 1f);
    private static final Color LIST_BG    = new Color(0.07f, 0.09f, 0.13f, 1f);
    private static final Color INPUT_BG   = new Color(0.13f, 0.16f, 0.21f, 1f);
    private static final Color ROW_FG     = new Color(0.92f, 0.94f, 0.97f, 1f);
    private static final Color BORDER_BG  = new Color(0.30f, 0.55f, 0.85f, 0.9f);
    private static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);

    private static Component.Text queryInput = null;
    private static final Property<Integer> selectedIndex = new Property<>(0);
    private static List<CommandRegistry.Command> currentResults = new ArrayList<>();
    private static final List<Component> currentRows = new ArrayList<>();
    private static Component currentRoot = null;
    private static boolean menuOpen = false;

    private EverythingMenu() {}

    // ---------- public API ----------

    public static void open() {
        if (queryInput == null) {
            queryInput = new Component.Text(
                "", FontGroups.DEFAULT, Em.of(1.05f), ROW_FG,
                Em.of(28f), null, Em.of(0.45f),
                null, false,
                true, true, true, false, 0
            );
            TextStates.onContentChange(queryInput, q -> {
                if (menuOpen) {
                    selectedIndex.set(0);
                    rebuild();
                }
            });
        }
        TextStates.setContent(queryInput, "");
        selectedIndex.set(0);
        menuOpen = true;
        // Build the tree FIRST, then push. Calling rebuild() here would
        // replaceTopmost — which corrupts any pre-existing overlay's stack
        // entry (e.g. an open Help dialog) by overwriting its component.
        buildTree();
        OverlayStack.push(new OverlayStack.Overlay(currentRoot, Anchor.CENTER, true, EverythingMenu::onDismiss));
    }

    public static void close() {
        if (!menuOpen) return;
        OverlayStack.pop();
    }

    public static boolean isOpen() {
        return menuOpen;
    }

    /**
     * Intercept arrow / Enter when the menu is open. Returns true if the
     * key was consumed; callers should invoke this before the regular
     * text-input key handler so Up/Down don't move the query caret.
     */
    public static boolean handleKey(int key) {
        if (!menuOpen) return false;
        int n = currentResults.size();
        switch (key) {
            case Glfw.GLFW_KEY_UP -> {
                if (n > 0) {
                    selectedIndex.set(((selectedIndex.get() - 1) % n + n) % n);
                    rebuild();
                }
                return true;
            }
            case Glfw.GLFW_KEY_DOWN -> {
                if (n > 0) {
                    selectedIndex.set((selectedIndex.get() + 1) % n);
                    rebuild();
                }
                return true;
            }
            case Glfw.GLFW_KEY_ENTER -> {
                activateSelected();
                return true;
            }
            default -> { return false; }
        }
    }

    // ---------- internals ----------

    private static void onDismiss() {
        menuOpen = false;
        for (Component row : currentRows) Handlers.clearAll(row);
        currentRows.clear();
        currentResults = List.of();
        Invalidator.invalidate();
    }

    private static void activateSelected() {
        if (selectedIndex.get() >= 0 && selectedIndex.get() < currentResults.size()) {
            CommandRegistry.Command cmd = currentResults.get(selectedIndex.get());
            close();
            cmd.action().run();
        } else {
            close();
        }
    }

    private static void rebuild() {
        buildTree();
        if (menuOpen && OverlayStack.isActive()) {
            OverlayStack.replaceTopmost(currentRoot);
        }
    }

    private static void buildTree() {
        for (Component row : currentRows) Handlers.clearAll(row);
        currentRows.clear();

        String query = TextStates.contentOf(queryInput);
        currentResults = CommandRegistry.filter(query);

        int sel = selectedIndex.get();
        if (sel >= currentResults.size()) sel = Math.max(0, currentResults.size() - 1);
        if (currentResults.isEmpty())     sel = 0;
        if (sel != selectedIndex.get())   selectedIndex.set(sel);
        final int selFinal = sel;

        Color selectedBg = Theme.of(Variant.PRIMARY).base();
        Color selectedFg = Theme.of(Variant.PRIMARY).onBase();

        List<Component> rows = new ArrayList<>();
        for (int i = 0; i < currentResults.size(); i++) {
            CommandRegistry.Command cmd = currentResults.get(i);
            boolean isSelected = (i == selFinal);
            Color rowBg = isSelected ? selectedBg : TRANSPARENT;
            Color rowFg = isSelected ? selectedFg : ROW_FG;

            Component.Text label = new Component.Text(
                cmd.label(), FontGroups.DEFAULT, Em.of(0.95f), rowFg,
                null, null, Em.of(0.35f),
                null, false,
                false, false, false, false, 0
            );
            Component.Flex row = new Component.Flex(
                null, Em.of(1.8f), Em.of(0.3f), rowBg,
                Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
                List.of(label),
                true, 0
            );
            final int idx = i;
            Handlers.onClick(row, () -> {
                selectedIndex.set(idx);
                activateSelected();
            });
            rows.add(row);
            currentRows.add(row);
        }

        Component listInner = new Component.Flex(
            null, Em.AUTO, Em.ZERO, TRANSPARENT,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            rows,
            false, 0
        );
        Component resultScroll = new Component.Scroll(
            null, Em.of(14f), Em.of(0.25f), LIST_BG,
            listInner, false, 0
        );

        Component.Flex inputBox = new Component.Flex(
            null, Em.AUTO, Em.ZERO, INPUT_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(queryInput),
            false, 0
        );

        Component.Flex dialog = new Component.Flex(
            Em.of(30f), Em.AUTO, Em.of(1.0f), DIALOG_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.6f),
            List.of(inputBox, resultScroll),
            false, 0
        );

        // Thin border halo via outer Flex.
        currentRoot = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.125f), BORDER_BG,
            Direction.COLUMN, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(dialog),
            false, 0
        );
    }
}
