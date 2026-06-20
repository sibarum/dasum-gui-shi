package sibarum.dasum.gui.core.input.wheel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry mechanics for the wheel router: priority ordering, first-consumer
 * stops the cascade, removal, and cross-thread registration visibility. The
 * terminal scroll-container step isn't a registered handler — it's reached
 * exactly when {@link WheelRouter#offerToHandlers} returns false — so a
 * cascade where every handler declines is the test that the built-in
 * scrolling still gets its turn.
 */
class WheelRouterTest {

    private static WheelEvent dummy() {
        return new WheelEvent(0d, -1d, 0f, -2.5f, 0d, 0d, null, null, null, false);
    }

    @BeforeEach
    void reset() {
        WheelRouter.resetForTest();
    }

    @Test
    void higherPriorityRunsFirstAndFirstConsumerStopsCascade() {
        List<String> calls = new ArrayList<>();
        WheelRouter.addHandler(0,   e -> { calls.add("low");  return false; });
        WheelRouter.addHandler(100, e -> { calls.add("high"); return true;  }); // consumes
        WheelRouter.addHandler(50,  e -> { calls.add("mid");  return false; });

        boolean consumed = WheelRouter.offerToHandlers(dummy());

        assertTrue(consumed, "a handler consumed the event");
        assertEquals(List.of("high"), calls,
                "highest priority runs first and consuming stops the cascade");
    }

    @Test
    void tiesBreakByRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        WheelRouter.addHandler(10, e -> { calls.add("first");  return false; });
        WheelRouter.addHandler(10, e -> { calls.add("second"); return false; });

        WheelRouter.offerToHandlers(dummy());

        assertEquals(List.of("first", "second"), calls, "equal priority = FIFO");
    }

    @Test
    void allDecliningFallsThroughToTerminal() {
        WheelRouter.addHandler(10, e -> false);
        WheelRouter.addHandler(20, e -> false);

        assertFalse(WheelRouter.offerToHandlers(dummy()),
                "no handler consumed → router runs its non-removable terminal scroll step");
    }

    @Test
    void removedHandlerIsNotInvoked() {
        List<String> calls = new ArrayList<>();
        WheelHandler h = WheelRouter.addHandler(10, e -> { calls.add("h"); return false; });

        WheelRouter.removeHandler(h);
        WheelRouter.offerToHandlers(dummy());

        assertEquals(List.of(), calls, "removed handler must not fire");
    }

    @Test
    void registrationFromAnotherThreadIsVisibleToDispatch() throws InterruptedException {
        List<String> calls = new ArrayList<>();
        Thread t = new Thread(() -> WheelRouter.addHandler(10, e -> { calls.add("worker"); return true; }));
        t.start();
        t.join();

        assertTrue(WheelRouter.offerToHandlers(dummy()),
                "handler registered on a worker thread is seen by the GUI-thread dispatch");
        assertEquals(List.of("worker"), calls);
    }
}
