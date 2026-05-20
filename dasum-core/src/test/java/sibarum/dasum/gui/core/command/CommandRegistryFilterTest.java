package sibarum.dasum.gui.core.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandRegistryFilterTest {

    @AfterEach
    void tearDown() {
        // Crude but adequate — the registry doesn't expose clear, so
        // each test unregisters whatever IDs it registered. Tests below
        // use a consistent ID prefix to make this self-contained.
        for (String id : List.of("demo.visualizer", "zoom.in", "test.alpha", "test.beta")) {
            CommandRegistry.unregister(id);
        }
    }

    @Test
    void filterMatchesExactTernionLabel() {
        // The actual label registered in Ternion's TsApp.registerCommands.
        CommandRegistry.register("demo.visualizer",
            "Demo: Visualizer scenario (Parameter → Visualizer → Terminal)",
            () -> {});

        List<CommandRegistry.Command> demo = CommandRegistry.filter("demo");
        List<CommandRegistry.Command> Demo = CommandRegistry.filter("Demo");
        List<CommandRegistry.Command> visualizer = CommandRegistry.filter("visualizer");

        assertEquals(1, demo.size(), "Filter 'demo' should match label starting with 'Demo:'");
        assertEquals(1, Demo.size(), "Case-insensitive: 'Demo' matches");
        assertEquals(1, visualizer.size(), "Filter 'visualizer' should match middle-of-label substring");
    }

    @Test
    void filterIsCaseInsensitive() {
        CommandRegistry.register("test.alpha", "Run Alpha Diagnostic", () -> {});
        assertEquals(1, CommandRegistry.filter("alpha").size());
        assertEquals(1, CommandRegistry.filter("ALPHA").size());
        assertEquals(1, CommandRegistry.filter("Alpha").size());
        assertEquals(1, CommandRegistry.filter("AlPhA").size());
    }

    @Test
    void filterDoesNotMatchId() {
        // Per the docstring's stated contract: substring on label only.
        // If this ever changes (e.g. to also match id), this test fires
        // and signals the docs need updating.
        CommandRegistry.register("test.beta", "Some Unrelated Label", () -> {});
        List<CommandRegistry.Command> hits = CommandRegistry.filter("beta");
        assertEquals(0, hits.size(), "Filter must not match against id");
    }

    @Test
    void emptyQueryReturnsAll() {
        CommandRegistry.register("test.alpha", "Alpha", () -> {});
        CommandRegistry.register("test.beta",  "Beta",  () -> {});
        assertTrue(CommandRegistry.filter("").size() >= 2);
        assertTrue(CommandRegistry.filter("   ").size() >= 2);
        assertTrue(CommandRegistry.filter(null).size() >= 2);
    }
}
