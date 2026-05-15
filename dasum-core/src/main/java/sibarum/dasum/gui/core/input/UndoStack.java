package sibarum.dasum.gui.core.input;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-{@link sibarum.dasum.gui.core.component.Component.Text Text} undo/redo
 * history. Each {@link EditOp} captures everything needed to apply and
 * reverse a content change.
 * <p>
 * Coalescing: consecutive same-kind contiguous edits within
 * {@link #COALESCE_NANOS} merge into the previous entry, so typing a word
 * collapses to a single undo step. Pastes, newlines, word-deletes, and
 * any edit involving a selection are treated as standalone entries.
 */
public final class UndoStack {

    public enum EditKind { INSERT_CHAR, DELETE_BACK, DELETE_FORWARD, OTHER }

    public record EditOp(
        int from, String removed, String inserted,
        int caretBefore, int anchorBefore,
        int caretAfter,  int anchorAfter,
        long timeNanos, EditKind kind
    ) {}

    private static final long COALESCE_NANOS = 1_000_000_000L;

    private final List<EditOp> ops = new ArrayList<>();
    private int cursor = 0; // count of currently-applied ops; ops[cursor..] is the redo tail
    private boolean coalesceClosed = false;

    public boolean canUndo() { return cursor > 0; }
    public boolean canRedo() { return cursor < ops.size(); }

    public void push(EditOp op) {
        // Drop the redo tail — any further edit invalidates pending redos.
        while (ops.size() > cursor) ops.remove(ops.size() - 1);

        if (!coalesceClosed && cursor > 0) {
            EditOp prev = ops.get(cursor - 1);
            EditOp merged = tryCoalesce(prev, op);
            if (merged != null) {
                ops.set(cursor - 1, merged);
                return;
            }
        }
        coalesceClosed = false;
        ops.add(op);
        cursor++;
    }

    public EditOp popUndo() {
        if (cursor == 0) return null;
        cursor--;
        return ops.get(cursor);
    }

    public EditOp popRedo() {
        if (cursor >= ops.size()) return null;
        EditOp op = ops.get(cursor);
        cursor++;
        return op;
    }

    /** Force the next {@link #push} to start a fresh entry instead of coalescing. */
    public void flushCoalesce() {
        coalesceClosed = true;
    }

    private static EditOp tryCoalesce(EditOp prev, EditOp next) {
        if (next.timeNanos - prev.timeNanos > COALESCE_NANOS) return null;

        if (prev.kind == EditKind.INSERT_CHAR && next.kind == EditKind.INSERT_CHAR
            && prev.removed.isEmpty() && next.removed.isEmpty()
            && prev.from + prev.inserted.length() == next.from
            && !prev.inserted.endsWith("\n")) {
            return new EditOp(
                prev.from, "", prev.inserted + next.inserted,
                prev.caretBefore, prev.anchorBefore,
                next.caretAfter,  next.anchorAfter,
                next.timeNanos, EditKind.INSERT_CHAR
            );
        }
        if (prev.kind == EditKind.DELETE_BACK && next.kind == EditKind.DELETE_BACK
            && prev.inserted.isEmpty() && next.inserted.isEmpty()
            && next.from + next.removed.length() == prev.from) {
            return new EditOp(
                next.from, next.removed + prev.removed, "",
                prev.caretBefore, prev.anchorBefore,
                next.caretAfter,  next.anchorAfter,
                next.timeNanos, EditKind.DELETE_BACK
            );
        }
        if (prev.kind == EditKind.DELETE_FORWARD && next.kind == EditKind.DELETE_FORWARD
            && prev.inserted.isEmpty() && next.inserted.isEmpty()
            && next.from == prev.from) {
            return new EditOp(
                prev.from, prev.removed + next.removed, "",
                prev.caretBefore, prev.anchorBefore,
                next.caretAfter,  next.anchorAfter,
                next.timeNanos, EditKind.DELETE_FORWARD
            );
        }
        return null;
    }
}
