package sibarum.dasum.gui.core.nav;

import sibarum.dasum.gui.core.command.CommandRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Process-global directory of named navigation destinations. An app registers
 * the human-readable destinations it wants reachable; {@link Navigator#navigate(String)}
 * does the heavy lifting of getting there (switching tabs, scrolling into view,
 * focusing).
 * <p>
 * Modeled on {@link CommandRegistry}: insertion order is preserved so the
 * default listing matches registration order, and {@link #filter(String)} is a
 * case-insensitive substring match on the label.
 * <p>
 * The label lives here, separately from the {@link NavId} tag on the component,
 * so destinations can be listed even while their component isn't currently in
 * the tree (e.g. its tab has never been opened). Registering a destination also
 * publishes a {@code "Go to: <label>"} command into {@link CommandRegistry} so
 * destinations surface in the Everything Menu with no menu-side changes.
 */
public final class NavRegistry {

    public record Destination(String id, String label) {}

    private static final Map<String, Destination> DESTINATIONS = new LinkedHashMap<>();

    private NavRegistry() {}

    public static void register(String id, String label) {
        DESTINATIONS.put(id, new Destination(id, label));
        CommandRegistry.register("nav." + id, "Go to: " + label, () -> Navigator.navigate(id));
    }

    public static void unregister(String id) {
        DESTINATIONS.remove(id);
        CommandRegistry.unregister("nav." + id);
    }

    public static List<Destination> all() {
        return new ArrayList<>(DESTINATIONS.values());
    }

    /** Case-insensitive substring filter on {@code label}. Blank query returns {@link #all()}. */
    public static List<Destination> filter(String query) {
        if (query == null || query.isBlank()) return all();
        String q = query.toLowerCase();
        List<Destination> out = new ArrayList<>();
        for (Destination d : DESTINATIONS.values()) {
            if (d.label().toLowerCase().contains(q)) out.add(d);
        }
        return out;
    }
}
