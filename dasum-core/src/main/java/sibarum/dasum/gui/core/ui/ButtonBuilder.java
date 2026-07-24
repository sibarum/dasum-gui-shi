package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.style.Border;
import sibarum.dasum.gui.core.style.BoxStyle;
import sibarum.dasum.gui.core.style.CornerRadii;
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
    private CornerRadii radii;           // null = keep the theme default radius
    private Border border;               // null = no border

    ButtonBuilder(String label) { this.label = label == null ? "" : label; }

    public ButtonBuilder width(Em w)        { this.width = w; return this; }
    public ButtonBuilder variant(Variant v) { this.variant = v; return this; }

    /** Override the theme's default corner radius with a uniform radius. */
    public ButtonBuilder cornerRadius(Em r) { this.radii = CornerRadii.all(r); return this; }
    /** Override the corners individually (clockwise from top-left). */
    public ButtonBuilder corners(Em tl, Em tr, Em br, Em bl) { this.radii = new CornerRadii(tl, tr, br, bl); return this; }
    /** Add an inset outline of the given width and color. */
    public ButtonBuilder border(Em width, Color color) { this.border = Border.of(width, color); return this; }
    public ButtonBuilder border(Border b) { this.border = b; return this; }

    /**
     * Merge the builder's style overrides onto the themed button. Applied
     * BEFORE any click handler is wired, so the handler lands on the final
     * record identity (avoiding the stranded-handler bug documented on
     * {@link Themed}).
     */
    private Component styled() {
        Component b = Themed.button(label, width, variant, grow);
        if ((radii == null && border == null) || !(b instanceof Component.Flex f)) {
            return b;
        }
        BoxStyle base = f.style() != null ? f.style() : BoxStyle.NONE;
        CornerRadii cr = radii  != null ? radii  : base.radii();
        Border      bd = border != null ? border : base.border();
        return f.withStyle(new BoxStyle(cr, bd));
    }

    /** Build the button and wire its click handler in one step; returns the record. */
    public Component onClick(Runnable handler) {
        Component b = styled();
        sibarum.dasum.gui.core.input.Handlers.onClick(b, handler);
        return tagged(b);
    }

    @Override
    public Component build() {
        return tagged(styled());
    }
}
