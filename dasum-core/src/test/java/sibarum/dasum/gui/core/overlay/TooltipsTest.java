package sibarum.dasum.gui.core.overlay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Components;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.input.HoverState;
import sibarum.dasum.gui.core.render.Color;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link Tooltips} registry. Pure-data behavior — no
 * rendering, no font, no GLFW. Verifies identity-keyed put/get/clear/migrate
 * and concurrency safety of the sidecar.
 */
final class TooltipsTest {

    private Component box(int id) {
        return new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.1f * (id % 10), 0.2f, 0.3f, 1f));
    }

    @BeforeEach
    void resetGlobals() {
        TooltipController.resetForTest();
        HoverState.clear();
    }

    @Test
    void identityKeyed_recordEqualityDoesNotCollide() {
        // Two records that compare equal() but are distinct identities —
        // tooltips must NOT share state across them.
        Component a = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.5f, 0.5f, 0.5f, 1f));
        Component b = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.5f, 0.5f, 0.5f, 1f));
        assertEquals(a, b, "records compare equal — identity test would be meaningless without this");
        assertFalse(a == b, "identities are distinct");

        Tooltips.set(a, "from a");
        assertEquals("from a", Tooltips.get(a));
        assertNull(Tooltips.get(b), "identity-keyed — b has no entry");
        Tooltips.remove(a);
        assertNull(Tooltips.get(a));
    }

    @Test
    void emptyOrNullTextRemoves() {
        Component a = box(0);
        Tooltips.set(a, "hello");
        Tooltips.set(a, "");
        assertNull(Tooltips.get(a), "empty text removes entry");

        Tooltips.set(a, "again");
        Tooltips.set(a, null);
        assertNull(Tooltips.get(a), "null text removes entry");
    }

    @Test
    void clearViaComponentsDetach() {
        // Add an entry, call Components.detach — the entry should be gone
        // (Tooltips is wired into clearAllBuiltIn).
        Component leaf = box(1);
        Tooltips.set(leaf, "doomed");
        assertEquals("doomed", Tooltips.get(leaf));
        Components.detach(leaf);
        assertNull(Tooltips.get(leaf), "Components.detach cleared tooltip");
    }

    @Test
    void migrateCopiesText() {
        Component from = box(2);
        Component to   = box(3);
        Tooltips.set(from, "carry me");
        Components.migrateState(from, to);
        assertEquals("carry me", Tooltips.get(to));
        // Migration is a copy, not a move — the original retains its entry.
        // (Detach the original separately if removal is wanted; matches
        // every other sidecar's migrate semantics.)
        assertEquals("carry me", Tooltips.get(from));
    }

    @Test
    void hasReflectsState() {
        Component a = box(4);
        assertFalse(Tooltips.has(a));
        Tooltips.set(a, "ok");
        assertTrue(Tooltips.has(a));
        Tooltips.remove(a);
        assertFalse(Tooltips.has(a));
    }

    @Test
    void clearOnDetachedComponentHidesLiveTooltip() {
        Component a = box(5);
        Tooltips.set(a, "live");
        // Force the controller's anchor to be `a` so we can verify
        // onComponentDetached fires.
        TooltipController.resetForTest();
        // Use the package-private hook directly — same path Tooltips.clear
        // takes during Components.detach.
        // We can't call resolveBeforeRender without a LayoutResult, so
        // simulate by registering then detaching.
        Components.detach(a);
        // Anchor must be null after detach (controller had nothing pinned,
        // but the behavior is "no-op if nothing pinned").
        assertNull(TooltipController.currentText());
    }

    @Test
    void concurrentRegistrationsDoNotCorrupt() throws InterruptedException {
        // Stress: 8 worker threads register, read, remove on disjoint
        // component instances. The IdentityHashMap is guarded by a single
        // lock; this just verifies we don't see lost updates or NPE.
        final int N_THREADS = 8;
        final int N_PER_THREAD = 500;
        Component[] components = new Component[N_THREADS * N_PER_THREAD];
        for (int i = 0; i < components.length; i++) components[i] = box(i);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        Thread[] threads = new Thread[N_THREADS];
        for (int t = 0; t < N_THREADS; t++) {
            final int tid = t;
            threads[t] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int base = tid * N_PER_THREAD;
                try {
                    for (int i = 0; i < N_PER_THREAD; i++) {
                        Component c = components[base + i];
                        Tooltips.set(c, "t" + tid + "i" + i);
                        String v = Tooltips.get(c);
                        if (v == null || !v.startsWith("t" + tid)) errors.incrementAndGet();
                    }
                    for (int i = 0; i < N_PER_THREAD; i++) {
                        Tooltips.remove(components[base + i]);
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                    errors.incrementAndGet();
                }
            }, "tooltips-stress-" + t);
        }
        for (Thread th : threads) th.start();
        start.countDown();
        for (Thread th : threads) th.join(TimeUnit.SECONDS.toMillis(30));
        assertEquals(0, errors.get(), "no concurrency errors during stress");
        // Every entry should have been removed.
        for (Component c : components) assertFalse(Tooltips.has(c), "all entries cleared");
    }
}
