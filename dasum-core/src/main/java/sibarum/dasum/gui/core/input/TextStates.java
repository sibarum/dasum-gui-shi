package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Identity-keyed registry of {@link TextState} — one per
 * {@link sibarum.dasum.gui.core.component.Component.Text Text} instance.
 * Lookup is create-on-demand. Also exposes a per-instance content-change
 * listener channel used by widgets that need to react to typing
 * (e.g. the Everything Menu's query input filtering its result list).
 */
public final class TextStates {

    private static final Map<Component, TextState> STATES = new IdentityHashMap<>();
    private static final Map<Component, List<Consumer<String>>> LISTENERS = new IdentityHashMap<>();

    private TextStates() {}

    public static TextState of(Component text) {
        TextState s = STATES.get(text);
        if (s == null) {
            s = new TextState();
            STATES.put(text, s);
        }
        return s;
    }

    /**
     * Effective content for a Text component — the per-instance mutable
     * override if one was set (editable text after the user typed), else
     * the Text record's initial content.
     */
    public static String contentOf(Component.Text text) {
        TextState s = STATES.get(text);
        if (s != null && s.content != null) return s.content;
        return text.content();
    }

    /**
     * Stores a new content string for the Text, notifies any registered
     * content-change listeners, and invalidates the renderer.
     * <p>
     * Caret and selection anchor are clamped to the new content length so
     * a subsequent edit can't index past the end (a programmatic content
     * reset to "" while the caret remembers a position from earlier typing
     * was the original bug). The phantom hover caret is cleared for the
     * same reason — its old position may no longer be valid.
     */
    public static void setContent(Component.Text text, String newContent) {
        TextState s = of(text);
        if (newContent.equals(s.content)) return;
        s.content = newContent;
        int len = newContent.length();
        if (s.caretIndex > len)      s.caretIndex      = len;
        if (s.caretIndex < 0)        s.caretIndex      = 0;
        if (s.selectionAnchor > len) s.selectionAnchor = len;
        if (s.selectionAnchor < 0)   s.selectionAnchor = 0;
        s.hoverCaretIndex = -1;
        List<Consumer<String>> ls = LISTENERS.get(text);
        if (ls != null) {
            for (Consumer<String> l : List.copyOf(ls)) l.accept(newContent);
        }
        sibarum.dasum.gui.core.event.Invalidator.invalidate();
    }

    /** Subscribe to {@link #setContent} calls on this Text instance. */
    public static void onContentChange(Component.Text text, Consumer<String> listener) {
        LISTENERS.computeIfAbsent(text, k -> new ArrayList<>()).add(listener);
    }

    /** Clears the hover phantom on every registered Text — call when focus moves elsewhere. */
    public static void clearAllHoverCarets() {
        for (TextState s : STATES.values()) s.hoverCaretIndex = -1;
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops any
     * text state and content-change listeners associated with {@code c}.
     */
    public static void clear(Component c) {
        STATES.remove(c);
        LISTENERS.remove(c);
    }

    /** Copy {@code from}'s edit state + listeners to {@code to}. */
    public static void migrate(Component from, Component to) {
        TextState s = STATES.get(from);
        if (s != null) STATES.put(to, s);
        List<Consumer<String>> ls = LISTENERS.get(from);
        if (ls != null) LISTENERS.put(to, new ArrayList<>(ls));
    }
}
