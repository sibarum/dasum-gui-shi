package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in context menu for selectable / editable {@link Component.Text}.
 * Used by {@link ContextMenuController} as a fallback when no explicit
 * {@link ContextMenuProvider} is registered on the right-clicked text.
 * <p>
 * Items follow the convention "omit when not applicable; grey-out when
 * applicable but not actionable":
 * <ul>
 *   <li><b>Cut</b> — included iff editable and a non-empty selection exists.</li>
 *   <li><b>Copy</b> — included iff selectable and a non-empty selection exists.</li>
 *   <li><b>Paste</b> — included iff editable; enabled iff the clipboard is non-empty.</li>
 *   <li><b>Select All</b> — included iff selectable; enabled iff content is non-empty.</li>
 * </ul>
 * <p>
 * Apps that want to augment the defaults — adding their own items above or
 * below — should call {@link #defaultItems} from a custom
 * {@link ContextMenuProvider} and concatenate. Opting out is a registration
 * that returns an empty list (or {@link Handlers#disableContextMenu}).
 */
public final class TextContextMenu {

    private TextContextMenu() {}

    /** Provider invoked by the dispatcher for selectable Text without explicit registration. */
    public static ContextMenuProvider defaultProvider() {
        return event -> {
            if (!(event.target() instanceof Component.Text text) || !text.selectable()) {
                return List.of();
            }
            return defaultItems(text, event);
        };
    }

    /**
     * Build the default item list for {@code text}. Exposed so apps can
     * compose their own provider on top of the defaults — see class doc.
     */
    public static List<ContextMenuItem> defaultItems(Component.Text text, ContextEvent event) {
        TextState ts = TextStates.of(text);
        boolean hasSelection = ts.hasSelection();
        boolean nonEmptyContent = !TextStates.contentOf(text).isEmpty();
        boolean clipboardNonEmpty = !clipboardEmpty(event.windowHandle());

        List<ContextMenuItem> out = new ArrayList<>(5);

        if (text.editable() && hasSelection) {
            out.add(new ContextMenuItem("Cut", () -> TextInputController.onCut(event.windowHandle())));
        }
        if (text.selectable() && hasSelection) {
            out.add(new ContextMenuItem("Copy", () -> TextInputController.onCopy(event.windowHandle())));
        }
        if (text.editable()) {
            out.add(new ContextMenuItem("Paste", clipboardNonEmpty,
                () -> TextInputController.onPaste(event.windowHandle())));
        }
        boolean hasUpperGroup = !out.isEmpty();
        if (text.selectable()) {
            if (hasUpperGroup) out.add(ContextMenuItem.separator());
            out.add(new ContextMenuItem("Select All", nonEmptyContent,
                TextInputController::onSelectAll));
        }
        return out;
    }

    private static boolean clipboardEmpty(MemorySegment win) {
        String s = Glfw.glfwGetClipboardString(win);
        return s == null || s.isEmpty();
    }
}
