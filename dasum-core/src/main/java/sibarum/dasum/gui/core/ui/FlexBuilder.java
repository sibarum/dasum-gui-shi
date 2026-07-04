package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link Component.Flex} - the row / column container.
 * <p>
 * Its whole reason for existing is to make the correct layout the default and
 * to name the axis intent explicitly, because the raw record's twin
 * {@code null} meanings are the framework's #1 footgun: a flex <em>child</em>
 * with a {@code null} main-axis size measures to <b>0</b> (collapse/overlap),
 * whereas {@code null} means "fill" only at the root. So this builder defaults
 * <b>both axes to fit-content</b> ({@link Em#AUTO}) - never the collapse-prone
 * {@code null}. Cross-axis filling still happens for free at layout time via the
 * parent's {@code STRETCH} align (a column stretches its children's width, a row
 * their height), so a {@code Ui.column()} of cards sizes to its content and
 * stretches to the pane width instead of stacking every child at {@code y=0}.
 * Opt into fill / grow explicitly at the root and elastic slots.
 * <ul>
 *   <li>{@link #fit()} - size to children ({@code Em.AUTO}) [default]</li>
 *   <li>{@link #fill()} - fill the parent on both axes ({@code null}); correct at
 *       the root or a grow slot, and flagged by the linter if used as a plain
 *       (grow=0) child, where it would collapse on the main axis</li>
 *   <li>{@link #grow(int)} - take a share of leftover main-axis space</li>
 *   <li>{@link #width(Em)} / {@link #height(Em)} - a fixed extent</li>
 * </ul>
 */
public final class FlexBuilder extends BaseBuilder<FlexBuilder> {

    private final Direction direction;
    private Em width;
    private Em height;
    private Em padding = Em.ZERO;
    private Em gap = Em.ZERO;
    private Color background = Color.TRANSPARENT;
    private JustifyContent justify = JustifyContent.START;
    private AlignItems align;
    private boolean wrap = false;
    private final List<Component> children = new ArrayList<>();

    FlexBuilder(Direction direction) {
        this.direction = direction;
        // Safe defaults: fit-content on BOTH axes (never the collapse-prone null).
        // The cross axis fills for free via the parent's STRETCH align at layout
        // time; making the cross null here would collapse this node when its
        // cross axis happens to be the parent's main axis (e.g. a row in a
        // column). Cross alignment matches the Flex.row/column factories.
        this.width = Em.AUTO;
        this.height = Em.AUTO;
        this.align = direction == Direction.ROW ? AlignItems.CENTER : AlignItems.STRETCH;
    }

    FlexBuilder(Component.Flex from) {
        this.direction = from.direction();
        this.width = from.width();
        this.height = from.height();
        this.padding = from.padding();
        this.gap = from.gap();
        this.background = from.color();
        this.justify = from.justify();
        this.align = from.align();
        this.wrap = from.wrap();
        this.grow = from.flexGrow();
        this.interactive = from.interactive();
        this.children.addAll(from.children());
    }

    // ---- sizing (intent-named; maps to null=fill / AUTO=fit / explicit) ----

    /** Fixed width. */
    public FlexBuilder width(Em w)  { this.width = w; return this; }
    /** Fixed height. */
    public FlexBuilder height(Em h) { this.height = h; return this; }
    /** Fixed width and height. */
    public FlexBuilder size(Em w, Em h) { this.width = w; this.height = h; return this; }
    /** Fill the parent on both axes ({@code null}); use at the root or a grow slot. */
    public FlexBuilder fill() { this.width = null; this.height = null; return this; }
    /** Fit content on both axes ({@code Em.AUTO}) - the default. */
    public FlexBuilder fit()  { this.width = Em.AUTO; this.height = Em.AUTO; return this; }

    // ---- styling ----

    public FlexBuilder padding(Em p)             { this.padding = p; return this; }
    public FlexBuilder gap(Em g)                 { this.gap = g; return this; }
    public FlexBuilder background(Color c)       { this.background = c; return this; }
    public FlexBuilder justify(JustifyContent j) { this.justify = j; return this; }
    public FlexBuilder align(AlignItems a)       { this.align = a; return this; }
    /** Wrap children onto multiple lines when they overflow (ROW only). */
    public FlexBuilder wrap()                    { this.wrap = true; return this; }
    public FlexBuilder wrap(boolean w)           { this.wrap = w; return this; }

    // ---- children (accept raw records or nested builders) ----

    public FlexBuilder add(Component child)      { children.add(child); return this; }
    public FlexBuilder add(UiBuilder child)      { children.add(child.build()); return this; }
    public FlexBuilder add(Component... kids)    { for (Component c : kids) children.add(c); return this; }
    public FlexBuilder addAll(List<Component> kids) { children.addAll(kids); return this; }

    @Override
    public Component build() {
        Component.Flex flex = new Component.Flex(
            width, height, padding, background,
            direction, justify, align, gap,
            List.copyOf(children), interactive, grow, wrap);
        return tagged(flex);
    }
}
