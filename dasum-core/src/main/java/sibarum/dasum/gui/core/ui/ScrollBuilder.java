package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

/**
 * Fluent builder for {@link Component.Scroll} — a scrollable viewport around a
 * single child. Defaults both axes to fill ({@code null}); set a fixed extent
 * on the axis you want to bound and scroll. Typically paired with
 * {@code .grow(1)} as the elastic content region of a pane.
 */
public final class ScrollBuilder extends BaseBuilder<ScrollBuilder> {

    private Em width;             // null = fill
    private Em height;            // null = fill
    private Em padding = Em.ZERO;
    private Color background = Color.TRANSPARENT;
    private Component child;

    ScrollBuilder(Component child) { this.child = child; }

    ScrollBuilder(Component.Scroll from) {
        this.width = from.width();
        this.height = from.height();
        this.padding = from.padding();
        this.background = from.color();
        this.child = from.child();
        this.grow = from.flexGrow();
        this.interactive = from.interactive();
    }

    public ScrollBuilder width(Em w)        { this.width = w; return this; }
    public ScrollBuilder height(Em h)       { this.height = h; return this; }
    public ScrollBuilder padding(Em p)      { this.padding = p; return this; }
    public ScrollBuilder background(Color c) { this.background = c; return this; }
    public ScrollBuilder child(Component c)  { this.child = c; return this; }
    public ScrollBuilder child(UiBuilder b)  { this.child = b.build(); return this; }

    @Override
    public Component build() {
        Component.Scroll scroll = new Component.Scroll(width, height, padding, background, child, interactive, grow);
        return tagged(scroll);
    }
}
