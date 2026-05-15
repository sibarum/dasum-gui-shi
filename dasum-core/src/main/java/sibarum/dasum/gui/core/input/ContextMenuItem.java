package sibarum.dasum.gui.core.input;

/**
 * A single entry in a context menu produced by a {@link ContextMenuProvider}.
 * <p>
 * Two flavors:
 * <ul>
 *   <li>Normal item — {@code label}, an {@code enabled} flag, and a
 *       {@code Runnable action}. Disabled items render dimmed and are
 *       skipped by keyboard navigation.</li>
 *   <li>Separator — visual divider with no label or action; produced via
 *       {@link #separator()}.</li>
 * </ul>
 * <p>
 * Visibility vs. enabled: the convention is "omit when not applicable to
 * this target; grey-out when applicable but not currently actionable"
 * (e.g. Paste exists on an editable Text but is disabled when the clipboard
 * is empty).
 */
public record ContextMenuItem(String label, boolean enabled, Runnable action, boolean isSeparator) {

    public ContextMenuItem(String label, Runnable action) {
        this(label, true, action, false);
    }

    public ContextMenuItem(String label, boolean enabled, Runnable action) {
        this(label, enabled, action, false);
    }

    private static final ContextMenuItem SEPARATOR =
        new ContextMenuItem("", false, () -> {}, true);

    /** Visual divider between groups of items. Skipped by keyboard nav. */
    public static ContextMenuItem separator() {
        return SEPARATOR;
    }
}
