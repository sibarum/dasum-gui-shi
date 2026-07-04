package sibarum.dasum.gui.core.ui;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The builders produce plain records with safe, collapse-proof defaults. */
final class UiBuilderTest {

    @Test
    void columnDefaultsFitBothAxesTransparentStretch() {
        Component.Flex f = (Component.Flex) Ui.column().build();
        assertEquals(Direction.COLUMN, f.direction());
        assertEquals(Em.AUTO, f.height(), "both axes default to fit-content, not the collapse-prone null");
        assertEquals(Em.AUTO, f.width());
        assertEquals(Color.TRANSPARENT, f.color(), "never null");
        assertEquals(AlignItems.STRETCH, f.align());
    }

    @Test
    void rowDefaultsFitBothAxesCenter() {
        Component.Flex f = (Component.Flex) Ui.row().build();
        assertEquals(Direction.ROW, f.direction());
        assertEquals(Em.AUTO, f.width());
        assertEquals(Em.AUTO, f.height());
        assertEquals(AlignItems.CENTER, f.align());
    }

    @Test
    void sizingVocabularyMapsToNullAutoAndGrow() {
        Component.Flex filled = (Component.Flex) Ui.column().fill().build();
        assertNull(filled.height(), ".fill() -> null on both axes");
        assertNull(filled.width());
        assertEquals(Em.AUTO, ((Component.Flex) Ui.column().fit().build()).height(), ".fit() -> AUTO");
        assertEquals(Em.of(3f), ((Component.Flex) Ui.column().height(Em.of(3f)).build()).height());
        assertEquals(2, Ui.row().grow(2).build().flexGrow());
    }

    @Test
    void addAcceptsBothRecordsAndBuilders() {
        Component.Flex f = (Component.Flex) Ui.column()
            .add(Ui.text("from builder"))
            .add(new Component.Text("from record", Em.of(1f), Color.WHITE))
            .build();
        assertEquals(2, f.children().size());
    }

    @Test
    void boxRequiresExplicitSize() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Ui.box().build());
        assertTrue(ex.getMessage().contains("width") && ex.getMessage().contains("height"), ex.getMessage());
        assertInstanceOf(Component.Box.class, Ui.box().size(Em.of(1f), Em.of(1f)).build());
    }

    @Test
    void textColorNeverNull() {
        Component.Text t = (Component.Text) Ui.text("hi").build();
        assertNotNull(t.color());
        assertEquals("hi", t.content());
    }

    @Test
    void spacerGrows() {
        assertEquals(1, Ui.spacer().flexGrow());
    }

    @Test
    void fromLiftsRecordBackIntoBuilder() {
        Component.Flex original = (Component.Flex) Ui.column().gap(Em.of(1f)).named("orig").build();
        Component.Flex edited = (Component.Flex) Ui.from(original).background(Color.WHITE).build();
        assertEquals(Em.of(1f), edited.gap(), "carried gap over");
        assertEquals(Color.WHITE, edited.color(), "applied the edit");
        assertEquals(Direction.COLUMN, edited.direction());
    }

    @Test
    void namedLabelSurfacesInLintPaths() {
        // A deliberately collapse-prone child so a diagnostic is produced, then
        // confirm the label rides through into the reported path.
        Component tree = Ui.column()
            .add(Ui.column().fill().named("home-card"))
            .build();
        boolean labelled = Ui.check(tree).stream().anyMatch(d -> d.path().contains("home-card"));
        assertTrue(labelled, "the .named() label appears in the diagnostic path");
    }
}
