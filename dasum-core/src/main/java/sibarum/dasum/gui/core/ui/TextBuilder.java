package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.theme.Theme;
import sibarum.dasum.gui.core.theme.Variant;

/**
 * Fluent builder for {@link Component.Text}. Defaults the color to the theme's
 * {@code DEFAULT} emphasis shade (never null, so the glyph path can't NPE) and
 * auto-sizes from content; opt into wrapping / selection / a fixed width as
 * needed.
 */
public final class TextBuilder extends BaseBuilder<TextBuilder> {

    private final String content;
    private String fontGroup = FontGroups.DEFAULT;
    private Em fontSize = Em.of(1f);
    private Color color = Theme.of(Variant.DEFAULT).emphasis();
    private Em width;
    private Em height;
    private Em padding = Em.ZERO;
    private Em wrapWidth;          // null = no wrap (single line)
    private boolean selectable = false;

    TextBuilder(String content) {
        this.content = content == null ? "" : content;
    }

    TextBuilder(Component.Text from) {
        this.content = from.content();
        this.fontGroup = from.fontGroup();
        this.fontSize = from.fontSize();
        this.color = from.color();
        this.width = from.width();
        this.height = from.height();
        this.padding = from.padding();
        this.wrapWidth = from.wrapWidth();
        this.selectable = from.selectable();
        this.grow = from.flexGrow();
        this.interactive = from.interactive();
    }

    /** Font size in em. */
    public TextBuilder size(Em fontSize)  { this.fontSize = fontSize; return this; }
    public TextBuilder color(Color c)     { this.color = c; return this; }
    /** Color from a theme variant's emphasis shade. */
    public TextBuilder variant(Variant v) { this.color = Theme.of(v).emphasis(); return this; }
    public TextBuilder fontGroup(String g) { this.fontGroup = g; return this; }
    public TextBuilder width(Em w)        { this.width = w; return this; }
    public TextBuilder height(Em h)       { this.height = h; return this; }
    public TextBuilder padding(Em p)      { this.padding = p; return this; }
    /** Word-wrap at the given max line width. */
    public TextBuilder wrap(Em maxWidth)  { this.wrapWidth = maxWidth; return this; }
    public TextBuilder selectable()       { this.selectable = true; return this; }
    public TextBuilder selectable(boolean s) { this.selectable = s; return this; }

    @Override
    public Component build() {
        boolean isInteractive = interactive || selectable;
        Component.Text text = new Component.Text(
            content, fontGroup, fontSize, color,
            width, height, padding,
            wrapWidth, false, false, false,
            isInteractive, selectable, false, false, grow);
        return tagged(text);
    }
}
