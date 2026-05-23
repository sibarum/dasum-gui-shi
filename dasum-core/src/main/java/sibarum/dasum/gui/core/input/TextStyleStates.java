package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Identity-keyed sidecar that holds per-{@link Component.Text Text}
 * foreground and background style ranges. Worker threads publish ranges
 * via {@link #setForeground} / {@link #setBackground} (or
 * {@link #updateForeground} / {@link #updateBackground} for race-free
 * read-modify-write), and the renderer reads the latest snapshot during
 * the next frame.
 *
 * <h2>Threading model</h2>
 * Two-level locking:
 * <ul>
 *   <li>A global {@code LOCK} guards the identity-keyed holder map. It is
 *       only held to look up, create, or remove a per-Text {@code Holder}
 *       (one map operation per call).</li>
 *   <li>Each {@code Holder} is its own monitor for the per-axis publish.
 *       Workers updating different Text components never block each other.</li>
 * </ul>
 * The per-axis range lists themselves are {@code volatile} immutable
 * snapshots, so the renderer reads them lock-free.
 *
 * <p>{@link #setForeground} / {@link #setBackground} always {@link
 * List#copyOf snapshot} their argument, so callers may safely reuse or
 * mutate the source list after the call returns. The argument must be
 * non-null and contain no null elements; use {@link #clearRanges} (or
 * pass {@code List.of()}) to remove all ranges on an axis.
 *
 * <p>{@link #updateForeground} / {@link #updateBackground} run the
 * supplied function inside the per-Holder lock, so the function sees the
 * latest snapshot and its result is published atomically with respect to
 * other style updates on the same Text. The function MUST NOT call back
 * into {@code TextStyleStates} for the same Text — that would deadlock.
 *
 * <p>Stale ranges (indices beyond the live content length, or after the
 * user deletes/inserts text) are silently clipped at render time. The
 * range list itself is not auto-adjusted; callers that need
 * survive-across-edits behavior should subscribe via
 * {@link TextStates#onContentChange} and republish.
 */
public final class TextStyleStates {

    private static final class Holder {
        volatile List<TextStyle> fg = List.of();
        volatile List<TextStyle> bg = List.of();
    }

    private static final Object LOCK = new Object();
    private static final Map<Component, Holder> HOLDERS = new IdentityHashMap<>();

    private TextStyleStates() {}

    // --- read ---

    /**
     * Immutable snapshot of the current foreground ranges. Returns
     * {@link List#of()} if no ranges have ever been published for this
     * Text; does not create a holder, so plain non-styled Texts pay
     * nothing here.
     */
    public static List<TextStyle> foregroundOf(Component.Text text) {
        Holder h;
        synchronized (LOCK) {
            h = HOLDERS.get(text);
        }
        return h == null ? List.of() : h.fg;
    }

    /** @see #foregroundOf */
    public static List<TextStyle> backgroundOf(Component.Text text) {
        Holder h;
        synchronized (LOCK) {
            h = HOLDERS.get(text);
        }
        return h == null ? List.of() : h.bg;
    }

    // --- write ---

    /**
     * Replace the foreground ranges for {@code text}. {@code ranges} is
     * snapshotted via {@link List#copyOf}, so the caller may reuse or
     * mutate the source list afterwards. {@code null} is rejected; use
     * {@link #clearRanges} or pass {@code List.of()} to remove all
     * foreground ranges.
     */
    public static void setForeground(Component.Text text, List<TextStyle> ranges) {
        Objects.requireNonNull(ranges, "ranges");
        List<TextStyle> snap = List.copyOf(ranges);
        Holder h = holder(text);
        synchronized (h) {
            h.fg = snap;
        }
        Invalidator.invalidate();
    }

    /** @see #setForeground */
    public static void setBackground(Component.Text text, List<TextStyle> ranges) {
        Objects.requireNonNull(ranges, "ranges");
        List<TextStyle> snap = List.copyOf(ranges);
        Holder h = holder(text);
        synchronized (h) {
            h.bg = snap;
        }
        Invalidator.invalidate();
    }

    /**
     * Race-free read-modify-write of the foreground ranges. {@code fn}
     * receives the current immutable snapshot and must return the new
     * ranges; the function runs inside the per-Holder lock so concurrent
     * publishers can't cause a lost update. The returned list is
     * snapshotted before being published.
     *
     * <p>The supplied function MUST NOT call back into
     * {@code TextStyleStates} for the same {@code text} — that would
     * deadlock on the per-Holder monitor.
     *
     * @return the new published snapshot
     */
    public static List<TextStyle> updateForeground(Component.Text text,
                                                    UnaryOperator<List<TextStyle>> fn) {
        Objects.requireNonNull(fn, "fn");
        Holder h = holder(text);
        List<TextStyle> next;
        synchronized (h) {
            next = List.copyOf(fn.apply(h.fg));
            h.fg = next;
        }
        Invalidator.invalidate();
        return next;
    }

    /** @see #updateForeground */
    public static List<TextStyle> updateBackground(Component.Text text,
                                                    UnaryOperator<List<TextStyle>> fn) {
        Objects.requireNonNull(fn, "fn");
        Holder h = holder(text);
        List<TextStyle> next;
        synchronized (h) {
            next = List.copyOf(fn.apply(h.bg));
            h.bg = next;
        }
        Invalidator.invalidate();
        return next;
    }

    /**
     * Drop all foreground AND background ranges for {@code text} (but
     * keep the holder in case the caller is about to publish new ranges
     * — cheaper than {@link #clear} when only the contents are being
     * reset). Use {@link #clear} from lifecycle hooks where the
     * component itself is going away.
     */
    public static void clearRanges(Component.Text text) {
        Holder h;
        synchronized (LOCK) {
            h = HOLDERS.get(text);
        }
        if (h == null) return;
        synchronized (h) {
            h.fg = List.of();
            h.bg = List.of();
        }
        Invalidator.invalidate();
    }

    // --- lifecycle (called by Components.detach / migrateState) ---

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops
     * any holder associated with {@code c}.
     */
    public static void clear(Component c) {
        synchronized (LOCK) {
            HOLDERS.remove(c);
        }
    }

    /**
     * Copy {@code from}'s ranges to {@code to}. Same-reference handover —
     * matches {@link TextStates#migrate} semantics. If both Texts then
     * have publishers writing, they share state by design (the
     * {@code with*} record copy is meant to be a logical no-op for state
     * purposes).
     */
    public static void migrate(Component from, Component to) {
        if (from == to) return;
        synchronized (LOCK) {
            Holder h = HOLDERS.get(from);
            if (h != null) HOLDERS.put(to, h);
        }
    }

    // --- internals ---

    private static Holder holder(Component.Text text) {
        synchronized (LOCK) {
            return HOLDERS.computeIfAbsent(text, k -> new Holder());
        }
    }
}
