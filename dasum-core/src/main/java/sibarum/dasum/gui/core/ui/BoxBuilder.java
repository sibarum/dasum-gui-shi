package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.style.Border;
import sibarum.dasum.gui.core.style.BoxStyle;
import sibarum.dasum.gui.core.style.CornerRadii;

import java.util.List;

/**
 * Fluent builder for {@link Component.Box} - a fixed-size styled rectangle
 * that holds at most one child. Two guardrails the raw record lacks:
 * <ul>
 *   <li>A Box has no fill/fit layout path, so {@link #width(Em)} and
 *       {@link #height(Em)} are <b>required</b> - {@link #build()} throws a
 *       clear error if either is missing (rather than the record's later
 *       "needs a concrete Em" or a {@code NaN} rect).</li>
 *   <li>A Box centers <em>every</em> child on top of each other, so more than
 *       one child is meaningless - this builder accepts a single child and
 *       points multi-child callers at {@code Ui.row()/column()}.</li>
 * </ul>
 */
public final class BoxBuilder extends BaseBuilder<BoxBuilder> {

    private Em width;
    private Em height;
    private Em padding = Em.ZERO;
    private Color background = Color.TRANSPARENT;
    private CornerRadii radii;   // null = square
    private Border border;       // null = none
    private Component child;

    BoxBuilder() {}

    BoxBuilder(Component.Box from) {
        this.width = from.width();
        this.height = from.height();
        this.padding = from.padding();
        this.background = from.color();
        this.grow = from.flexGrow();
        this.interactive = from.interactive();
        BoxStyle s = from.style();
        if (s != null) { this.radii = s.radii(); this.border = s.border(); }
        List<Component> kids = from.children();
        this.child = (kids == null || kids.isEmpty()) ? null : kids.get(0);
    }

    public BoxBuilder width(Em w)        { this.width = w; return this; }
    public BoxBuilder height(Em h)       { this.height = h; return this; }
    public BoxBuilder size(Em w, Em h)   { this.width = w; this.height = h; return this; }
    public BoxBuilder padding(Em p)      { this.padding = p; return this; }
    public BoxBuilder background(Color c) { this.background = c; return this; }
    public BoxBuilder child(Component c)  { this.child = c; return this; }
    public BoxBuilder child(UiBuilder b)  { this.child = b.build(); return this; }

    /** Round all four corners uniformly. */
    public BoxBuilder cornerRadius(Em r) { this.radii = CornerRadii.all(r); return this; }
    /** Round corners individually (clockwise from top-left). */
    public BoxBuilder corners(Em tl, Em tr, Em br, Em bl) { this.radii = new CornerRadii(tl, tr, br, bl); return this; }
    /** Add an inset outline of the given width and color. */
    public BoxBuilder border(Em width, Color color) { this.border = Border.of(width, color); return this; }
    public BoxBuilder border(Border b) { this.border = b; return this; }

    @Override
    public Component build() {
        String who = label != null ? "Ui.box() '" + label + "'" : "Ui.box()";
        if (width == null || height == null) {
            throw new IllegalStateException(who
                + " needs both .width(...) and .height(...) - a styled box has a"
                + " concrete size; use Ui.row()/Ui.column() to fill or fit content");
        }
        List<Component> children = child == null ? List.of() : List.of(child);
        BoxStyle style = boxStyle(radii, border);
        Component.Box box = new Component.Box(width, height, padding, background, children, interactive, grow, style);
        return tagged(box);
    }

    /** Assemble a {@link BoxStyle} from optional radii/border, or null when neither is set. */
    static BoxStyle boxStyle(CornerRadii radii, Border border) {
        if (radii == null && border == null) return null;
        return new BoxStyle(radii != null ? radii : CornerRadii.NONE,
                            border != null ? border : Border.NONE);
    }
}
