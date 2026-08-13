package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.TextWrapStates;
import sibarum.dasum.gui.core.theme.Theme;
import sibarum.dasum.gui.core.theme.Variant;

/**
 * Fluent builder for {@link Component.Text}. Defaults the color to the theme's
 * {@code DEFAULT} emphasis shade (never null, so the glyph path can't NPE) and
 * auto-sizes from content; opt into wrapping / selection / editing / a fixed
 * width as needed.
 *
 * <p>{@link #editable()} makes the Text an input field: it forces
 * interactive+selectable on (the caret/key pipeline requires both) and, because
 * an <em>empty</em> editable Text has no glyphs and so no intrinsic width, gives
 * it a non-collapsing default width when none was set explicitly — the same
 * "never the collapse-prone default" guardrail the flex builder applies. This is
 * the blessed way to build an input; callers should not touch the raw
 * {@code new Component.Text(...)} constructor (which offers no such guardrail).
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
    private boolean editable = false;
    private boolean acceptsTab = false;
    private boolean clip = false;
    private boolean wordWrap = false;  // runtime soft-wrap toggle, initial state

    /** Default width for an empty editable field with no explicit width — so it doesn't collapse to
     *  a zero-width (invisible, unclickable) rect. Callers override with {@link #width(Em)}. */
    private static final Em DEFAULT_EDITABLE_WIDTH = Em.of(12f);

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
        this.clip = from.clip();
        this.selectable = from.selectable();
        this.editable = from.editable();
        this.acceptsTab = from.acceptsTab();
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
    /**
     * Start with runtime word-wrap on — soft-wraps to the field's own
     * laid-out content width (re-wrapping on resize), independent of any
     * fixed {@link #wrap(Em)}. Flip at runtime with
     * {@link TextWrapStates#setWordWrap} / {@link TextWrapStates#toggleWordWrap}
     * (or Alt+Z on the focused editable field).
     */
    public TextBuilder wordWrap()            { this.wordWrap = true; return this; }
    public TextBuilder wordWrap(boolean on)  { this.wordWrap = on; return this; }
    /** Clip glyphs to the text's box (so overflowing content doesn't bleed past a fixed/grown width). */
    public TextBuilder clip()             { this.clip = true; return this; }
    public TextBuilder clip(boolean c)    { this.clip = c; return this; }
    public TextBuilder selectable()       { this.selectable = true; return this; }
    public TextBuilder selectable(boolean s) { this.selectable = s; return this; }
    /** Make this an editable input field (forces interactive+selectable; see the class doc). */
    public TextBuilder editable()         { this.editable = true; return this; }
    public TextBuilder editable(boolean e) { this.editable = e; return this; }
    /** Let Tab insert a tab character rather than cycling focus (off by default — a single-line
     *  field wants Tab to move focus). Only meaningful together with {@link #editable()}. */
    public TextBuilder acceptsTab(boolean a) { this.acceptsTab = a; return this; }

    @Override
    public Component build() {
        // editable requires the full caret/selection pipeline: interactive + selectable both on.
        boolean isSelectable = selectable || editable;
        boolean isInteractive = interactive || isSelectable;
        // An empty editable field has no glyphs → no intrinsic width; default one so it doesn't
        // collapse to an invisible, unclickable rect (mirrors the flex builder's anti-collapse rule).
        // Skip when the field grows — a grow>0 slot takes its width from the flex share, so null is
        // correct there and forcing a fixed default would break the intended fill.
        Em resolvedWidth = (editable && width == null && grow == 0) ? DEFAULT_EDITABLE_WIDTH : width;
        Component.Text text = new Component.Text(
            content, fontGroup, fontSize, color,
            resolvedWidth, height, padding,
            wrapWidth, clip, false, false,
            isInteractive, isSelectable, editable, acceptsTab, grow);
        // Runtime word-wrap is sidecar state keyed by the built instance —
        // seed it here so wordWrap() takes effect from the first frame.
        if (wordWrap) TextWrapStates.setWordWrap(text, true);
        return tagged(text);
    }
}
