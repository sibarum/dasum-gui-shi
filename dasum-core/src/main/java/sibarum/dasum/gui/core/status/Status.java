package sibarum.dasum.gui.core.status;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.DynamicChildren;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.Anchor;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.theme.Palette;
import sibarum.dasum.gui.core.theme.Theme;
import sibarum.dasum.gui.core.theme.Variant;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Singleton status ribbon at the bottom of the viewport.
 *
 * <p>Two display modes:
 * <ul>
 *   <li><b>Idle</b> — shows the configured default message (typically a
 *       contextual keyboard-shortcut tip), tinted with the default
 *       variant.</li>
 *   <li><b>Event active</b> — most-recent {@link #log} call's message,
 *       tinted with that event's variant. Auto-reverts to idle after
 *       {@value #MESSAGE_DURATION_MS} ms.</li>
 * </ul>
 *
 * <p>Every {@link #log} call appends a {@link StatusEvent} to a session-
 * long history. Clicking the ribbon (when no other overlay is active)
 * opens a modal popup with a selectable textarea of every event so far,
 * scrolled to the bottom. The popup is dismissed by clicking outside or
 * pressing ESC.
 *
 * <p>Apps install the ribbon by wrapping their root component:
 * <pre>{@code
 *   Component root = Status.wrap(MainShell.build());
 * }</pre>
 *
 * <p>Threading: {@link #log} is safe to call from any thread. The
 * scheduled revert runs on an internal daemon thread; mutations of the
 * visual happen via {@link DynamicChildren} which is not thread-safe,
 * so display updates from off-thread are conservative — a value
 * Property change drives the update from a single subscriber, accessed
 * under a static lock. {@link Invalidator#invalidate} is thread-safe
 * by design.
 */
public final class Status {

    public static final long MESSAGE_DURATION_MS = 6000L;
    /** Maximum events kept in the in-memory history. Older events drop off the front. */
    public static final int MAX_HISTORY = 1000;

    private static final Em RIBBON_HEIGHT_EM = Em.of(1.7f);
    /** Fraction of the viewport the log dialog occupies when one is open. */
    private static final float LOG_DIALOG_W_FRACTION = 0.80f;
    private static final float LOG_DIALOG_H_FRACTION = 0.70f;
    /** Clamps so the dialog stays readable on tiny windows and reasonable on huge ones. */
    private static final float LOG_DIALOG_MIN_W_EM = 28f;
    private static final float LOG_DIALOG_MIN_H_EM = 14f;
    private static final float LOG_DIALOG_MAX_W_EM = 120f;
    private static final float LOG_DIALOG_MAX_H_EM = 60f;
    /** Reserve some em of the dialog's width for padding when wrapping the textarea. */
    private static final float LOG_DIALOG_PADDING_EM = 2f;
    /** Used only if no layout has been computed yet (pre-first-frame click — shouldn't happen). */
    private static final Em LOG_DIALOG_FALLBACK_W = Em.of(60f);
    private static final Em LOG_DIALOG_FALLBACK_H = Em.of(28f);
    private static final Color RIBBON_BG       = new Color(0.07f, 0.09f, 0.12f, 1f);
    private static final Color RIBBON_BG_HOVER = new Color(0.10f, 0.12f, 0.16f, 1f);
    private static final Color DIALOG_BG       = new Color(0.10f, 0.12f, 0.16f, 1f);
    private static final Color DIALOG_BORDER   = new Color(0.30f, 0.55f, 0.85f, 0.9f);
    private static final Color LABEL_FG        = new Color(0.92f, 0.94f, 0.97f, 1f);
    private static final Color HINT_FG         = new Color(0.65f, 0.70f, 0.78f, 0.85f);

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // ----- session state -----
    private static final Object LOCK = new Object();
    private static final List<StatusEvent> HISTORY = new ArrayList<>();
    private static final List<Consumer<StatusEvent>> LISTENERS = new ArrayList<>();
    private static StatusEvent activeEvent = null;
    private static String  defaultMessage = "";
    private static Variant defaultVariant = Variant.DEFAULT;
    private static final AtomicLong eventCounter = new AtomicLong(0L);

    // ----- visuals (lazily built by wrap) -----
    private static Component.Flex ribbon = null;
    private static boolean wrapped = false;

    // ----- timer for auto-revert -----
    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dasum-status-timer");
            t.setDaemon(true);
            return t;
        });

    private Status() {}

    // ---------- public API ----------

    /**
     * Wrap an app's root component with the status ribbon at the bottom.
     * Returns a {@link Component.Flex Flex(COLUMN)} whose first child is
     * {@code content} (with flexGrow=1) and second child is the ribbon.
     * The ribbon's identity is stable for the lifetime of the JVM —
     * {@link Handlers#onClick click handling} and content updates via
     * {@link DynamicChildren} preserve it.
     *
     * <p>Call once at startup. Subsequent calls return a fresh wrapper
     * around {@code content} re-using the same singleton ribbon, but
     * apps that use {@code Status} typically only need one root.
     */
    public static Component wrap(Component content) {
        ensureRibbon();
        Component grown = grow(content);
        return new Component.Flex(
            null, null, Em.ZERO, RIBBON_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(grown, ribbon),
            false, 0
        );
    }

    /**
     * Set the message shown when no event is active. Typical use: a
     * contextual keyboard-shortcut hint that changes when focus moves.
     */
    public static void setDefaultMessage(String text) {
        setDefaultMessage(text, Variant.DEFAULT);
    }

    public static void setDefaultMessage(String text, Variant variant) {
        synchronized (LOCK) {
            defaultMessage = text == null ? "" : text;
            defaultVariant = variant == null ? Variant.DEFAULT : variant;
            if (activeEvent == null) refresh();
        }
    }

    /** Log + display an event. Variant defaults to {@link Variant#DEFAULT}. */
    public static StatusEvent log(String message) {
        return log(message, null, Variant.DEFAULT);
    }

    public static StatusEvent log(String message, Variant variant) {
        return log(message, null, variant);
    }

    /**
     * Log + display an event. Returns the recorded {@link StatusEvent}
     * (timestamp = {@link System#currentTimeMillis}).
     *
     * <p>Thread-safe. The ribbon updates synchronously under a static
     * lock; the auto-revert is scheduled on a daemon thread and woken
     * the next frame via {@link Invalidator#invalidate}.
     */
    public static StatusEvent log(String message, String details, Variant variant) {
        StatusEvent e = new StatusEvent(System.currentTimeMillis(), message, details, variant);
        long id;
        synchronized (LOCK) {
            HISTORY.add(e);
            while (HISTORY.size() > MAX_HISTORY) HISTORY.remove(0);
            activeEvent = e;
            id = eventCounter.incrementAndGet();
            refresh();
        }
        // Snapshot listener list to invoke outside the lock.
        List<Consumer<StatusEvent>> snap;
        synchronized (LOCK) {
            snap = List.copyOf(LISTENERS);
        }
        for (Consumer<StatusEvent> l : snap) l.accept(e);
        SCHEDULER.schedule(() -> tryRevert(id), MESSAGE_DURATION_MS, TimeUnit.MILLISECONDS);
        return e;
    }

    /** Convenience: log at INFO variant. */
    public static StatusEvent info(String message)    { return log(message, null, Variant.INFO); }
    /** Convenience: log at SUCCESS variant. */
    public static StatusEvent success(String message) { return log(message, null, Variant.SUCCESS); }
    /** Convenience: log at WARNING variant. */
    public static StatusEvent warn(String message)    { return log(message, null, Variant.WARNING); }
    /** Convenience: log at ERROR variant. */
    public static StatusEvent error(String message)   { return log(message, null, Variant.ERROR); }

    public static StatusEvent error(String message, String details) {
        return log(message, details, Variant.ERROR);
    }

    /** Clear the active event immediately (revert to idle). */
    public static void clearMessage() {
        synchronized (LOCK) {
            if (activeEvent == null) return;
            activeEvent = null;
            eventCounter.incrementAndGet(); // invalidate any pending revert
            refresh();
        }
    }

    /** Snapshot of session event history, oldest first. Safe to read from any thread. */
    public static List<StatusEvent> events() {
        synchronized (LOCK) {
            return List.copyOf(HISTORY);
        }
    }

    /**
     * Subscribe to every {@link #log} call. Listeners are invoked
     * outside the internal lock so they may safely call other
     * {@code Status} methods.
     */
    public static void subscribe(Consumer<StatusEvent> listener) {
        synchronized (LOCK) {
            LISTENERS.add(listener);
        }
    }

    public static StatusEvent activeEvent() {
        synchronized (LOCK) { return activeEvent; }
    }

    // ---------- internals ----------

    private static Component grow(Component content) {
        // Force flexGrow=1 on the user's root so the ribbon hugs the bottom.
        if (content instanceof Component.Flex f) return f.withFlexGrow(1);
        if (content instanceof Component.Box b) return b.withFlexGrow(1);
        // For other variants we can't directly grow — wrap in a Flex.
        return new Component.Flex(
            null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(content), false, 1);
    }

    private static void ensureRibbon() {
        if (ribbon != null) return;
        ribbon = new Component.Flex(
            null, RIBBON_HEIGHT_EM, Em.of(0.4f), RIBBON_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(), true, 0);
        Handlers.onClick(ribbon, Status::onRibbonClicked);
        refresh();
    }

    /** Rebuild the ribbon's content under {@link #LOCK}. */
    private static void refresh() {
        if (ribbon == null) return;
        DynamicChildren.clearChildren(ribbon);
        StatusEvent ev = activeEvent;
        String text;
        Variant variant;
        if (ev != null) {
            text = "[" + ev.variant().name() + "] " + ev.message();
            variant = ev.variant();
        } else {
            text = defaultMessage;
            variant = defaultVariant;
        }
        if (text != null && !text.isEmpty()) {
            DynamicChildren.add(ribbon, buildRibbonText(text, variant));
        }
        // Always trail with a faded "click for log" hint when no event is
        // active and the default text isn't already serving that purpose —
        // discoverable signal that the bar is clickable.
        if (ev == null && (text == null || text.isEmpty())) {
            DynamicChildren.add(ribbon, buildRibbonText(
                "Click for event log", Variant.DEFAULT));
        }
        Invalidator.invalidate();
    }

    private static Component buildRibbonText(String text, Variant variant) {
        Palette p = Theme.of(variant);
        Color fg = variant == Variant.DEFAULT ? LABEL_FG : p.emphasis();
        return new Component.Text(
            text, FontGroups.DEFAULT, Em.of(0.9f), fg,
            null, null, Em.ZERO, null, true,
            false, false, false, false, 0);
    }

    private static void tryRevert(long id) {
        synchronized (LOCK) {
            if (eventCounter.get() != id) return;  // a newer event arrived
            if (activeEvent == null) return;
            activeEvent = null;
            refresh();
        }
    }

    private static void onRibbonClicked() {
        // Suppress when any overlay is active so the logs popup doesn't
        // stack on top of a modal dialog or command palette.
        if (OverlayStack.isActive()) return;
        openLogs();
    }

    private static void openLogs() {
        Em dialogW;
        Em dialogH;
        Em wrapW;
        LayoutResult lr = LatestLayout.result();
        Component appRoot = LatestLayout.root();
        if (lr != null && appRoot != null && EmContext.pixelsPerEm() > 0f) {
            PixelRect viewport = lr.rectOf(appRoot);
            if (viewport != null && viewport.width() > 0f && viewport.height() > 0f) {
                float ppe = EmContext.pixelsPerEm();
                float emW = viewport.width()  / ppe;
                float emH = viewport.height() / ppe;
                float w = clamp(emW * LOG_DIALOG_W_FRACTION, LOG_DIALOG_MIN_W_EM, LOG_DIALOG_MAX_W_EM);
                // Hard cap to the viewport so we never produce a dialog that
                // overflows; the OverlayStack would clamp anyway but a too-large
                // intrinsic size still hurts internal layout (caret math, scroll).
                w = Math.min(w, emW - 0.5f);
                float h = clamp(emH * LOG_DIALOG_H_FRACTION, LOG_DIALOG_MIN_H_EM, LOG_DIALOG_MAX_H_EM);
                h = Math.min(h, emH - 0.5f);
                dialogW = Em.of(w);
                dialogH = Em.of(h);
                wrapW   = Em.of(Math.max(LOG_DIALOG_MIN_W_EM - LOG_DIALOG_PADDING_EM,
                                         w - LOG_DIALOG_PADDING_EM));
            } else {
                dialogW = LOG_DIALOG_FALLBACK_W;
                dialogH = LOG_DIALOG_FALLBACK_H;
                wrapW   = Em.of(LOG_DIALOG_FALLBACK_W.value() - LOG_DIALOG_PADDING_EM);
            }
        } else {
            dialogW = LOG_DIALOG_FALLBACK_W;
            dialogH = LOG_DIALOG_FALLBACK_H;
            wrapW   = Em.of(LOG_DIALOG_FALLBACK_W.value() - LOG_DIALOG_PADDING_EM);
        }

        Component textarea = buildLogsTextArea(wrapW);
        Component scroll = new Component.Scroll(
            dialogW, dialogH, Em.of(0.6f), DIALOG_BG,
            textarea, false, 0);
        Component header = new Component.Text("Event Log", Em.of(1.15f), LABEL_FG);
        Component hint   = new Component.Text(
            "click outside or press ESC to dismiss", Em.of(0.85f), HINT_FG);
        Component dialog = new Component.Flex(
            dialogW, Em.AUTO, Em.of(1.0f), DIALOG_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.5f),
            List.of(header, hint, scroll), false, 0);
        Component framed = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.125f), DIALOG_BORDER,
            Direction.COLUMN, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(dialog), false, 0);
        OverlayStack.push(new OverlayStack.Overlay(
            framed, Anchor.CENTER, true, () -> {}));
        // Scroll to bottom — bounds-clamped by the Scroll viewport on the
        // next layout pass.
        sibarum.dasum.gui.core.input.ScrollStates.of(scroll).scrollByPx(0f, 1_000_000f);
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /**
     * Build a single selectable Text containing every event in history,
     * one row per event, with optional details indented underneath.
     */
    private static Component buildLogsTextArea(Em wrapWidth) {
        StringBuilder sb = new StringBuilder();
        List<StatusEvent> snapshot;
        synchronized (LOCK) {
            snapshot = List.copyOf(HISTORY);
        }
        if (snapshot.isEmpty()) {
            sb.append("(no events yet)");
        } else {
            for (StatusEvent e : snapshot) {
                sb.append(TIME_FMT.format(Instant.ofEpochMilli(e.timestamp())))
                  .append("  [")
                  .append(e.variant().name())
                  .append("] ")
                  .append(e.message())
                  .append('\n');
                if (e.hasDetails()) {
                    for (String line : e.details().split("\\R")) {
                        sb.append("    ").append(line).append('\n');
                    }
                }
            }
        }
        return new Component.Text(
            sb.toString(), FontGroups.DEFAULT, Em.of(0.9f), LABEL_FG,
            null, null, Em.of(0.5f),
            wrapWidth,      // wrap to dialog width (passed in from openLogs)
            true,           // clip to viewport
            true, true, false, false, 0
        );
    }
}
