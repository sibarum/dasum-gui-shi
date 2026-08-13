package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Identity-keyed sidecar holding the <em>runtime</em> word-wrap state for a
 * {@link Component.Text Text}: whether soft-wrap is toggled on, and the
 * content-box width (in screen pixels) recorded by the last layout pass so
 * "wrap to my own width" has a concrete target.
 *
 * <h2>Why this exists — the single source of truth for the wrap width</h2>
 * Every consumer that maps between text offsets and screen positions
 * ({@link TextMetrics#lines}, and through it {@link TextGeometry} and the
 * renderer's glyph loop) independently re-derives the visual line breaks.
 * They stay in agreement only if they all resolve the <em>same</em> wrap
 * width. The record's fixed {@link Component.Text#wrapWidth()} guaranteed
 * that trivially; a runtime "wrap to the editor's own width" toggle would
 * not, unless the width is resolved from one place. {@link #effectiveWrapPx}
 * is that place: it takes only the {@code Text} instance, so no call site
 * has to thread a rect through, and none can disagree about where lines
 * break. That is what keeps foreground/background style spans, the caret,
 * and hit-testing aligned across a re-wrap.
 *
 * <h2>Frame ordering</h2>
 * {@link #recordContentWidth} is written by {@code Layout} while it assigns
 * each Text its rect; layout runs before render and before the next input
 * frame, so the value the renderer and geometry read is the one produced by
 * the layout that positioned the very glyphs on screen. On the first frame a
 * wrapped field appears (or the frame its width changes) the recorded width
 * lags by one pass, but because <em>all</em> consumers read the same value
 * that frame, they never disagree with each other — the only effect is that
 * the frame wraps to the previous width, corrected on the next pass. It does
 * not fire {@link Invalidator}: it is an observation of the layout in
 * progress, not a state change.
 *
 * <p>Threading mirrors the other text sidecars: a single lock around the
 * identity map, released before {@link Invalidator#invalidate}.
 */
public final class TextWrapStates {

    private static final Object LOCK = new Object();
    private static final Map<Component, Boolean> WRAP = new IdentityHashMap<>();
    private static final Map<Component, Float> CONTENT_WIDTH = new IdentityHashMap<>();

    private TextWrapStates() {}

    // ---- runtime toggle ----

    /** Whether runtime word-wrap is on for {@code text} (default {@code false}). */
    public static boolean isWordWrap(Component.Text text) {
        synchronized (LOCK) {
            return Boolean.TRUE.equals(WRAP.get(text));
        }
    }

    /**
     * Turn runtime word-wrap on or off and invalidate so the next layout
     * re-breaks lines. Seamless: caret/selection are character offsets and
     * survive the re-wrap untouched; the enclosing {@code Scroll} re-clamps
     * its offset on the next frame.
     */
    public static void setWordWrap(Component.Text text, boolean on) {
        boolean changed;
        synchronized (LOCK) {
            boolean cur = Boolean.TRUE.equals(WRAP.get(text));
            changed = cur != on;
            if (changed) {
                if (on) WRAP.put(text, Boolean.TRUE);
                else    WRAP.remove(text);
            }
        }
        if (changed) Invalidator.invalidate();
    }

    /** Flip the runtime word-wrap flag and return the new value. */
    public static boolean toggleWordWrap(Component.Text text) {
        boolean next = !isWordWrap(text);
        setWordWrap(text, next);
        return next;
    }

    // ---- layout-recorded content-box width ----

    /**
     * Record the content-box width (rect width minus horizontal padding and
     * the line-number gutter, in screen pixels) that the current layout gave
     * {@code text}. Called by {@code Layout}; not a user-facing API and does
     * not invalidate.
     */
    public static void recordContentWidth(Component.Text text, float contentBoxPx) {
        synchronized (LOCK) {
            CONTENT_WIDTH.put(text, contentBoxPx);
        }
    }

    // ---- resolution (the single source of truth) ----

    /**
     * The wrap width in pixels every line-break computation must use, or
     * {@code null} for "split on {@code '\n'} only". Resolution order:
     * <ol>
     *   <li>runtime word-wrap on AND a positive content width recorded →
     *       that width (wrap to the editor's own laid-out width);</li>
     *   <li>otherwise the record's fixed {@link Component.Text#wrapWidth()}
     *       (converted to px), or {@code null} if unset.</li>
     * </ol>
     * The fixed-{@code wrapWidth} path is byte-for-byte the pre-toggle
     * behaviour, so non-wrapping and statically-wrapped Texts are unchanged.
     */
    public static Float effectiveWrapPx(Component.Text text) {
        boolean on;
        Float recorded;
        synchronized (LOCK) {
            on = Boolean.TRUE.equals(WRAP.get(text));
            recorded = CONTENT_WIDTH.get(text);
        }
        if (on && recorded != null && recorded > 0f) return recorded;
        return text.wrapWidth() != null ? text.wrapWidth().toPixels() : null;
    }

    // ---- lifecycle (called by Components.detach / migrateState) ----

    /** Drop any wrap state for {@code c}. */
    public static void clear(Component c) {
        synchronized (LOCK) {
            WRAP.remove(c);
            CONTENT_WIDTH.remove(c);
        }
    }

    /** Copy {@code from}'s wrap state to {@code to} (matches the other sidecars' handover). */
    public static void migrate(Component from, Component to) {
        if (from == to) return;
        synchronized (LOCK) {
            Boolean w = WRAP.get(from);
            if (w != null) WRAP.put(to, w);
            Float cw = CONTENT_WIDTH.get(from);
            if (cw != null) CONTENT_WIDTH.put(to, cw);
        }
    }
}
