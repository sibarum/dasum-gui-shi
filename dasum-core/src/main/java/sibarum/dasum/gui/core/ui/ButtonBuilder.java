package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.theme.Themed;
import sibarum.dasum.gui.core.theme.Variant;

/**
 * Fluent builder for a themed button (a styled, interactive {@link Component.Flex}
 * with a centered label — there is no {@code Button} record). Delegates to
 * {@link Themed#button} so colors follow the active theme. Prefer the terminal
 * {@link #onClick(Runnable)} over {@code build()} + a separate handler
 * registration: it wires the handler to the button's final identity in one
 * step, avoiding the stranded-handler bug documented on {@code Themed}.
 */
public final class ButtonBuilder extends BaseBuilder<ButtonBuilder> {

    private final String label;
    private Em width = Em.AUTO;          // fit the label by default
    private Variant variant = Variant.DEFAULT;

    ButtonBuilder(String label) { this.label = label == null ? "" : label; }

    public ButtonBuilder width(Em w)        { this.width = w; return this; }
    public ButtonBuilder variant(Variant v) { this.variant = v; return this; }

    /** Build the button and wire its click handler in one step; returns the record. */
    public Component onClick(Runnable handler) {
        Component b = Themed.button(label, width, variant, grow, handler);
        return tagged(b);
    }

    @Override
    public Component build() {
        Component b = Themed.button(label, width, variant, grow);
        return tagged(b);
    }
}
