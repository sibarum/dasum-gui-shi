package sibarum.dasum.gui.core.status;

/**
 * One logged entry in the {@link Status} bar. The bar is purely a <b>ledger</b>
 * of what happened, so every entry is recorded; three orthogonal axes decide
 * how it is treated:
 * <ul>
 *   <li>{@link #alert} (the <b>surface</b> axis) — {@code true} briefly shows
 *       the entry on the bar (then it decays to the "N new" counter) and bumps
 *       the unseen count; {@code false} is a quiet history-only note.</li>
 *   <li>{@link #severity} — signed good/neutral/bad, shown as a faint tint on
 *       the transient alert and the history row (never on the idle counter).</li>
 *   <li>{@link #channel} — user-facing vs technical, the axis the (deferred)
 *       log filter slices on.</li>
 * </ul>
 * Plus a one-line {@link #message}, an optional multi-line {@link #details}
 * (shown only in the log popup, {@code null} if absent), and an epoch-millis
 * {@link #timestamp}.
 */
public record StatusEvent(long timestamp, String message, String details,
                          Severity severity, Channel channel, boolean alert) {

    public StatusEvent {
        if (message == null) message = "";
        if (severity == null) severity = Severity.NEUTRAL;
        if (channel == null) channel = Channel.USER;
    }

    public boolean hasDetails() {
        return details != null && !details.isBlank();
    }
}
