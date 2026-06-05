package sibarum.dasum.gui.core.input;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TextStyleStatesTest {

    private static final Color RED   = new Color(1f, 0f, 0f, 1f);
    private static final Color GREEN = new Color(0f, 1f, 0f, 1f);
    private static final Color BLUE  = new Color(0f, 0f, 1f, 1f);
    private static final Color WHITE = new Color(1f, 1f, 1f, 1f);

    private static Component.Text text(String s) {
        return new Component.Text(s, Em.of(1f), WHITE);
    }

    @Test
    void unstyledTextReturnsEmptyAndDoesNotCreateHolder() {
        Component.Text t = text("hello");
        assertTrue(TextStyleStates.foregroundOf(t).isEmpty());
        assertTrue(TextStyleStates.backgroundOf(t).isEmpty());
        // A second call must still see empty — and (the bigger concern)
        // there should be no holder lingering. We can't peek HOLDERS
        // directly, but we can prove no listener is firing and that
        // clear is a no-op by removing nothing of substance.
        TextStyleStates.clear(t);
        assertTrue(TextStyleStates.foregroundOf(t).isEmpty());
    }

    @Test
    void identityKeyedNotEqualsKeyed() {
        Component.Text a = text("hello");
        Component.Text b = text("hello"); // record.equals(a) == true, but distinct identity
        assertEquals(a, b, "precondition: records are value-equal");
        TextStyleStates.setForeground(a, List.of(new TextStyle(0, 1, RED)));
        assertEquals(1, TextStyleStates.foregroundOf(a).size());
        assertTrue(TextStyleStates.foregroundOf(b).isEmpty(),
                   "identity-keyed lookup must not see value-equal sibling");
        TextStyleStates.clear(a);
        TextStyleStates.clear(b);
    }

    @Test
    void setForegroundRoundTrip() {
        Component.Text t = text("hello world");
        List<TextStyle> input = List.of(
            new TextStyle(0, 5, RED),
            new TextStyle(6, 11, GREEN)
        );
        TextStyleStates.setForeground(t, input);
        List<TextStyle> out = TextStyleStates.foregroundOf(t);
        assertEquals(input, out);
        TextStyleStates.clear(t);
    }

    @Test
    void setSnapshotsDefensively() {
        Component.Text t = text("abcdef");
        List<TextStyle> source = new ArrayList<>();
        source.add(new TextStyle(0, 3, RED));
        TextStyleStates.setForeground(t, source);
        // Mutating the source after set must not affect the stored snapshot.
        source.clear();
        source.add(new TextStyle(0, 6, BLUE));
        assertEquals(1, TextStyleStates.foregroundOf(t).size());
        assertEquals(RED, TextStyleStates.foregroundOf(t).get(0).color());
        TextStyleStates.clear(t);
    }

    @Test
    void foregroundOfReturnsImmutableSnapshot() {
        Component.Text t = text("abcdef");
        TextStyleStates.setForeground(t, List.of(new TextStyle(0, 3, RED)));
        List<TextStyle> snap = TextStyleStates.foregroundOf(t);
        assertThrows(UnsupportedOperationException.class,
                     () -> snap.add(new TextStyle(3, 6, GREEN)));
        TextStyleStates.clear(t);
    }

    @Test
    void setForegroundRejectsNull() {
        Component.Text t = text("abc");
        assertThrows(NullPointerException.class,
                     () -> TextStyleStates.setForeground(t, null));
        assertThrows(NullPointerException.class,
                     () -> TextStyleStates.setBackground(t, null));
    }

    @Test
    void backgroundIsIndependentOfForeground() {
        Component.Text t = text("abcdef");
        TextStyleStates.setForeground(t, List.of(new TextStyle(0, 2, RED)));
        TextStyleStates.setBackground(t, List.of(new TextStyle(3, 5, BLUE)));
        assertEquals(RED,  TextStyleStates.foregroundOf(t).get(0).color());
        assertEquals(BLUE, TextStyleStates.backgroundOf(t).get(0).color());
        TextStyleStates.clear(t);
    }

    @Test
    void updateForegroundRunsAtomicallyAndSeesLatest() {
        Component.Text t = text("abcdef");
        TextStyleStates.setForeground(t, List.of(new TextStyle(0, 2, RED)));
        List<TextStyle> result = TextStyleStates.updateForeground(t, current -> {
            assertEquals(1, current.size());
            assertEquals(RED, current.get(0).color());
            List<TextStyle> next = new ArrayList<>(current);
            next.add(new TextStyle(3, 5, GREEN));
            return next;
        });
        assertEquals(2, result.size());
        assertEquals(2, TextStyleStates.foregroundOf(t).size());
        TextStyleStates.clear(t);
    }

    @Test
    void clearRangesEmptiesBothAxesWithoutDroppingHolder() {
        Component.Text t = text("abcdef");
        TextStyleStates.setForeground(t, List.of(new TextStyle(0, 2, RED)));
        TextStyleStates.setBackground(t, List.of(new TextStyle(2, 4, BLUE)));
        TextStyleStates.clearRanges(t);
        assertTrue(TextStyleStates.foregroundOf(t).isEmpty());
        assertTrue(TextStyleStates.backgroundOf(t).isEmpty());
        // The holder is still there; a subsequent set should round-trip.
        TextStyleStates.setForeground(t, List.of(new TextStyle(0, 1, GREEN)));
        assertEquals(1, TextStyleStates.foregroundOf(t).size());
        TextStyleStates.clear(t);
    }

    @Test
    void clearRemovesHolder() {
        Component.Text t = text("abcdef");
        TextStyleStates.setForeground(t, List.of(new TextStyle(0, 2, RED)));
        TextStyleStates.clear(t);
        // Holder is gone — foregroundOf returns the empty constant.
        assertSame(List.of(), TextStyleStates.foregroundOf(t));
    }

    @Test
    void migrateTransfersRanges() {
        Component.Text from = text("abcdef");
        Component.Text to   = text("abcdef"); // value-equal but distinct identity
        assertNotSame(from, to);
        TextStyleStates.setForeground(from, List.of(new TextStyle(0, 3, RED)));
        TextStyleStates.setBackground(from, List.of(new TextStyle(0, 6, BLUE)));

        TextStyleStates.migrate(from, to);

        assertEquals(1, TextStyleStates.foregroundOf(to).size());
        assertEquals(RED, TextStyleStates.foregroundOf(to).get(0).color());
        assertEquals(1, TextStyleStates.backgroundOf(to).size());

        // Same-holder semantics — mutating one side surfaces on the other,
        // matching TextStates.migrate behaviour. Document via assertion.
        TextStyleStates.setForeground(to, List.of(new TextStyle(0, 6, GREEN)));
        assertEquals(GREEN, TextStyleStates.foregroundOf(from).get(0).color());

        TextStyleStates.clear(from);
        TextStyleStates.clear(to);
    }

    @Test
    void migrateSameInstanceIsNoOp() {
        Component.Text t = text("abc");
        TextStyleStates.setForeground(t, List.of(new TextStyle(0, 1, RED)));
        TextStyleStates.migrate(t, t);
        assertEquals(1, TextStyleStates.foregroundOf(t).size());
        TextStyleStates.clear(t);
    }

    /**
     * Stress: while a worker publishes thousands of updates, a reader
     * should never observe a torn list. The list reference is
     * volatile-published, and the content is an immutable copy, so the
     * read can never see a partially-built list. Asserts that every
     * snapshot the reader sees is either empty or has the expected
     * single-element shape — i.e. nothing inconsistent.
     */
    @Test
    void concurrentSetAndReadIsTearFree() throws Exception {
        Component.Text t = text("abcdef");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        int iters = 5_000;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iters; i++) {
                        TextStyleStates.setForeground(t,
                            List.of(new TextStyle(0, (i % 5) + 1, RED)));
                    }
                } catch (Throwable th) { failure.set(th); }
            });
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iters; i++) {
                        List<TextStyle> snap = TextStyleStates.foregroundOf(t);
                        if (!snap.isEmpty()) {
                            if (snap.size() != 1) {
                                throw new AssertionError("size " + snap.size());
                            }
                            TextStyle r = snap.get(0);
                            if (r.start() != 0 || r.end() < 1 || r.end() > 5) {
                                throw new AssertionError("ranges: " + r);
                            }
                            if (!RED.equals(r.color())) {
                                throw new AssertionError("color: " + r.color());
                            }
                        }
                    }
                } catch (Throwable th) { failure.set(th); }
            });
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally {
            pool.shutdownNow();
            TextStyleStates.clear(t);
        }
    }

    /**
     * Concurrent updateForeground calls must not lose updates. Two
     * workers each append N entries; the final list should contain
     * exactly 2N entries.
     */
    @Test
    void concurrentUpdateDoesNotLoseUpdates() throws Exception {
        Component.Text t = text("abcdef");
        int perWorker = 1_000;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int w = 0; w < 2; w++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perWorker; i++) {
                            TextStyleStates.updateForeground(t, current -> {
                                List<TextStyle> next = new ArrayList<>(current);
                                next.add(new TextStyle(0, 1, RED));
                                return next;
                            });
                        }
                    } catch (Throwable th) { failure.set(th); }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertEquals(2 * perWorker, TextStyleStates.foregroundOf(t).size());
        } finally {
            pool.shutdownNow();
            TextStyleStates.clear(t);
        }
    }

    /**
     * The 3-arg compatibility constructor must default wrapLineEndings to
     * false, and the flag must participate in record equality — guards
     * against the field being added to the constructor but dropped from
     * the canonical record components.
     */
    @Test
    void wrapLineEndingsDefaultsFalseAndAffectsEquality() {
        TextStyle compat = new TextStyle(0, 5, RED);
        assertEquals(new TextStyle(0, 5, RED, false), compat);
        assertFalse(compat.wrapLineEndings(), "3-arg constructor must default to false");
        assertFalse(new TextStyle(0, 5, RED, true).equals(compat),
                    "wrapLineEndings must be a record component, not a ctor side-channel");
    }

    /**
     * Compat constructors default outline/weight to none; withers copy all
     * other components; outline color/width must be set together.
     */
    @Test
    void outlineAndWeightDefaultsWithersAndValidation() {
        sibarum.dasum.gui.core.em.Em w = sibarum.dasum.gui.core.em.Em.of(0.05f);

        TextStyle plain = new TextStyle(0, 5, RED, true);
        assertEquals(null, plain.outlineColor());
        assertEquals(null, plain.outlineWidth());
        assertEquals(null, plain.weight());

        TextStyle outlined = plain.withOutline(BLUE, w);
        assertEquals(new TextStyle(0, 5, RED, true, BLUE, w, null), outlined);
        assertFalse(outlined.equals(plain), "outline must participate in equality");

        TextStyle weighted = outlined.withWeight(w);
        assertEquals(new TextStyle(0, 5, RED, true, BLUE, w, w), weighted);

        assertThrows(IllegalArgumentException.class,
                     () -> new TextStyle(0, 5, RED, false, BLUE, null, null),
                     "outlineColor without outlineWidth must be rejected");
        assertThrows(IllegalArgumentException.class,
                     () -> new TextStyle(0, 5, RED, false, null, w, null),
                     "outlineWidth without outlineColor must be rejected");
    }
}
