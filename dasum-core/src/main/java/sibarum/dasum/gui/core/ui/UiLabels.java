package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Process-global, identity-keyed registry of the debug labels set via
 * {@code .named(...)} on a {@link UiBuilder}. Labels are a build-time
 * convenience only — they live here rather than on the immutable
 * {@link Component} records (mirroring how the framework keeps hover/focus/
 * scroll state in side registries keyed by component identity), so that
 * {@link LayoutLint} can print readable node paths like
 * {@code root > column > card 'home'} without changing the record API.
 */
final class UiLabels {

    private static final Map<Component, String> LABELS =
        Collections.synchronizedMap(new IdentityHashMap<>());

    private UiLabels() {}

    static void set(Component c, String label) {
        if (c != null && label != null) LABELS.put(c, label);
    }

    /** The label registered for {@code c}, or {@code null} if none. */
    static String get(Component c) {
        return c == null ? null : LABELS.get(c);
    }
}
