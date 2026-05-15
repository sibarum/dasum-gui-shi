package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.TextGeometry;
import sibarum.dasum.gui.core.text.TextMetrics;
import sibarum.dasum.gui.core.text.WordBoundary;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.lang.foreign.MemorySegment;

/**
 * Translates input events into caret / selection / content edits for
 * selectable and editable {@link Component.Text} components.
 * <p>
 * Static API; callers (the App's input wiring) invoke the methods from
 * the appropriate GLFW callback. State is held in {@link TextState}
 * sidecars per Text-component identity. Multi-click detection is a
 * controller-internal concern.
 */
public final class TextInputController {

    private TextInputController() {}

    // ---------- multi-click tracking ----------

    private static final long   MULTI_CLICK_NANOS  = 500_000_000L; // 500 ms
    private static final double MULTI_CLICK_PIXELS = 5.0d;

    private static long      lastClickNanos    = 0L;
    private static Component lastClickTarget   = null;
    private static double    lastClickX        = 0d;
    private static double    lastClickY        = 0d;
    private static int       consecutiveClicks = 0;

    // ---------- mouse ----------

    public static void onMouseDown(Component hit, double cursorX, double cursorY, boolean shiftHeld) {
        if (!(hit instanceof Component.Text text) || !text.selectable()) {
            // Click elsewhere resets the multi-click streak.
            consecutiveClicks = 0;
            lastClickTarget = null;
            return;
        }
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect rect = lr.rectOf(text);
        if (rect == null) return;

        long now = System.nanoTime();
        boolean sameAsLast = (lastClickTarget == hit)
            && (now - lastClickNanos) < MULTI_CLICK_NANOS
            && Math.abs(cursorX - lastClickX) < MULTI_CLICK_PIXELS
            && Math.abs(cursorY - lastClickY) < MULTI_CLICK_PIXELS
            && !shiftHeld;
        int clicks = sameAsLast ? (consecutiveClicks % 3) + 1 : 1;
        consecutiveClicks = clicks;
        lastClickNanos    = now;
        lastClickTarget   = hit;
        lastClickX        = cursorX;
        lastClickY        = cursorY;

        String content = TextStates.contentOf(text);
        int hitIdx = TextGeometry.charIndexAt(text, content, rect, (float) cursorX, (float) cursorY);
        TextState ts = TextStates.of(text);

        switch (clicks) {
            case 2 -> {
                int[] bounds = WordBoundary.wordBoundsAt(content, hitIdx);
                ts.selectionAnchor = bounds[0];
                ts.caretIndex      = bounds[1];
                ts.lastVisualX     = TextGeometry.caretVisualX(text, content, rect, ts.caretIndex);
            }
            case 3 -> {
                int lineStart = TextGeometry.lineStartOfIndex(text, content, hitIdx);
                int lineEnd   = TextGeometry.lineEndOfIndex(text, content, hitIdx);
                ts.selectionAnchor = lineStart;
                ts.caretIndex      = lineEnd;
                ts.lastVisualX     = TextGeometry.caretVisualX(text, content, rect, ts.caretIndex);
            }
            default -> {
                ts.caretIndex = hitIdx;
                if (!shiftHeld) ts.selectionAnchor = hitIdx;
                ts.lastVisualX = TextGeometry.caretVisualX(text, content, rect, hitIdx);
            }
        }
        ts.hoverCaretIndex = -1;
        Invalidator.invalidate();
        scrollCaretIntoView(text);
    }

    public static void onCursorMove(Component hovered, double cursorX, double cursorY) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;

        TextStates.clearAllHoverCarets();
        if (hovered instanceof Component.Text text && text.selectable()
            && FocusState.focused() != text) {
            PixelRect rect = lr.rectOf(text);
            if (rect != null) {
                TextState ts = TextStates.of(text);
                ts.hoverCaretIndex = TextGeometry.charIndexAt(text, TextStates.contentOf(text),
                    rect, (float) cursorX, (float) cursorY);
                Invalidator.invalidate();
            }
        }

        if (InputState.leftButtonHeld()
            && FocusState.focused() instanceof Component.Text dragText && dragText.selectable()) {
            PixelRect rect = lr.rectOf(dragText);
            if (rect == null) return;
            String content = TextStates.contentOf(dragText);
            int newCaret = TextGeometry.charIndexAt(dragText, content, rect, (float) cursorX, (float) cursorY);
            TextState ts = TextStates.of(dragText);
            if (newCaret != ts.caretIndex) {
                ts.caretIndex = newCaret;
                ts.lastVisualX = TextGeometry.caretVisualX(dragText, content, rect, newCaret);
                Invalidator.invalidate();
                scrollCaretIntoView(dragText);
            }
        }
    }

    // ---------- key navigation ----------

    /** Returns true if the key was consumed by the focused Text. */
    public static boolean onKey(int key, boolean shiftHeld, boolean ctrlHeld) {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.selectable()) return false;
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return false;
        PixelRect rect = lr.rectOf(text);
        if (rect == null) return false;

        TextState ts = TextStates.of(text);
        ts.hoverCaretIndex = -1;

        String content = TextStates.contentOf(text);
        int oldCaret = ts.caretIndex;
        int newCaret = oldCaret;
        boolean preserveVisualX = false;

        switch (key) {
            case Glfw.GLFW_KEY_LEFT  -> newCaret = ctrlHeld
                ? WordBoundary.prevWordBoundary(content, oldCaret)
                : Math.max(0, oldCaret - 1);
            case Glfw.GLFW_KEY_RIGHT -> newCaret = ctrlHeld
                ? WordBoundary.nextWordBoundary(content, oldCaret)
                : Math.min(content.length(), oldCaret + 1);
            case Glfw.GLFW_KEY_HOME  -> newCaret = ctrlHeld
                ? 0
                : TextGeometry.lineStartOfIndex(text, content, oldCaret);
            case Glfw.GLFW_KEY_END   -> newCaret = ctrlHeld
                ? content.length()
                : TextGeometry.lineEndOfIndex(text, content, oldCaret);
            case Glfw.GLFW_KEY_UP    -> { newCaret = moveVertically(text, content, rect, ts, -1); preserveVisualX = true; }
            case Glfw.GLFW_KEY_DOWN  -> { newCaret = moveVertically(text, content, rect, ts, +1); preserveVisualX = true; }
            default -> { return false; }
        }

        boolean changed = (newCaret != oldCaret) || (!shiftHeld && ts.hasSelection());
        ts.caretIndex = newCaret;
        if (!shiftHeld) ts.selectionAnchor = newCaret;
        if (!preserveVisualX) ts.lastVisualX = TextGeometry.caretVisualX(text, content, rect, newCaret);
        if (changed) {
            Invalidator.invalidate();
            scrollCaretIntoView(text);
        }
        return true;
    }

    private static int moveVertically(Component.Text text, String content, PixelRect rect, TextState ts, int dir) {
        FontGroup fg = FontGroups.getOrDefault(text.fontGroup());
        float fontPx = text.fontSize().toPixels();
        float lineH  = fg.atlas().metrics().lineHeight() * fontPx;
        float pad    = text.padding().toPixels();

        int curLine = TextGeometry.lineOfIndex(text, content, ts.caretIndex);
        int targetLine = curLine + dir;
        int totalLines = Math.max(1, TextMetrics.lineCount(text, content));
        if (targetLine < 0)            return TextGeometry.lineStartOfIndex(text, content, ts.caretIndex);
        if (targetLine >= totalLines)  return TextGeometry.lineEndOfIndex(text, content, ts.caretIndex);

        float targetX = (ts.lastVisualX >= 0f)
            ? ts.lastVisualX
            : TextGeometry.caretVisualX(text, content, rect, ts.caretIndex);
        float targetY = rect.y() + pad + targetLine * lineH + lineH * 0.5f;
        return TextGeometry.charIndexAt(text, content, rect, targetX, targetY);
    }

    /** Selects the entire content of the focused selectable Text. */
    public static boolean onSelectAll() {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.selectable()) return false;
        TextState ts = TextStates.of(text);
        String content = TextStates.contentOf(text);
        ts.selectionAnchor = 0;
        ts.caretIndex      = content.length();
        ts.lastVisualX     = -1f;
        ts.hoverCaretIndex = -1;
        Invalidator.invalidate();
        scrollCaretIntoView(text);
        return true;
    }

    // ---------- editing ----------

    public static boolean onCharInput(int codepoint) {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.editable()) return false;
        // Tab is reserved for focus cycling unless Text.acceptsTab() — and Tab
        // doesn't reach this callback through GLFW char events anyway; it comes
        // via the key callback. Other control chars are rejected here.
        if (codepoint < 32 || codepoint == 0x7F /* DEL */) return false;
        TextState ts = TextStates.of(text);
        // Selection-replacing inserts can't coalesce with prior typing.
        UndoStack.EditKind kind = ts.hasSelection() ? UndoStack.EditKind.OTHER : UndoStack.EditKind.INSERT_CHAR;
        insertString(text, new String(Character.toChars(codepoint)), kind);
        return true;
    }

    public static boolean onBackspace(boolean ctrlHeld) {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.editable()) return false;
        TextState ts = TextStates.of(text);
        String content = TextStates.contentOf(text);
        if (ts.hasSelection()) {
            replaceRange(text, ts.selectionStart(), ts.selectionEnd(), "", UndoStack.EditKind.OTHER);
        } else if (ctrlHeld) {
            int from = WordBoundary.prevWordBoundary(content, ts.caretIndex);
            if (from < ts.caretIndex) replaceRange(text, from, ts.caretIndex, "", UndoStack.EditKind.OTHER);
        } else if (ts.caretIndex > 0) {
            int prevCp  = content.codePointBefore(ts.caretIndex);
            int prevLen = Character.charCount(prevCp);
            replaceRange(text, ts.caretIndex - prevLen, ts.caretIndex, "", UndoStack.EditKind.DELETE_BACK);
        }
        return true;
    }

    public static boolean onDelete(boolean ctrlHeld) {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.editable()) return false;
        TextState ts = TextStates.of(text);
        String content = TextStates.contentOf(text);
        if (ts.hasSelection()) {
            replaceRange(text, ts.selectionStart(), ts.selectionEnd(), "", UndoStack.EditKind.OTHER);
        } else if (ctrlHeld) {
            int to = WordBoundary.nextWordBoundary(content, ts.caretIndex);
            if (to > ts.caretIndex) replaceRange(text, ts.caretIndex, to, "", UndoStack.EditKind.OTHER);
        } else if (ts.caretIndex < content.length()) {
            int cp  = content.codePointAt(ts.caretIndex);
            int len = Character.charCount(cp);
            replaceRange(text, ts.caretIndex, ts.caretIndex + len, "", UndoStack.EditKind.DELETE_FORWARD);
        }
        return true;
    }

    public static boolean onEnter() {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.editable()) return false;
        insertString(text, "\n", UndoStack.EditKind.OTHER);
        return true;
    }

    /**
     * Tab handler. Consumes the keystroke only when the focused Text is
     * editable AND {@code acceptsTab}; otherwise returns false so the
     * caller can fall back to focus cycling.
     */
    public static boolean onTab() {
        if (!(FocusState.focused() instanceof Component.Text text)) return false;
        if (!text.editable() || !text.acceptsTab()) return false;
        insertString(text, "\t", UndoStack.EditKind.OTHER);
        return true;
    }

    // ---------- undo / redo ----------

    public static boolean onUndo() {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.editable()) return false;
        TextState ts = TextStates.of(text);
        UndoStack.EditOp op = ts.undoStack.popUndo();
        if (op == null) return true;

        String content = TextStates.contentOf(text);
        int insertEnd = op.from() + op.inserted().length();
        String reverted = content.substring(0, op.from()) + op.removed() + content.substring(insertEnd);
        TextStates.setContent(text, reverted);

        ts.caretIndex      = op.caretBefore();
        ts.selectionAnchor = op.anchorBefore();
        ts.lastVisualX     = -1f;
        ts.hoverCaretIndex = -1;
        ts.undoStack.flushCoalesce();
        consecutiveClicks = 0;
        lastClickTarget   = null;
        scrollCaretIntoView(text);
        return true;
    }

    public static boolean onRedo() {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.editable()) return false;
        TextState ts = TextStates.of(text);
        UndoStack.EditOp op = ts.undoStack.popRedo();
        if (op == null) return true;

        String content = TextStates.contentOf(text);
        int removedEnd = op.from() + op.removed().length();
        String redone = content.substring(0, op.from()) + op.inserted() + content.substring(removedEnd);
        TextStates.setContent(text, redone);

        ts.caretIndex      = op.caretAfter();
        ts.selectionAnchor = op.anchorAfter();
        ts.lastVisualX     = -1f;
        ts.hoverCaretIndex = -1;
        ts.undoStack.flushCoalesce();
        consecutiveClicks = 0;
        lastClickTarget   = null;
        scrollCaretIntoView(text);
        return true;
    }

    // ---------- clipboard ----------

    public static boolean onCopy(MemorySegment windowHandle) {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.selectable()) return false;
        TextState ts = TextStates.of(text);
        if (!ts.hasSelection()) return true;
        String content = TextStates.contentOf(text);
        String selected = content.substring(ts.selectionStart(), ts.selectionEnd());
        Glfw.glfwSetClipboardString(windowHandle, selected);
        return true;
    }

    public static boolean onCut(MemorySegment windowHandle) {
        if (!(FocusState.focused() instanceof Component.Text text)) return false;
        if (!text.selectable()) return false;
        TextState ts = TextStates.of(text);
        if (!ts.hasSelection()) return true;
        String content = TextStates.contentOf(text);
        String selected = content.substring(ts.selectionStart(), ts.selectionEnd());
        Glfw.glfwSetClipboardString(windowHandle, selected);
        if (text.editable()) {
            replaceRange(text, ts.selectionStart(), ts.selectionEnd(), "", UndoStack.EditKind.OTHER);
        }
        return true;
    }

    public static boolean onPaste(MemorySegment windowHandle) {
        if (!(FocusState.focused() instanceof Component.Text text) || !text.editable()) return false;
        String clip = Glfw.glfwGetClipboardString(windowHandle);
        if (clip == null || clip.isEmpty()) return true;
        insertString(text, clip, UndoStack.EditKind.OTHER);
        return true;
    }

    // ---------- shared edit primitives ----------

    private static void insertString(Component.Text text, String s, UndoStack.EditKind kind) {
        TextState ts = TextStates.of(text);
        if (ts.hasSelection()) {
            // Replacing a selection breaks any prior coalesce group.
            replaceRange(text, ts.selectionStart(), ts.selectionEnd(), s, UndoStack.EditKind.OTHER);
        } else {
            replaceRange(text, ts.caretIndex, ts.caretIndex, s, kind);
        }
    }

    private static void replaceRange(Component.Text text, int from, int to, String replacement, UndoStack.EditKind kind) {
        String content = TextStates.contentOf(text);
        if (from < 0) from = 0;
        if (to > content.length()) to = content.length();

        TextState ts = TextStates.of(text);
        int caretBefore  = ts.caretIndex;
        int anchorBefore = ts.selectionAnchor;
        String removed   = content.substring(from, to);

        String updated = content.substring(0, from) + replacement + content.substring(to);
        TextStates.setContent(text, updated);

        int newCaret = from + replacement.length();
        ts.caretIndex      = newCaret;
        ts.selectionAnchor = newCaret;
        ts.lastVisualX     = -1f;
        ts.hoverCaretIndex = -1;
        // Edits break the multi-click streak — typing then re-clicking
        // shouldn't count as part of an earlier double/triple-click sequence.
        consecutiveClicks = 0;
        lastClickTarget   = null;

        // No-op edits (e.g. backspace at start) don't get a history entry.
        if (!removed.isEmpty() || !replacement.isEmpty()) {
            ts.undoStack.push(new UndoStack.EditOp(
                from, removed, replacement,
                caretBefore, anchorBefore,
                newCaret,    newCaret,
                System.nanoTime(), kind
            ));
        }

        scrollCaretIntoView(text);
    }

    /**
     * Nudge enclosing {@link Component.Scroll} ancestors so the focused text's
     * caret stays inside their viewport. Uses the most recent layout — the
     * text rect hasn't moved between events, and any newly-inserted content
     * grows the rect on the next frame; the worst case is a one-frame lag
     * before the scroll catches up.
     */
    static void scrollCaretIntoView(Component.Text text) {
        Component root = LatestLayout.root();
        LayoutResult lr = LatestLayout.result();
        if (root == null || lr == null) return;
        PixelRect textRect = lr.rectOf(text);
        if (textRect == null) return;
        TextState ts = TextStates.of(text);
        PixelRect caretRect = TextGeometry.caretBounds(text, TextStates.contentOf(text), textRect, ts.caretIndex);

        for (Component ancestor : HitTest.pathTo(root, text)) {
            if (!(ancestor instanceof Component.Scroll scroll)) continue;
            PixelRect outer = lr.rectOf(scroll);
            if (outer == null) continue;
            float pad = scroll.padding().toPixels();
            PixelRect interior = new PixelRect(
                outer.x() + pad, outer.y() + pad,
                Math.max(0f, outer.width()  - 2f * pad),
                Math.max(0f, outer.height() - 2f * pad)
            );

            float dx = 0f, dy = 0f;
            if (caretRect.bottom() > interior.bottom()) dy = caretRect.bottom() - interior.bottom();
            else if (caretRect.y() < interior.y())      dy = caretRect.y()      - interior.y();
            if (caretRect.right() > interior.right())   dx = caretRect.right() - interior.right();
            else if (caretRect.x() < interior.x())      dx = caretRect.x()      - interior.x();

            if (dx != 0f || dy != 0f) ScrollStates.of(scroll).scrollByPx(dx, dy);
        }
    }
}
