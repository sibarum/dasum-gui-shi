package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

/**
 * Builder for a {@link Component.SceneView} — a viewport hosting custom-rendered content (a plot, a
 * point cloud, a 3D scene). A SceneView has NO intrinsic content size, so the collapse-prone default
 * for it is {@code fit} (→ 0), not {@code fill}. This builder therefore defaults to <b>fill both axes
 * + grow(1) + interactive</b> — a scene almost always wants to occupy its slot — so
 * {@code Ui.sceneView().background(c)} lays out correctly whether it is the whole window or one cell
 * of a column. Opt out with {@link #size}/{@link #fit} for the rare fixed-size viewport.
 */
public final class SceneViewBuilder extends BaseBuilder<SceneViewBuilder> {

    private Em width = null;      // null = fill; a scene fills its slot by default
    private Em height = null;
    private Em padding = Em.ZERO;
    private Color background = Color.TRANSPARENT;

    SceneViewBuilder() {
        this.grow = 1;            // take a share of leftover main-axis space (fills a flex cell)
        this.interactive = true;  // pan/zoom/orbit by default
    }

    /** Fill the parent on both axes (the default) — correct at the root or a grow/stretch slot. */
    public SceneViewBuilder fill() { this.width = null; this.height = null; return this; }
    /** Fixed size — a viewport that does NOT fill (rare; it has no content size to fit otherwise). */
    public SceneViewBuilder size(Em w, Em h) { this.width = w; this.height = h; return this; }
    /** Size to content on both axes ({@code Em.AUTO}); a SceneView has none, so this collapses — use
     *  only alongside an explicit {@link #width}/{@link #height}. */
    public SceneViewBuilder fit() { this.width = Em.AUTO; this.height = Em.AUTO; return this; }
    public SceneViewBuilder width(Em w)  { this.width = w; return this; }
    public SceneViewBuilder height(Em h) { this.height = h; return this; }

    public SceneViewBuilder padding(Em p)        { this.padding = p; return this; }
    public SceneViewBuilder background(Color c)  { this.background = c; return this; }

    @Override
    public Component build() {
        return tagged(new Component.SceneView(width, height, padding, background, interactive, grow));
    }
}
