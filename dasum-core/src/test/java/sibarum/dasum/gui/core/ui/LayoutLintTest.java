package sibarum.dasum.gui.core.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each lint rule fires on a crafted tree and stays silent on a good one. */
final class LayoutLintTest {

    @AfterEach
    void resetStrict() { Ui.strict(true); }

    private static boolean has(List<Diagnostic> ds, Diagnostic.Severity sev, String rule) {
        return ds.stream().anyMatch(d -> d.severity() == sev && d.rule().equals(rule));
    }

    @Test
    void detectsCollapsingFlexChild() {
        // The screenshot bug: a fill (null main) grow=0 Flex child of a column.
        Component tree = Ui.column().named("pane")
            .add(Ui.column().fill().named("card"))
            .build();
        List<Diagnostic> ds = Ui.check(tree);
        assertTrue(has(ds, Diagnostic.Severity.ERROR, "collapse-to-zero"));
        assertTrue(ds.stream().anyMatch(d -> d.path().contains("card")));
    }

    @Test
    void aFitChildDoesNotCollapse() {
        Component tree = Ui.column()
            .add(Ui.column().fit().add(Ui.text("content")))
            .build();
        assertFalse(has(Ui.check(tree), Diagnostic.Severity.ERROR, "collapse-to-zero"));
    }

    @Test
    void detectsBoxWithMultipleChildren() {
        // Only constructible via the raw record — the builder forbids it.
        Component a = new Component.Text("a", Em.of(1f), Color.WHITE);
        Component b = new Component.Text("b", Em.of(1f), Color.WHITE);
        Component box = new Component.Box(Em.of(4f), Em.of(4f), Em.ZERO, Color.TRANSPARENT, List.of(a, b));
        assertTrue(has(Ui.check(box), Diagnostic.Severity.ERROR, "box-multi-child"));
    }

    @Test
    void detectsJustifyVsGrow() {
        Component tree = Ui.row().justify(sibarum.dasum.gui.core.component.JustifyContent.SPACE_BETWEEN)
            .add(Ui.text("a").grow(1))
            .add(Ui.text("b"))
            .build();
        assertTrue(has(Ui.check(tree), Diagnostic.Severity.WARN, "justify-vs-grow"));
    }

    @Test
    void detectsWrapOnColumn() {
        Component tree = Ui.column().wrap().add(Ui.text("x")).build();
        assertTrue(has(Ui.check(tree), Diagnostic.Severity.WARN, "wrap-on-column"));
    }

    @Test
    void detectsStretchIgnoringExplicitCrossSize() {
        // Column stretches width; a child with a fixed width is ignored.
        Component tree = Ui.column().add(Ui.box().size(Em.of(3f), Em.of(2f))).build();
        assertTrue(has(Ui.check(tree), Diagnostic.Severity.WARN, "stretch-ignores-size"));
    }

    @Test
    void detectsUnscrollableScroll() {
        Component tree = Ui.scroll(Ui.column().fill().build()).height(Em.of(10f)).build();
        assertTrue(has(Ui.check(tree), Diagnostic.Severity.WARN, "scroll-cant-scroll"));
    }

    @Test
    void detectsFixedParentOverflow() {
        Component tree = Ui.row().width(Em.of(5f)).gap(Em.ZERO)
            .add(Ui.box().size(Em.of(4f), Em.of(1f)))
            .add(Ui.box().size(Em.of(4f), Em.of(1f)))
            .build();
        assertTrue(has(Ui.check(tree), Diagnostic.Severity.WARN, "fixed-overflow"));
    }

    @Test
    void aCleanTreeReportsNothing() {
        Component tree = Ui.column().padding(Em.of(1f)).gap(Em.of(0.5f))
            .add(Ui.text("Title").size(Em.of(1.4f)))
            .add(Ui.row().add(Ui.button("Run")).add(Ui.spacer()))
            .add(Ui.column().fit().add(Ui.text("body")))
            .build();
        assertEquals(List.of(), Ui.check(tree));
    }

    @Test
    void lintThrowsInStrictAndLogsInLenient() {
        Component bad = Ui.column().add(Ui.column().fill().named("card")).build();
        Ui.strict(true);
        assertThrows(IllegalStateException.class, () -> Ui.lint(bad));
        Ui.lenient();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> Ui.lint(bad));
    }
}
