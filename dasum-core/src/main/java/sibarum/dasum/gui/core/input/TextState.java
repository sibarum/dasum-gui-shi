package sibarum.dasum.gui.core.input;

/**
 * Mutable per-Text editing state. {@code caretIndex} is the character
 * offset of the caret (0..content.length()); {@code selectionAnchor} is
 * the "other end" of the selection — equal to {@code caretIndex} means
 * no selection. {@code lastVisualX} remembers the pixel X the caret
 * wanted on Up/Down arrow movement so the caret can return to the same
 * column when stepping back to a wider line.
 * <p>
 * {@code hoverCaretIndex == -1} means "no phantom caret right now". The
 * phantom is cleared on text change, mouseout, keypress, or focus loss
 * — those resets are driven by the framework, not stored here.
 */
public final class TextState {

    public int caretIndex = 0;
    public int selectionAnchor = 0;
    public float lastVisualX = -1f;
    public int hoverCaretIndex = -1;

    /**
     * Mutable content override. {@code null} means "use the Text record's
     * initial content". Set by editing operations; cleared (back to null)
     * if you want to revert to the record's value.
     */
    public String content = null;

    /** Per-Text edit history for Ctrl+Z / Ctrl+Y. */
    public final UndoStack undoStack = new UndoStack();

    public boolean hasSelection() {
        return caretIndex != selectionAnchor;
    }

    public int selectionStart() {
        return Math.min(caretIndex, selectionAnchor);
    }

    public int selectionEnd() {
        return Math.max(caretIndex, selectionAnchor);
    }

    public void collapseToCaret() {
        selectionAnchor = caretIndex;
    }
}
