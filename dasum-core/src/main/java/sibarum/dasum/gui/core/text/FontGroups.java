package sibarum.dasum.gui.core.text;

import java.util.HashMap;
import java.util.Map;

/**
 * Process-global registry of named font groups. Apps register groups at
 * startup; text components look them up by name during layout/render.
 * <p>
 * The default group is {@code "primary"} — a Text component with no
 * explicit fontGroup falls back to that name.
 */
public final class FontGroups {

    public static final String DEFAULT = "primary";

    private static final Map<String, FontGroup> GROUPS = new HashMap<>();

    private FontGroups() {}

    public static void register(FontGroup group) {
        GROUPS.put(group.name(), group);
    }

    public static FontGroup get(String name) {
        FontGroup g = GROUPS.get(name);
        if (g == null) throw new IllegalStateException("No FontGroup registered for name '" + name + "'");
        return g;
    }

    public static FontGroup getOrDefault(String name) {
        FontGroup g = GROUPS.get(name);
        if (g != null) return g;
        return get(DEFAULT);
    }

    public static boolean isRegistered(String name) {
        return GROUPS.containsKey(name);
    }
}
