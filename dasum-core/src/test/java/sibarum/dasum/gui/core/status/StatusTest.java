package sibarum.dasum.gui.core.status;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.DynamicChildren;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the remodelled {@link Status} (a ledger with a log/alert
 * split, three orthogonal axes, and a seen-counter idle state). The 6-second
 * auto-revert is exercised by hand; here we verify the synchronous parts —
 * history append, the surface/counter split, seen-acknowledgment, and axis
 * preservation. The {@code Status} singleton is shared across tests, so each
 * counter assertion resets via {@link Status#markSeen()} and works in deltas.
 */
final class StatusTest {

    @Test
    void plainLogIsHistoryOnly_noSurface_noCounterBump() {
        Status.markSeen();
        int histBefore = Status.events().size();
        Status.clearMessage();                 // ensure idle
        StatusEvent e = Status.log("a quiet note");
        assertEquals(histBefore + 1, Status.events().size(), "recorded in history");
        assertFalse(e.alert(), "log() is not an alert");
        assertNull(Status.activeEvent(), "a plain log does not surface on the bar");
        assertEquals(0, Status.newAlertCount(), "a plain log does not bump the unseen counter");
    }

    @Test
    void alertSurfacesAndBumpsCounter() {
        Status.markSeen();
        StatusEvent e = Status.good("compiled");
        assertTrue(e.alert());
        assertEquals(Severity.GOOD, e.severity());
        assertEquals(e, Status.activeEvent(), "an alert becomes the active event");
        assertEquals(1, Status.newAlertCount(), "an alert bumps the unseen counter");
        Status.bad("parse error");
        assertEquals(2, Status.newAlertCount(), "each alert increments the counter");
    }

    @Test
    void markSeenZeroesTheCounter() {
        Status.notify("something");
        assertTrue(Status.newAlertCount() > 0);
        Status.markSeen();
        assertEquals(0, Status.newAlertCount(), "seeing the log acknowledges every alert");
    }

    @Test
    void clearMessageRevertsActiveAlert() {
        Status.notify("transient");
        assertNotNull(Status.activeEvent());
        Status.clearMessage();
        assertNull(Status.activeEvent(), "clearMessage reverts to idle");
    }

    @Test
    void axesArePreservedAndOrthogonal() {
        StatusEvent tech = Status.technical("db warm", "took 12ms");
        assertEquals(Channel.TECHNICAL, tech.channel());
        assertEquals(Severity.NEUTRAL, tech.severity());
        assertFalse(tech.alert(), "technical() is history-only");
        assertTrue(tech.hasDetails());

        StatusEvent bad = Status.bad("boom", "stack line 1\nstack line 2");
        assertEquals(Channel.USER, bad.channel());
        assertEquals(Severity.BAD, bad.severity());
        assertTrue(bad.alert());
        assertEquals("stack line 1\nstack line 2", bad.details());
    }

    @Test
    void subscriberReceivesBothLogsAndAlerts() {
        List<StatusEvent> received = new ArrayList<>();
        Status.subscribe(received::add);
        StatusEvent quiet = Status.log("noted");
        StatusEvent loud  = Status.good("done");
        assertTrue(received.contains(quiet), "history-only entries reach subscribers too");
        assertTrue(received.contains(loud));
    }

    @Test
    void detailsAreOptional() {
        StatusEvent e = Status.log("no-details");
        assertNull(e.details());
        assertFalse(e.hasDetails());
    }

    @Test
    void dockedFieldPersistsAcrossAlertsAndDoesNotOverlap() {
        Status.setDockedMessage("Ln 12, Col 4");
        Component root = Status.wrap(new Component.Box(
            Em.of(1f), Em.of(1f), Em.ZERO, new Color(0.1f, 0.1f, 0.1f, 1f)));
        // ribbon = Flex(ROW)[contentZone(grow), dockedZone(fit)].
        Component.Flex ribbon = (Component.Flex) ((Component.Flex) root).children().get(1);
        Component contentZone = ribbon.children().get(0);
        Component dockedZone  = ribbon.children().get(1);
        assertEquals(1, DynamicChildren.effectiveChildren(dockedZone).size(), "docked field shown");
        // An alert populates the leading zone but must not clear the docked field.
        Status.good("saved");
        assertEquals(1, DynamicChildren.effectiveChildren(dockedZone).size(),
            "docked field survives an active alert");
        assertTrue(DynamicChildren.effectiveChildren(contentZone).size() >= 1,
            "leading zone shows the alert");
        Status.clearMessage();
        assertEquals(1, DynamicChildren.effectiveChildren(dockedZone).size(),
            "docked field survives reverting to idle");
        assertEquals("Ln 12, Col 4", Status.dockedMessage());
        Status.setDockedMessage("");  // tidy the shared singleton for other tests
    }

    @Test
    void contextualOverrideIsPureDisplay_noHistoryNoCounter() {
        Status.markSeen();
        Status.clearMessage();            // ensure idle (shared singleton; a prior alert may linger)
        Status.clearContextualMessage();
        int histBefore = Status.events().size();
        Status.setContextualMessage("error: unbound name 'x'", Severity.BAD);
        assertEquals(histBefore, Status.events().size(), "a contextual override records no history");
        assertEquals(0, Status.newAlertCount(), "a contextual override bumps no counter");
        assertNull(Status.activeEvent(), "a contextual override sets no active event");
        Status.clearContextualMessage();  // tidy the shared singleton
    }

    @Test
    void wrapBuildsRootContainingRibbonWithContentAndDockedZones() {
        Component root = Status.wrap(new Component.Box(
            Em.of(1f), Em.of(1f), Em.ZERO, new Color(0.1f, 0.1f, 0.1f, 1f)));
        // root = Flex(COLUMN)[content, ribbon]; ribbon = Flex(ROW)[contentZone, dockedZone].
        assertTrue(root instanceof Component.Flex, "Status.wrap returns a Flex");
        Component.Flex f = (Component.Flex) root;
        assertEquals(2, f.children().size(), "wrapped root = content + ribbon");
        Component.Flex ribbon = (Component.Flex) f.children().get(1);
        assertEquals(2, ribbon.children().size(), "ribbon = leading content zone + trailing docked zone");
        // The idle bar shows something clickable (the counter or the affordance), never empty.
        Component contentZone = ribbon.children().get(0);
        assertTrue(DynamicChildren.effectiveChildren(contentZone).size() >= 1,
            "idle bar always shows the counter or the 'Event log' affordance");
    }
}
