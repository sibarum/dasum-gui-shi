package sibarum.dasum.gui.core.component;

import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

/**
 * Sealed tree of component descriptors. Components are pure-data records;
 * transient state (hover, focus, scroll, caret) lives in process-global
 * registries keyed by component identity.
 * <p>
 * Variants:
 * <ul>
 *   <li>{@link Box} — styled rectangle with optional fixed em size, used as
 *       leaf or single-child container.</li>
 *   <li>{@link Flex} — layout container; arranges children along ROW or
 *       COLUMN with justify-content / align-items / gap / per-child grow.</li>
 * </ul>
 * Both can be {@code interactive} (participate in hover/focus/hit testing)
 * and both can carry a {@code flexGrow} weight for when they're a child of
 * a Flex parent.
 */
public sealed interface Component permits Component.Box, Component.Flex, Component.Scroll, Component.Text, Component.Checkbox, Component.Radio, Component.Slider, Component.Tabs, Component.GraphSurface, Component.SceneView, Component.DataTable {

    /** Per-child flex weight; default 0 means the child takes its intrinsic size. */
    int flexGrow();

    /** Whether this component participates in hover/focus/hit testing. */
    boolean interactive();

    record Box(Em width, Em height, Em padding, Color color, List<Component> children, boolean interactive, int flexGrow) implements Component {

        public Box(Em width, Em height, Em padding, Color color) {
            this(width, height, padding, color, List.of(), false, 0);
        }

        public Box(Em width, Em height, Em padding, Color color, Component child) {
            this(width, height, padding, color, List.of(child), false, 0);
        }

        public Box(Em width, Em height, Em padding, Color color, List<Component> children) {
            this(width, height, padding, color, children, false, 0);
        }

        public Box withInteractive(boolean v) { return new Box(width, height, padding, color, children, v, flexGrow); }
        public Box withColor(Color c)         { return new Box(width, height, padding, c, children, interactive, flexGrow); }
        public Box withChildren(List<Component> kids) { return new Box(width, height, padding, color, kids, interactive, flexGrow); }
        public Box withFlexGrow(int g)        { return new Box(width, height, padding, color, children, interactive, g); }
    }

    /**
     * Layout container. {@code width}/{@code height} may be {@code null}
     * to mean "fill the parent's available extent on that axis".
     */
    record Flex(
        Em width, Em height, Em padding, Color color,
        Direction direction, JustifyContent justify, AlignItems align, Em gap,
        List<Component> children, boolean interactive, int flexGrow
    ) implements Component {

        public static Flex row(Em gap, List<Component> children) {
            return new Flex(null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
                Direction.ROW, JustifyContent.START, AlignItems.STRETCH, gap, children, false, 0);
        }

        public static Flex column(Em gap, List<Component> children) {
            return new Flex(null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
                Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, gap, children, false, 0);
        }

        public Flex withWidth(Em w)               { return new Flex(w, height, padding, color, direction, justify, align, gap, children, interactive, flexGrow); }
        public Flex withHeight(Em h)              { return new Flex(width, h, padding, color, direction, justify, align, gap, children, interactive, flexGrow); }
        public Flex withPadding(Em p)             { return new Flex(width, height, p, color, direction, justify, align, gap, children, interactive, flexGrow); }
        public Flex withColor(Color c)            { return new Flex(width, height, padding, c, direction, justify, align, gap, children, interactive, flexGrow); }
        public Flex withJustify(JustifyContent j) { return new Flex(width, height, padding, color, direction, j, align, gap, children, interactive, flexGrow); }
        public Flex withAlign(AlignItems a)       { return new Flex(width, height, padding, color, direction, justify, a, gap, children, interactive, flexGrow); }
        public Flex withFlexGrow(int g)           { return new Flex(width, height, padding, color, direction, justify, align, gap, children, interactive, g); }
    }

    /**
     * Scrollable viewport — content larger than the viewport scrolls via
     * the mouse wheel (and, later, drag). Per-instance scroll position
     * lives in {@code ScrollStates} keyed by identity.
     * <p>
     * {@code width} or {@code height} may be {@code null} to mean
     * "fill the parent's available extent on that axis" — same semantics
     * as {@link Flex}. A null-sized Scroll inside a flex parent picks up
     * stretch / grow from the parent's algorithm. If the child has no
     * explicit extent on an axis, it stretches to fill the Scroll's
     * interior on that axis (no scrolling there); otherwise the child's
     * explicit size is respected and overflow scrolls.
     */
    record Scroll(Em width, Em height, Em padding, Color color, Component child, boolean interactive, int flexGrow) implements Component {

        public Scroll(Em width, Em height, Em padding, Color color, Component child) {
            this(width, height, padding, color, child, false, 0);
        }

        public Scroll withFlexGrow(int g)    { return new Scroll(width, height, padding, color, child, interactive, g); }
        public Scroll withInteractive(boolean v) { return new Scroll(width, height, padding, color, child, v, flexGrow); }
    }

    /**
     * Text-rendering primitive — the foundation every wrapper (Label, Button,
     * TextInput, TextArea) sits on. Multi-line via embedded {@code '\n'}; no
     * automatic word wrap at this stage. Width/height may be {@code null} to
     * mean "auto-size from content + font metrics".
     *
     * @param content    the text to render; may contain {@code '\n'} for multi-line
     * @param fontGroup  registered {@link sibarum.dasum.gui.core.text.FontGroup} name
     *                   (e.g. {@code "primary"})
     * @param fontSize   em size — 1em ≈ the framework's root em scale
     * @param color      text colour (no alpha mixing yet beyond the MSDF coverage)
     */
    record Text(
        String content, String fontGroup, Em fontSize, Color color,
        Em width, Em height, Em padding,
        Em wrapWidth, boolean clip, boolean lineNumbers,
        boolean interactive, boolean selectable, boolean editable, boolean acceptsTab, int flexGrow
    ) implements Component {

        public Text(String content, Em fontSize, Color color) {
            this(content, sibarum.dasum.gui.core.text.FontGroups.DEFAULT, fontSize, color,
                 null, null, Em.ZERO, null, false, false, false, false, false, false, 0);
        }

        /** Pre-lineNumbers canonical shape — kept so existing positional call sites don't break. */
        public Text(String content, String fontGroup, Em fontSize, Color color,
                    Em width, Em height, Em padding,
                    Em wrapWidth, boolean clip,
                    boolean interactive, boolean selectable, boolean editable, boolean acceptsTab, int flexGrow) {
            this(content, fontGroup, fontSize, color, width, height, padding,
                 wrapWidth, clip, false, interactive, selectable, editable, acceptsTab, flexGrow);
        }

        public Text withInteractive(boolean v) { return new Text(content, fontGroup, fontSize, color, width, height, padding, wrapWidth, clip, lineNumbers, v, selectable, editable, acceptsTab, flexGrow); }
        public Text withFlexGrow(int g)        { return new Text(content, fontGroup, fontSize, color, width, height, padding, wrapWidth, clip, lineNumbers, interactive, selectable, editable, acceptsTab, g); }
        public Text withContent(String c)      { return new Text(c, fontGroup, fontSize, color, width, height, padding, wrapWidth, clip, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }
        public Text withColor(Color c)         { return new Text(content, fontGroup, fontSize, c, width, height, padding, wrapWidth, clip, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }
        public Text withWidth(Em w)            { return new Text(content, fontGroup, fontSize, color, w, height, padding, wrapWidth, clip, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }
        public Text withHeight(Em h)           { return new Text(content, fontGroup, fontSize, color, width, h, padding, wrapWidth, clip, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }
        public Text withFontGroup(String fg)   { return new Text(content, fg, fontSize, color, width, height, padding, wrapWidth, clip, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }
        public Text withPadding(Em p)          { return new Text(content, fontGroup, fontSize, color, width, height, p, wrapWidth, clip, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }

        /** Selectable implies interactive — selection requires hit-test + focus participation. */
        public Text withSelectable(boolean s)  { return new Text(content, fontGroup, fontSize, color, width, height, padding, wrapWidth, clip, lineNumbers, s || interactive, s, editable, acceptsTab, flexGrow); }

        /** Editable implies selectable + interactive — editing requires hit-test, focus, and selection. */
        public Text withEditable(boolean e)    { return new Text(content, fontGroup, fontSize, color, width, height, padding, wrapWidth, clip, lineNumbers, e || interactive, e || selectable, e, acceptsTab, flexGrow); }

        /**
         * When {@code true}, the Tab key inserts a literal {@code '\t'} character
         * instead of being consumed as focus cycling. Only meaningful when
         * {@code editable}.
         */
        public Text withAcceptsTab(boolean a)  { return new Text(content, fontGroup, fontSize, color, width, height, padding, wrapWidth, clip, lineNumbers, interactive, selectable, editable, a, flexGrow); }

        /**
         * Max line width in em. When non-null, the text wraps to fit:
         * preferred breaks at whitespace; fallback breaks at letter/punctuation
         * and letter/digit transitions; long unbreakable words overflow on
         * their current line rather than wasting an empty break.
         */
        public Text withWrapWidth(Em w)        { return new Text(content, fontGroup, fontSize, color, width, height, padding, w, clip, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }

        /** When {@code true}, glyphs that fall outside the text rect are scissor-clipped. */
        public Text withClip(boolean c)        { return new Text(content, fontGroup, fontSize, color, width, height, padding, wrapWidth, c, lineNumbers, interactive, selectable, editable, acceptsTab, flexGrow); }

        /**
         * When {@code true}, a line-number gutter is rendered left of the
         * content. Numbers count <em>logical</em> lines (split on {@code '\n'});
         * soft-wrapped continuation lines are unnumbered, matching editor
         * convention. Numbers render in the text color at reduced alpha.
         * <p>
         * The gutter sits between the left padding edge and the content, so
         * caret math, selection rects, and hit-testing shift right with it —
         * see {@code TextMetrics.gutterWidthPixels}. {@code wrapWidth} still
         * measures the text itself; the component's intrinsic width grows by
         * the gutter.
         */
        public Text withLineNumbers(boolean n) { return new Text(content, fontGroup, fontSize, color, width, height, padding, wrapWidth, clip, n, interactive, selectable, editable, acceptsTab, flexGrow); }
    }

    /**
     * Square stateful toggle. Holds a {@link Property Property&lt;Boolean&gt;}
     * — clicking flips it, the framework re-renders, and any subscribers
     * are notified. {@code boxColor} fills the whole square; when the
     * value is {@code true}, an inset rectangle in {@code checkColor} is
     * drawn over it.
     *
     * @param size       em side length (Checkbox is always square)
     * @param boxColor   background fill — visible whether checked or not
     * @param checkColor fill of the inset indicator drawn when value is true
     * @param value      reactive state cell; subscribe for change events
     */
    record Checkbox(Em size, Color boxColor, Color checkColor, Property<Boolean> value, boolean interactive, int flexGrow) implements Component {

        public Checkbox(Em size, Color boxColor, Color checkColor, Property<Boolean> value) {
            this(size, boxColor, checkColor, value, true, 0);
        }

        public Checkbox withFlexGrow(int g)        { return new Checkbox(size, boxColor, checkColor, value, interactive, g); }
        public Checkbox withInteractive(boolean v) { return new Checkbox(size, boxColor, checkColor, value, v, flexGrow); }
    }

    /**
     * Member of a radio group. Several radios share a single
     * {@code Property<T> group}; each radio carries its own {@code T value}.
     * Activating a radio sets the group property to that radio's value;
     * the radio renders its filled dot iff the group property currently
     * equals its value.
     * <p>
     * Visually distinguished from Checkbox by a tighter inset on the
     * inner indicator (no true circle rendering yet).
     *
     * @param size      em side length (Radio is always square)
     * @param boxColor  background fill
     * @param dotColor  inner-dot fill drawn when selected
     * @param group     shared selection cell — subscribe for change events
     * @param value     this radio's value; equality with {@code group.get()}
     *                  uses {@link java.util.Objects#equals}
     */
    record Radio<T>(Em size, Color boxColor, Color dotColor, Property<T> group, T value, boolean interactive, int flexGrow) implements Component {

        public Radio(Em size, Color boxColor, Color dotColor, Property<T> group, T value) {
            this(size, boxColor, dotColor, group, value, true, 0);
        }

        public boolean selected() {
            return java.util.Objects.equals(group.get(), value);
        }

        public Radio<T> withFlexGrow(int g)        { return new Radio<>(size, boxColor, dotColor, group, value, interactive, g); }
        public Radio<T> withInteractive(boolean v) { return new Radio<>(size, boxColor, dotColor, group, value, v, flexGrow); }
    }

    /**
     * Continuous range input. Bounding rect is {@code length × thickness}
     * along the orientation axis. The track is filled with
     * {@code trackColor}; the portion between the track origin and the
     * thumb is overlaid with {@code fillColor}; the thumb is a
     * {@code thumbThickness}-wide bar centered on the current value
     * position.
     * <p>
     * Drag updates the value continuously; releasing far off the slider
     * doesn't snap. Keyboard: arrows step by 1% of range, PageUp/Down by
     * 10%, Home/End jump to min/max.
     *
     * @param orientation       {@link Direction#ROW} = horizontal,
     *                          {@link Direction#COLUMN} = vertical
     * @param length            extent along the main axis
     * @param thickness         extent along the cross axis
     * @param thumbThickness    thumb's extent along the main axis
     * @param value             reactive value cell; subscribe for change events
     */
    record Slider(
        Direction orientation,
        Em length, Em thickness, Em thumbThickness,
        Color trackColor, Color fillColor, Color thumbColor,
        Property<Float> value, float min, float max,
        boolean interactive, int flexGrow
    ) implements Component {

        public Slider(Direction orientation, Em length, Em thickness, Em thumbThickness,
                      Color trackColor, Color fillColor, Color thumbColor,
                      Property<Float> value, float min, float max) {
            this(orientation, length, thickness, thumbThickness,
                 trackColor, fillColor, thumbColor, value, min, max, true, 0);
        }

        public boolean horizontal() { return orientation == Direction.ROW; }

        /** Current value mapped to [0, 1]. */
        public float fraction() {
            if (max <= min) return 0f;
            float f = (value.get() - min) / (max - min);
            return Math.max(0f, Math.min(1f, f));
        }

        public Slider withFlexGrow(int g)        { return new Slider(orientation, length, thickness, thumbThickness, trackColor, fillColor, thumbColor, value, min, max, interactive, g); }
        public Slider withInteractive(boolean v) { return new Slider(orientation, length, thickness, thumbThickness, trackColor, fillColor, thumbColor, value, min, max, v, flexGrow); }
    }

    /**
     * Tabbed container — a header strip of clickable tab labels above an
     * active content panel. Only the currently active tab's content is
     * laid out, rendered, and hit-tested, so inactive panels' interactive
     * components drop out of the Tab cycle naturally.
     * <p>
     * {@link TabPanel} pairs a label with a content component. Switching
     * is driven through {@link #activeIndex}: app code subscribes to that
     * {@code Property<Integer>} for change events, or sets it
     * programmatically to switch tabs.
     * <p>
     * Header click dispatch lives in
     * {@link sibarum.dasum.gui.core.input.TabsController} (scrollbar-style
     * sidecar for the synthesized tab cells); keyboard nav (Left/Right
     * when the Tabs container is focused) is handled by the App's key
     * callback via the same controller.
     *
     * @param width / height        null = fill parent's available extent on that axis
     * @param headerHeight          em height of the top tab strip
     * @param tabPadding            em horizontal padding inside each tab label cell
     * @param contentPadding        em padding between the strip's bottom and the active panel
     * @param headerBg              fill behind unselected tab cells
     * @param activeTabBg           fill of the active tab cell
     * @param tabFg                 label glyph color
     * @param contentBg             fill of the content area below the strip
     * @param tabFontSize           em label size
     * @param fontGroup             registered font group name for labels
     * @param tabs                  list of {@code (label, content)} pairs
     * @param activeIndex           reactive index of the currently-displayed tab
     */
    record Tabs(
        Em width, Em height,
        Em headerHeight, Em tabPadding, Em contentPadding,
        Color headerBg, Color activeTabBg, Color tabFg, Color contentBg,
        Em tabFontSize, String fontGroup,
        List<TabPanel> tabs,
        Property<Integer> activeIndex,
        boolean interactive, int flexGrow
    ) implements Component {

        public record TabPanel(String label, Component content) {}

        public Component activeContent() {
            int idx = activeIndex.get();
            if (idx < 0 || idx >= tabs.size()) return null;
            return tabs.get(idx).content();
        }

        public Tabs withFlexGrow(int g)        { return new Tabs(width, height, headerHeight, tabPadding, contentPadding, headerBg, activeTabBg, tabFg, contentBg, tabFontSize, fontGroup, tabs, activeIndex, interactive, g); }
        public Tabs withInteractive(boolean v) { return new Tabs(width, height, headerHeight, tabPadding, contentPadding, headerBg, activeTabBg, tabFg, contentBg, tabFontSize, fontGroup, tabs, activeIndex, v, flexGrow); }
    }

    /**
     * Absolute-positioned 2D surface — children float at {@code (x, y)}
     * positions held in {@code GraphSurfacePositions} keyed by
     * surface+child identity. Foundation for the node-editor: nodes are
     * regular components (Flex, Box, …) that get dragged around inside a
     * GraphSurface by {@code GraphSurfaceController}.
     * <p>
     * The name is deliberately not "Canvas" — that name is reserved for a
     * future drawing-API component. {@code GraphSurface} covers node
     * editors, flow diagrams, mind maps, dependency views, etc.
     * <p>
     * Children outside the surface's rect aren't clipped at this stage —
     * clipping comes when we add it to the surface's render model.
     *
     * @param width / height  surface extents in em; {@code null} on either
     *                        axis means "fill the parent's available extent"
     *                        on that axis — same semantics as {@code Flex}
     * @param color           background fill
     * @param children        positioned via {@code GraphSurfacePositions} sidecar
     * @param interactive     must be true for drag dispatch to consider its children
     */
    record GraphSurface(Em width, Em height, Color color, List<Component> children, boolean interactive, int flexGrow) implements Component {

        /** Convenience constructor — GraphSurface defaults to non-interactive
         *  so it stays out of the Tab cycle; only its children participate in focus. */
        public GraphSurface(Em width, Em height, Color color, List<Component> children) {
            this(width, height, color, children, false, 0);
        }

        public GraphSurface withFlexGrow(int g)        { return new GraphSurface(width, height, color, children, interactive, g); }
        public GraphSurface withInteractive(boolean v) { return new GraphSurface(width, height, color, children, v, flexGrow); }
    }

    /**
     * Leaf 3D viewport — a layered scene (points / lines / triangles,
     * painter's order) drawn through one camera. The record carries only
     * layout + appearance — scene content, camera, and interaction policy
     * are stored externally in {@code SceneStates} (in the
     * {@code dasum-vis} module), looked up by component identity.
     * <p>
     * Rendering and input handling are also owned by {@code dasum-vis};
     * {@code dasum-core} treats this variant as a leaf with a background
     * fill plus a delegated render hook (see
     * {@link sibarum.dasum.gui.core.render.CustomRenderers}). A
     * scene viewport that has no registered renderer is harmless —
     * it just draws as a flat box.
     *
     * @param width   em viewport width; {@code null} means fill parent
     * @param height  em viewport height; {@code null} means fill parent
     * @param padding inner padding inside the viewport rect
     * @param color   background fill (drawn beneath the scene)
     */
    record SceneView(
        Em width, Em height, Em padding, Color color,
        boolean interactive, int flexGrow
    ) implements Component {

        public SceneView(Em width, Em height, Em padding, Color color) {
            this(width, height, padding, color, true, 0);
        }

        public SceneView withFlexGrow(int g)        { return new SceneView(width, height, padding, color, interactive, g); }
        public SceneView withInteractive(boolean v) { return new SceneView(width, height, padding, color, v, flexGrow); }
    }

    /**
     * Spreadsheet-style data grid — virtualized rows / columns backed by a
     * {@link sibarum.dasum.gui.core.data.DataTableSource}. The variant
     * carries only layout + appearance; selection, scroll, edit, and hover
     * state live in {@code DataTableStates} keyed by component identity.
     * <p>
     * Rendering is delegated to a {@code DataTableRenderer} registered via
     * {@link sibarum.dasum.gui.core.render.CustomRenderers}, mirroring the
     * SceneView variant's hook. The framework treats DataTable as a leaf
     * for hit-test + layout — internal row / column / region resolution
     * happens inside the table's own controllers.
     *
     * @param width / height       null = fill parent's available extent on that axis
     * @param headerHeight         em height of the column-header strip (sticky top)
     * @param rowHeight            em height of each body row (uniform)
     * @param rowNumberColumnWidth em width of the row-number gutter (sticky left)
     * @param source               adapter providing row count + cell strings + mutators
     * @param headerBg / cellBgEven / cellBgOdd / gridLine / selectionFill / textColor — appearance
     * @param fontGroup            registered FontGroup name for cell glyphs (e.g. "primary")
     * @param fontSize             em size for cell + header text
     * @param selection            observable selection state — apps subscribe for change events;
     *                             the renderer + selection controller publish via this property
     */
    record DataTable(
        Em width, Em height,
        Em headerHeight, Em rowHeight, Em rowNumberColumnWidth,
        sibarum.dasum.gui.core.data.DataTableSource source,
        Color headerBg, Color cellBgEven, Color cellBgOdd,
        Color gridLine, Color selectionFill, Color textColor,
        String fontGroup, Em fontSize,
        Property<sibarum.dasum.gui.core.data.TableSelection> selection,
        boolean interactive, int flexGrow
    ) implements Component {

        /** Convenience constructor with sensible defaults. */
        public DataTable(Em width, Em height,
                         sibarum.dasum.gui.core.data.DataTableSource source,
                         Property<sibarum.dasum.gui.core.data.TableSelection> selection) {
            this(width, height,
                Em.of(1.8f), Em.of(1.6f), Em.of(3.5f),
                source,
                new Color(0.16f, 0.18f, 0.22f, 1f),
                new Color(0.10f, 0.12f, 0.15f, 1f),
                new Color(0.08f, 0.10f, 0.13f, 1f),
                new Color(0.22f, 0.24f, 0.30f, 1f),
                new Color(0.30f, 0.55f, 0.85f, 0.35f),
                new Color(0.92f, 0.94f, 0.97f, 1f),
                sibarum.dasum.gui.core.text.FontGroups.DEFAULT, Em.of(0.95f),
                selection, true, 0
            );
        }

        public DataTable withFlexGrow(int g)        { return new DataTable(width, height, headerHeight, rowHeight, rowNumberColumnWidth, source, headerBg, cellBgEven, cellBgOdd, gridLine, selectionFill, textColor, fontGroup, fontSize, selection, interactive, g); }
        public DataTable withInteractive(boolean v) { return new DataTable(width, height, headerHeight, rowHeight, rowNumberColumnWidth, source, headerBg, cellBgEven, cellBgOdd, gridLine, selectionFill, textColor, fontGroup, fontSize, selection, v, flexGrow); }
    }
}
