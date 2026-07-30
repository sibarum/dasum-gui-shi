package sibarum.dasum.gui.core.status;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.DynamicChildren;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.overlay.Anchor;
import sibarum.dasum.gui.core.text.Icon;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.theme.Theme;
import sibarum.dasum.gui.core.theme.Variant;
import sibarum.dasum.gui.core.ui.Ui;

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
 * Singleton status ribbon at the bottom of the viewport — a pure <b>ledger</b>
 * of what happened, deliberately NOT a notifications surface.
 *
 * <p>Every call records a {@link StatusEvent} in a session-long history. Three
 * orthogonal axes on the event decide its treatment (see {@link StatusEvent}):
 * <ul>
 *   <li><b>surface</b> ({@code alert}) — an <b>alert</b> ({@link #alert},
 *       {@link #good}, {@link #bad}, {@link #notify}) briefly shows on the bar,
 *       then decays back to idle after {@value #MESSAGE_DURATION_MS} ms, and
 *       increments the unseen-alert counter. A plain {@link #log} /
 *       {@link #technical} entry is history-only: it never touches the bar and
 *       never bumps the counter.</li>
 *   <li><b>severity</b> — a <i>faint</i>, static, theme-aware tint on the
 *       transient alert and the history row. Never on the idle counter, never
 *       a background fill or icon: the resting bar is calm regardless of what
 *       it contains.</li>
 *   <li><b>channel</b> — user vs technical, for the (deferred) log filter.</li>
 * </ul>
 *
 * <p><b>Idle state</b> is a quiet "<i>N</i> new" count of alerts logged since
 * they were last seen — no colour, no icon, no flash on increment. Clicking
 * the ribbon opens the full history and marks everything {@linkplain #markSeen
 * seen} (the pull-gesture <i>is</i> the acknowledgment), zeroing the count.
 *
 * <p>Apps install the ribbon by wrapping their root: {@code Status.wrap(root)}.
 *
 * <p>Threading: every log method is safe to call from any thread. The revert
 * runs on a daemon timer; display mutations go through {@link DynamicChildren}
 * under a static lock and wake the loop via {@link Invalidator}.
 */
public final class Status {

    public static final long MESSAGE_DURATION_MS = 6000L;
    /** Maximum events kept in the in-memory history. Older events drop off the front. */
    public static final int MAX_HISTORY = 1000;

    private static final Em RIBBON_HEIGHT_EM = Em.of(1.7f);
    /** Max dialog size on large viewports — past this, the dialog stops growing and centers. */
    private static final Em LOG_DIALOG_MAX_W = Em.of(120f);
    private static final Em LOG_DIALOG_MAX_H = Em.of(60f);
    /** Space between the viewport edge and the dialog edge — the "minus margins" the user sees. */
    private static final Em LOG_DIALOG_VIEWPORT_MARGIN = Em.of(1.5f);
    /** Approximate wrap width for the log textarea — see {@link #buildLogsTextArea}. */
    private static final Em LOG_DIALOG_WRAP_WIDTH = Em.of(LOG_DIALOG_MAX_W.value() - 4f);
    private static final Color RIBBON_BG       = new Color(0.07f, 0.09f, 0.12f, 1f);
    private static final Color DIALOG_BG       = new Color(0.10f, 0.12f, 0.16f, 1f);
    private static final Color DIALOG_BORDER   = new Color(0.30f, 0.55f, 0.85f, 0.9f);
    private static final Color LABEL_FG        = new Color(0.92f, 0.94f, 0.97f, 1f);
    /** Muted neutral for the idle counter + affordance — calm, non-nagging. */
    private static final Color HINT_FG         = new Color(0.65f, 0.70f, 0.78f, 0.85f);
    /** How far a GOOD/BAD tint nudges from the base text colour toward the theme shade.
     *  Small on purpose: pre-attentive, not attention-capturing. */
    private static final float FAINT_MIX       = 0.45f;

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // ----- session state -----
    private static final Object LOCK = new Object();
    private static final List<StatusEvent> HISTORY = new ArrayList<>();
    private static final List<Consumer<StatusEvent>> LISTENERS = new ArrayList<>();
    /** The alert currently shown on the bar (null = idle). Only alerts are ever active. */
    private static StatusEvent activeEvent = null;
    /** Alerts logged since the history was last opened/seen. The idle "N new" count. */
    private static int newAlertCount = 0;
    private static final AtomicLong eventCounter = new AtomicLong(0L);

    // ----- visuals (lazily built by wrap) -----
    private static Component.Flex ribbon = null;
    /** The single content zone; refresh() clears + repopulates it (stable identity). */
    private static Component.Flex contentZone = null;

    // ----- close-icon registration -----
    private static int    closeIconCodepoint = 0;
    private static String closeIconFontGroup = Icon.DEFAULT_FONT_GROUP;

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
     * {@code content} (grown to fill) and second child is the ribbon. Call once
     * at startup; the ribbon's identity is stable for the JVM's lifetime.
     */
    public static Component wrap(Component content) {
        ensureRibbon();
        Component grown = grow(content);
        return new Component.Flex(
            null, null, Em.ZERO, RIBBON_BG,
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(grown, ribbon),
            false, 0
        );
    }

    // --- history-only entries (the quiet ledger: no surface, no counter bump) ---

    /** Record a neutral, user-channel note in the history only. */
    public static StatusEvent log(String message) {
        return record(message, null, Severity.NEUTRAL, Channel.USER, false);
    }

    public static StatusEvent log(String message, String details) {
        return record(message, details, Severity.NEUTRAL, Channel.USER, false);
    }

    /** Record a technical-channel note in the history only (filtered out of the user view). */
    public static StatusEvent technical(String message) {
        return record(message, null, Severity.NEUTRAL, Channel.TECHNICAL, false);
    }

    public static StatusEvent technical(String message, String details) {
        return record(message, details, Severity.NEUTRAL, Channel.TECHNICAL, false);
    }

    // --- alerts (briefly surface on the bar + bump the "N new" counter; USER channel) ---

    /** Surface an alert of the given severity. */
    public static StatusEvent alert(String message, Severity severity) {
        return record(message, null, severity, Channel.USER, true);
    }

    public static StatusEvent alert(String message, String details, Severity severity) {
        return record(message, details, severity, Channel.USER, true);
    }

    /** Alert: a positive outcome (faint good tint). */
    public static StatusEvent good(String message)   { return alert(message, Severity.GOOD); }
    /** Alert: a negative outcome (faint bad tint). */
    public static StatusEvent bad(String message)    { return alert(message, Severity.BAD); }
    public static StatusEvent bad(String message, String details) {
        return alert(message, details, Severity.BAD);
    }
    /** Alert: neutral — surfaces briefly but carries no valence tint. */
    public static StatusEvent notify(String message) { return alert(message, Severity.NEUTRAL); }

    /** Clear the active alert immediately (revert to idle). Does not change the unseen count. */
    public static void clearMessage() {
        synchronized (LOCK) {
            if (activeEvent == null) return;
            activeEvent = null;
            eventCounter.incrementAndGet(); // invalidate any pending revert
            refresh();
        }
    }

    /** The number of alerts logged since the history was last seen (the idle counter). */
    public static int newAlertCount() {
        synchronized (LOCK) { return newAlertCount; }
    }

    /**
     * Mark every alert seen and zero the counter — the acknowledgment gesture.
     * Called automatically when the log popup opens; exposed so an app can
     * acknowledge through its own affordance too.
     */
    public static void markSeen() {
        synchronized (LOCK) {
            if (newAlertCount == 0) return;
            newAlertCount = 0;
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
     * Subscribe to every recorded event (alerts and history-only alike).
     * Listeners run outside the internal lock, so they may call other
     * {@code Status} methods safely.
     */
    public static void subscribe(Consumer<StatusEvent> listener) {
        synchronized (LOCK) {
            LISTENERS.add(listener);
        }
    }

    /** The alert currently displayed on the bar, or null when idle. */
    public static StatusEvent activeEvent() {
        synchronized (LOCK) { return activeEvent; }
    }

    /**
     * Register a codepoint from the default {@code "icons"} font group for the
     * log dialog's close button. Until set, the button falls back to ASCII "X".
     */
    public static void setCloseIcon(int codepoint) {
        setCloseIcon(codepoint, Icon.DEFAULT_FONT_GROUP);
    }

    public static void setCloseIcon(int codepoint, String fontGroup) {
        closeIconCodepoint = codepoint;
        closeIconFontGroup = fontGroup == null ? Icon.DEFAULT_FONT_GROUP : fontGroup;
    }

    // ---------- internals ----------

    /**
     * The single record path. Appends to history and fires listeners always;
     * only an {@code alert} touches the bar (sets the active event, bumps the
     * unseen counter, schedules the revert).
     */
    private static StatusEvent record(String message, String details,
                                      Severity severity, Channel channel, boolean alert) {
        StatusEvent e = new StatusEvent(
            System.currentTimeMillis(), message, details, severity, channel, alert);
        long id = 0L;
        synchronized (LOCK) {
            HISTORY.add(e);
            while (HISTORY.size() > MAX_HISTORY) HISTORY.remove(0);
            if (alert) {
                activeEvent = e;
                newAlertCount++;
                id = eventCounter.incrementAndGet();
                refresh();
            }
        }
        List<Consumer<StatusEvent>> snap;
        synchronized (LOCK) {
            snap = List.copyOf(LISTENERS);
        }
        for (Consumer<StatusEvent> l : snap) l.accept(e);
        if (alert) {
            final long revertId = id;
            SCHEDULER.schedule(() -> tryRevert(revertId), MESSAGE_DURATION_MS, TimeUnit.MILLISECONDS);
        }
        return e;
    }

    private static Component grow(Component content) {
        if (content instanceof Component.Flex f) return f.withFlexGrow(1);
        if (content instanceof Component.Box b) return b.withFlexGrow(1);
        return new Component.Flex(
            null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(content), false, 1);
    }

    private static void ensureRibbon() {
        if (ribbon != null) return;
        contentZone = (Component.Flex) Ui.row()
            .grow(1).gap(Em.of(0.5f)).justify(JustifyContent.START).align(AlignItems.CENTER)
            .build();
        ribbon = (Component.Flex) Ui.row()
            .height(RIBBON_HEIGHT_EM).padding(Em.of(0.4f)).background(RIBBON_BG)
            .justify(JustifyContent.START).align(AlignItems.CENTER)
            .interactive(true)
            .add(contentZone)
            .build();
        Handlers.onClick(ribbon, Status::onRibbonClicked);
        refresh();
    }

    /**
     * Rebuild the content zone under {@link #LOCK}. Active alert → its message,
     * faintly tinted by severity. Idle → "N new" (muted neutral) or, when the
     * count is zero, a faint clickable affordance. Never a background or icon.
     */
    private static void refresh() {
        if (contentZone == null) return;
        DynamicChildren.clearChildren(contentZone);
        StatusEvent ev = activeEvent;
        if (ev != null) {
            DynamicChildren.add(contentZone, buildMessageText(ev.message(), faint(ev.severity())));
        } else if (newAlertCount > 0) {
            DynamicChildren.add(contentZone, buildMessageText(newAlertCount + " new", HINT_FG));
        } else {
            DynamicChildren.add(contentZone, buildMessageText("Event log", HINT_FG));
        }
        Invalidator.invalidate();
    }

    /** A faint, static, theme-aware tint for a severity — a small nudge from the base text colour. */
    private static Color faint(Severity s) {
        if (s == null || s == Severity.NEUTRAL) return LABEL_FG;
        Variant v = s == Severity.GOOD ? Variant.SUCCESS : Variant.ERROR;
        return mix(LABEL_FG, Theme.of(v).emphasis(), FAINT_MIX);
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
            a.r() + (b.r() - a.r()) * t,
            a.g() + (b.g() - a.g()) * t,
            a.b() + (b.b() - a.b()) * t,
            a.a() + (b.a() - a.a()) * t);
    }

    /** A flexible, truncating ribbon label in the given colour. */
    private static Component buildMessageText(String text, Color fg) {
        return new Component.Text(
            text, FontGroups.DEFAULT, Em.of(0.9f), fg,
            null, null, Em.ZERO, null, true,
            true, false,   // ellipsize, lineNumbers
            false, false, false, false, 1);  // interactive/selectable/editable/acceptsTab, grow=1
    }

    private static void tryRevert(long id) {
        synchronized (LOCK) {
            if (eventCounter.get() != id) return;  // a newer alert arrived
            if (activeEvent == null) return;
            activeEvent = null;
            refresh();
        }
    }

    private static void onRibbonClicked() {
        // Suppress when any overlay is active so the log popup doesn't stack on
        // top of a modal dialog or command palette.
        if (OverlayStack.isActive()) return;
        markSeen();     // opening the log IS the acknowledgment — zero the counter
        openLogs();
    }

    private static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);

    private static void openLogs() {
        Component textarea = buildLogsTextArea(LOG_DIALOG_WRAP_WIDTH);
        Component scroll = new Component.Scroll(
            null, null, Em.of(0.6f), DIALOG_BG,
            textarea, false, 1);
        Component title     = new Component.Text("Event Log", Em.of(1.15f), LABEL_FG);
        Component closeGlyph = closeIconCodepoint != 0
            ? Icon.of(closeIconCodepoint, closeIconFontGroup, Em.of(1.0f), LABEL_FG)
            : new Component.Text("X", Em.of(1.0f), LABEL_FG);
        Component closeBtn  = new Component.Flex(
            Em.of(1.6f), Em.of(1.6f), Em.ZERO, TRANSPARENT,
            sibarum.dasum.gui.core.component.Direction.ROW,
            JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(closeGlyph),
            true, 0);
        Handlers.onClick(closeBtn, OverlayStack::pop);
        Component header = new Component.Flex(
            null, null, Em.ZERO, TRANSPARENT,
            sibarum.dasum.gui.core.component.Direction.ROW,
            JustifyContent.SPACE_BETWEEN, AlignItems.CENTER, Em.ZERO,
            List.of(title, closeBtn), false, 0);
        Component hint   = new Component.Text(
            "click outside or press ESC to dismiss", Em.of(0.85f), HINT_FG);
        Component dialog = new Component.Flex(
            null, null, Em.of(1.0f), DIALOG_BG,
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            JustifyContent.START, AlignItems.STRETCH, Em.of(0.5f),
            List.of(header, hint, scroll), false, 1);
        Component framed = new Component.Flex(
            null, null, Em.of(0.125f), DIALOG_BORDER,
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(dialog), false, 1);
        Component padded = new Component.Flex(
            LOG_DIALOG_MAX_W, LOG_DIALOG_MAX_H, LOG_DIALOG_VIEWPORT_MARGIN, TRANSPARENT,
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(framed), false, 0);
        OverlayStack.push(new OverlayStack.Overlay(
            padded, Anchor.CENTER, true, () -> {}));
        FocusState.set(textarea);
        sibarum.dasum.gui.core.input.ScrollStates.of(scroll).scrollByPx(0f, 1_000_000f);
    }

    /**
     * Build a single selectable Text of the whole history, one row per event —
     * {@code HH:mm:ss  [severity/channel] message}, with any details indented
     * underneath. (The popup is monochrome; per-row severity tint lives on the
     * live ribbon, not here.)
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
                  .append(e.severity().name().toLowerCase())
                  .append('/')
                  .append(e.channel().name().toLowerCase())
                  .append(e.alert() ? "/alert" : "")
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
