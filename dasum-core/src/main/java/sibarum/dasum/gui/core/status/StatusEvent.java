package sibarum.dasum.gui.core.status;

import sibarum.dasum.gui.core.theme.Variant;

/**
 * One logged event in the {@link Status} ribbon. Carries:
 * <ul>
 *   <li>A one-line {@link #message} — the headline shown in the bar.</li>
 *   <li>An optional multi-line {@link #details} field — shown only in
 *       the logs popup. {@code null} if absent.</li>
 *   <li>A {@link Variant} — controls the bar's tint while the event is
 *       active and the row's color in the logs popup.</li>
 *   <li>An epoch-millis {@link #timestamp} — used in the logs view.</li>
 * </ul>
 */
public record StatusEvent(long timestamp, String message, String details, Variant variant) {

    public StatusEvent {
        if (message == null) message = "";
        if (variant == null) variant = Variant.DEFAULT;
    }

    public boolean hasDetails() {
        return details != null && !details.isBlank();
    }
}
