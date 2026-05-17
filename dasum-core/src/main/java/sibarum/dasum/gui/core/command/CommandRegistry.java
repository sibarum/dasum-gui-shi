package sibarum.dasum.gui.core.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Process-global registry of named, label-described actions surfaced by
 * the {@link EverythingMenu}. Insertion order is preserved so the menu's
 * default (empty-query) ordering matches registration order.
 * <p>
 * Match strategy is case-insensitive substring on the label — fuzzy
 * ranking can layer on top later without changing the registration API.
 */
public final class CommandRegistry {

    public record Command(String id, String label, Runnable action) {}

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    private CommandRegistry() {}

    public static void register(String id, String label, Runnable action) {
        COMMANDS.put(id, new Command(id, label, action));
    }

    public static void unregister(String id) {
        COMMANDS.remove(id);
    }

    /** Run the command registered under {@code id}, if any. No-op when unknown. */
    public static void invoke(String id) {
        Command c = COMMANDS.get(id);
        if (c != null) c.action().run();
    }

    public static List<Command> all() {
        return new ArrayList<>(COMMANDS.values());
    }

    /** Case-insensitive substring filter on {@code label}. Blank query returns {@link #all()}. */
    public static List<Command> filter(String query) {
        if (query == null || query.isBlank()) return all();
        String q = query.toLowerCase();
        List<Command> out = new ArrayList<>();
        for (Command c : COMMANDS.values()) {
            if (c.label().toLowerCase().contains(q)) out.add(c);
        }
        return out;
    }
}
