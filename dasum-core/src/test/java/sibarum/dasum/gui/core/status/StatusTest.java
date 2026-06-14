package sibarum.dasum.gui.core.status;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.DynamicChildren;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.theme.Variant;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke + behavior tests for {@link Status}. The 6-second auto-revert
 * is exercised by manual interaction only — JUnit-time, we verify the
 * synchronous parts (history append, active-event set, clear, listener
 * delivery) which together prove the wiring is sound.
 */
final class StatusTest {

    @Test
    void logAppendsToHistoryAndSetsActive() {
        // Snapshot the size — earlier tests may have logged events.
        int before = Status.events().size();
        StatusEvent e = Status.log("hello", Variant.INFO);
        assertNotNull(e);
        assertEquals("hello", e.message());
        assertEquals(Variant.INFO, e.variant());
        assertEquals(before + 1, Status.events().size(),
            "history grew by one");
        assertEquals(e, Status.activeEvent(),
            "log() sets active event");
    }

    @Test
    void clearMessageRevertsActiveEvent() {
        Status.log("transient");
        assertNotNull(Status.activeEvent());
        Status.clearMessage();
        assertNull(Status.activeEvent(), "clearMessage clears active");
    }

    @Test
    void subscriberReceivesEvents() {
        List<StatusEvent> received = new ArrayList<>();
        Status.subscribe(received::add);
        StatusEvent a = Status.error("boom", "stack trace line 1\nstack trace line 2");
        StatusEvent b = Status.success("done");
        assertTrue(received.contains(a));
        assertTrue(received.contains(b));
        // The error event preserves its details for the popup.
        assertEquals("stack trace line 1\nstack trace line 2", a.details());
        assertTrue(a.hasDetails());
    }

    @Test
    void detailsAreOptional() {
        StatusEvent e = Status.log("no-details");
        assertNull(e.details());
        assertEquals(false, e.hasDetails());
    }

    @Test
    void dockedFieldPersistsAcrossMessagesAndDoesNotOverlap() {
        Status.setDockedMessage("v1.2.3", Variant.INFO);
        Component root = Status.wrap(new Component.Box(
            Em.of(1f), Em.of(1f), Em.ZERO, new Color(0.1f, 0.1f, 0.1f, 1f)));

        // root = Flex(COLUMN)[content, ribbon]; ribbon = Flex(ROW)[messageZone, dockedZone].
        Component.Flex ribbon = (Component.Flex) ((Component.Flex) root).children().get(1);
        Component messageZone = ribbon.children().get(0);
        Component dockedZone  = ribbon.children().get(1);

        // Distinct sibling containers — they can't overlap.
        assertNotSame(messageZone, dockedZone, "message and docked fields are separate zones");
        assertEquals(1, DynamicChildren.effectiveChildren(dockedZone).size(),
            "docked field is shown");

        // A transient event populates the message zone but must NOT clear the docked field.
        Status.log("saved", Variant.SUCCESS);
        assertEquals(1, DynamicChildren.effectiveChildren(dockedZone).size(),
            "docked field survives an active event");
        assertTrue(DynamicChildren.effectiveChildren(messageZone).size() >= 1,
            "message zone shows the event message");

        // Reverting the message still leaves the docked field intact.
        Status.clearMessage();
        assertEquals(1, DynamicChildren.effectiveChildren(dockedZone).size(),
            "docked field survives reverting to idle");

        Status.setDockedMessage(""); // tidy up shared singleton for other tests
    }

    @Test
    void wrapBuildsRootContainingRibbon() {
        sibarum.dasum.gui.core.component.Component inner =
            new sibarum.dasum.gui.core.component.Component.Box(
                sibarum.dasum.gui.core.em.Em.of(1f),
                sibarum.dasum.gui.core.em.Em.of(1f),
                sibarum.dasum.gui.core.em.Em.ZERO,
                new sibarum.dasum.gui.core.render.Color(0.1f, 0.1f, 0.1f, 1f));
        sibarum.dasum.gui.core.component.Component root = Status.wrap(inner);
        // The wrap is a Flex(COLUMN) with two children: grown content + ribbon.
        assertTrue(root instanceof sibarum.dasum.gui.core.component.Component.Flex,
            "Status.wrap returns a Flex");
        sibarum.dasum.gui.core.component.Component.Flex f =
            (sibarum.dasum.gui.core.component.Component.Flex) root;
        assertEquals(2, f.children().size(),
            "Wrapped root has content + ribbon");
    }
}
