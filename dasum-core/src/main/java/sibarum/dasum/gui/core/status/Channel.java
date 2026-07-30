package sibarum.dasum.gui.core.status;

/**
 * The audience axis of a {@link StatusEvent} — one of the three orthogonal
 * axes of the status model ({@link Severity severity}, channel, and the
 * surface/alert flag).
 *
 * <p>Channel classifies who an event is <i>for</i>, independent of how loud it
 * is or whether it surfaces. It exists to feed the (deferred) log filter: a
 * user typically wants to see {@link #USER} entries and hide {@link #TECHNICAL}
 * ones, while a developer flips that. Keeping it orthogonal to severity and
 * surface is what lets the filter slice cleanly later.
 */
public enum Channel {
    /** Meant for the end user — outcomes, confirmations, things they did. */
    USER,
    /** Diagnostic/technical detail — traces, timings, internal state. */
    TECHNICAL
}
