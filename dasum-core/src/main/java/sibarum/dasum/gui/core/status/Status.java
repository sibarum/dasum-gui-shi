package sibarum.dasum.gui.core.status;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
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
    /** Clear space between the (truncating) message and the docked field. */
    private static final Em DOCK_MARGIN_EM = Em.of(1.25f);
    /** Max dialog size on large viewports — past this, the dialog stops growing and centers. */
    private static final Em LOG_DIALOG_MAX_W = Em.of(120f);
    private static final Em LOG_DIALOG_MAX_H = Em.of(60f);
    /** Space between the viewport edge and the dialog edge — the "minus margins" the user sees. */
    private static final Em LOG_DIALOG_VIEWPORT_MARGIN = Em.of(1.5f);
    /** Approximate wrap width for the log textarea — see {@link #buildLogsTextArea}. */
    private static final Em LOG_DIALOG_WRAP_WIDTH = Em.of(LOG_DIALOG_MAX_W.value() - 4f);
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

    // ----- docked field (permanent, app-controlled, never overwritten by events) -----
    private static String  dockedMessage = "";
    private static Variant dockedVariant = Variant.DEFAULT;

    // ----- visuals (lazily built by wrap) -----
    private static Component.Flex ribbon = null;
    // The ribbon is split into two sibling zones so the always-present docked
    // field can't be clobbered by the event/idle message refresh: the message
    // zone grows to fill (pushing the docked zone to the trailing edge) and is
    // the only one cleared by refresh(); the docked zone is owned by
    // refreshDocked() alone.
    private static Component.Flex messageZone = null;
    private static Component.Flex dockedZone = null;
    private static boolean wrapped = false;

    // ----- close-icon registration -----
    // Apps may register an icon-font codepoint so the log dialog's close
    // button renders as a proper glyph (e.g. lucide "x") instead of the
    // fallback ASCII letter. dasum-core can't depend on app-generated
    // Icons constants, so the codepoint is plugged in at startup.
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

    /**
     * Set the text shown in the ribbon's permanently-docked field, anchored to
     * the trailing (right) edge. Unlike {@link #setDefaultMessage}, this field
     * is independent of the event/idle message: it stays put when a
     * {@link #log} event is displayed and never overlaps the message field.
     * Typical use: a persistent indicator such as current mode, cursor
     * position, or a clock. Pass an empty string to hide it.
     *
     * <p>May be called before {@link #wrap}; the value is shown once the ribbon
     * is built. Thread-safe.
     */
    public static void setDockedMessage(String text) {
        setDockedMessage(text, Variant.DEFAULT);
    }

    public static void setDockedMessage(String text, Variant variant) {
        synchronized (LOCK) {
            dockedMessage = text == null ? "" : text;
            dockedVariant = variant == null ? Variant.DEFAULT : variant;
            refreshDocked();
        }
    }

    /** The current docked-field text (empty string when unset). Safe from any thread. */
    public static String dockedMessage() {
        synchronized (LOCK) { return dockedMessage; }
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

    /**
     * Register a codepoint from the default {@code "icons"} font group for
     * the log dialog's close button. Call once at startup with the
     * generated {@code Icons.X} (or equivalent) constant. Until set, the
     * close button falls back to a plain ASCII "X" rendered in the primary
     * font.
     */
    public static void setCloseIcon(int codepoint) {
        setCloseIcon(codepoint, Icon.DEFAULT_FONT_GROUP);
    }

    /** As {@link #setCloseIcon(int)} but from a non-default icon font group. */
    public static void setCloseIcon(int codepoint, String fontGroup) {
        closeIconCodepoint = codepoint;
        closeIconFontGroup = fontGroup == null ? Icon.DEFAULT_FONT_GROUP : fontGroup;
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
        // Anchored-trailing-field pattern (ROW): a leading flexGrow=1 zone and a
        // trailing rigid (flexGrow=0) zone. Three rules make it hold up under
        // both empty and overflowing content:
        //   1. Leading zone grows to fill, pushing the trailing zone to the
        //      right edge.
        //   2. Trailing zone uses Em.AUTO width (fit-content) — NOT null. A
        //      null-width flex resolves to the 0 fallback in intrinsic context,
        //      collapsing the zone so its content spills past the right edge.
        //   3. The leading content (flexGrow=1, ellipsize) yields to the rigid
        //      trailing zone when space runs out — Layout shrinks grow>0
        //      children first — so the message truncates with "..." instead of
        //      running under the docked field. DOCK_MARGIN_EM keeps them apart.
        messageZone = new Component.Flex(
            null, null, Em.ZERO, TRANSPARENT,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(), false, 1);
        dockedZone = new Component.Flex(
            Em.AUTO, null, Em.ZERO, TRANSPARENT,
            Direction.ROW, JustifyContent.END, AlignItems.CENTER, Em.of(0.5f),
            List.of(), false, 0);
        ribbon = new Component.Flex(
            null, RIBBON_HEIGHT_EM, Em.of(0.4f), RIBBON_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, DOCK_MARGIN_EM,
            List.of(messageZone, dockedZone), true, 0);
        Handlers.onClick(ribbon, Status::onRibbonClicked);
        refresh();
        refreshDocked();
    }

    /** Rebuild the message zone's content under {@link #LOCK}. */
    private static void refresh() {
        if (messageZone == null) return;
        DynamicChildren.clearChildren(messageZone);
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
            DynamicChildren.add(messageZone, buildMessageText(text, variant));
        }
        // Always trail with a faded "click for log" hint when no event is
        // active and the default text isn't already serving that purpose —
        // discoverable signal that the bar is clickable.
        if (ev == null && (text == null || text.isEmpty())) {
            DynamicChildren.add(messageZone, buildMessageText(
                "Click for event log", Variant.DEFAULT));
        }
        Invalidator.invalidate();
    }

    /** Rebuild the docked zone's content under {@link #LOCK}; independent of refresh(). */
    private static void refreshDocked() {
        if (dockedZone == null) return;
        DynamicChildren.clearChildren(dockedZone);
        if (dockedMessage != null && !dockedMessage.isEmpty()) {
            DynamicChildren.add(dockedZone, buildRibbonText(dockedMessage, dockedVariant));
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

    /**
     * Message-zone label: like {@link #buildRibbonText} but flexible and
     * truncating. {@code flexGrow=1} lets the layout shrink it to the space
     * left of the docked field (the docked field is rigid, so it wins the
     * right edge); {@code ellipsize} then trims the text with a trailing
     * "..." instead of letting it run under the docked text.
     */
    private static Component buildMessageText(String text, Variant variant) {
        return ((Component.Text) buildRibbonText(text, variant))
            .withEllipsize(true)
            .withFlexGrow(1);
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
        // The dialog tree uses null sizes + flexGrow=1 all the way down so the
        // dialog re-fits the viewport on every layout pass — including after a
        // window resize. The OUTER Flex requests the max em size; OverlayStack
        // clamps it to the viewport, so on small windows the outer == viewport
        // and on large windows it caps at the max. The outer's padding is the
        // viewport-edge margin; STRETCH+flexGrow propagate the interior down
        // through framed → dialog → scroll → textarea.
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
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(closeGlyph),
            true, 0);
        Handlers.onClick(closeBtn, OverlayStack::pop);
        Component header = new Component.Flex(
            null, null, Em.ZERO, TRANSPARENT,
            Direction.ROW, JustifyContent.SPACE_BETWEEN, AlignItems.CENTER, Em.ZERO,
            List.of(title, closeBtn), false, 0);
        Component hint   = new Component.Text(
            "click outside or press ESC to dismiss", Em.of(0.85f), HINT_FG);
        Component dialog = new Component.Flex(
            null, null, Em.of(1.0f), DIALOG_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.5f),
            List.of(header, hint, scroll), false, 1);
        Component framed = new Component.Flex(
            null, null, Em.of(0.125f), DIALOG_BORDER,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(dialog), false, 1);
        Component padded = new Component.Flex(
            LOG_DIALOG_MAX_W, LOG_DIALOG_MAX_H, LOG_DIALOG_VIEWPORT_MARGIN, TRANSPARENT,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(framed), false, 0);
        OverlayStack.push(new OverlayStack.Overlay(
            padded, Anchor.CENTER, true, () -> {}));
        // Override OverlayStack's auto-focus (which picks the first interactive
        // descendant in tree order — the close button, since it sits in the
        // header). The log textarea is the meaningful focus target: it owns
        // the keyboard read/copy affordances and the visible caret. The close
        // button still responds to clicks regardless of focus.
        FocusState.set(textarea);
        // Scroll to bottom — bounds-clamped by the Scroll viewport on the
        // next layout pass.
        sibarum.dasum.gui.core.input.ScrollStates.of(scroll).scrollByPx(0f, 1_000_000f);
    }

    private static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);

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
