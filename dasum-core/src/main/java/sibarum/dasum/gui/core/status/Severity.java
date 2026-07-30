package sibarum.dasum.gui.core.status;

/**
 * The signed severity axis of a {@link StatusEvent} — one of the three
 * orthogonal axes of the status model (severity, {@link Channel channel},
 * and the surface/alert flag).
 *
 * <p>Severity drives only a <b>faint</b> colour tint (see {@code Status}'s
 * tinting): {@link #GOOD} nudges toward the theme's success shade, {@link #BAD}
 * toward its error shade, {@link #NEUTRAL} stays at the base text colour. The
 * tint is calibrated to be pre-attentive but not attention-<i>capturing</i> —
 * readable to someone who glances over primed for it, invisible to someone
 * mid-thought. It never saturates, animates, or fills a background.
 */
public enum Severity {
    /** A positive outcome — a faint nudge toward the success shade. */
    GOOD,
    /** No valence — plain base text colour. */
    NEUTRAL,
    /** A negative outcome — a faint nudge toward the error shade. */
    BAD
}
