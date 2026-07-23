package sibarum.dasum.gui.core.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Ui.sceneView() builder and the geometry-based collapse lint (the plot-in-a-column trap). */
class SceneViewLintTest {

    @BeforeEach void dpi() { EmContext.setDpiScale(1f); }

    private static Component button() {
        return new Component.Box(Em.of(10f), Em.of(3f), Em.ZERO, Color.WHITE, List.of(), false, 0);
    }

    private static boolean hasCollapse(List<Diagnostic> ds) {
        return ds.stream().anyMatch(d -> d.rule().equals("collapsed-render"));
    }

    @Test
    void sceneViewBuilder_defaultsToFillAndGrow() {
        Component c = Ui.sceneView().background(Color.BLACK).build();
        Component.SceneView sv = (Component.SceneView) c;
        assertNull(sv.width(), "fills width by default");
        assertNull(sv.height(), "fills height by default");
        assertEquals(1, sv.flexGrow(), "takes a share of leftover space by default");
        assertTrue(sv.interactive(), "interactive by default");
    }

    @Test
    void lint_flagsASceneCollapsedByAWrapperColumn() {
        // The bug: a fill SceneView wrapped in an explicit column that itself doesn't fill its
        // parent's cross axis collapses to zero width. The geometry pass must flag it.
        Component wrapped = Ui.column().align(AlignItems.CENTER)
            .add(Ui.column().align(AlignItems.CENTER)
                .add(Ui.sceneView().background(Color.BLACK))
                .add(button()))
            .build();
        assertTrue(hasCollapse(Ui.check(wrapped)),
            "a wrapper-collapsed scene must be flagged: " + Ui.check(wrapped));
    }

    @Test
    void lint_passesWhenTheSceneIsADirectFillChild() {
        // Scene directly in a filling column → resolves to a real rect → no collapse finding.
        Component ok = Ui.column().fill()
            .add(Ui.sceneView().background(Color.BLACK))
            .add(button())
            .build();
        assertFalse(hasCollapse(Ui.check(ok)),
            "a direct fill-child scene must NOT be flagged: " + Ui.check(ok));
    }
}
