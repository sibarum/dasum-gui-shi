package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.vis.math.CameraMode;
import sibarum.dasum.gui.vis.math.CameraRig;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.scene.ImageLayer;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.SceneStates;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Thin controller that binds a plot model to a {@code Component.SceneView}:
 * frames an orthographic camera to the plot's world rectangle, publishes the
 * layers, and (because tick density should track zoom) re-publishes with
 * adjusted tick counts whenever the camera changes.
 *
 * <p>All updates go through the lock-free {@link SceneStates#publish} —
 * layers are immutable and never mutated in place, so a worker thread can
 * call {@link #showLinePlot}/{@link #showFieldMap} freely while the renderer
 * reads on the GLFW thread.
 */
public final class PlotView {

    private final Component.SceneView view;

    /** Builds the current scene as a function of the live ortho zoom (half-height). */
    private volatile Function<Float, List<Layer>> builder;
    /** The zoom the plot was framed at — the reference for tick-density scaling. */
    private volatile float baseScale = 1f;

    public PlotView(Component.SceneView view) {
        if (view == null) throw new IllegalArgumentException("view != null");
        this.view = view;
        // Re-tick on zoom/pan. We only publish here (never setCamera), so this
        // cannot recurse into the camera-change dispatch.
        SceneStates.onCameraChange(view, cam -> {
            Function<Float, List<Layer>> b = builder;
            if (b != null && cam.mode() == CameraMode.ORTHOGRAPHIC) {
                SceneStates.publish(view, new SceneSnapshot(b.apply(cam.orthoScale())));
            }
        });
    }

    public Component.SceneView view() { return view; }

    /** Publish a line/curve chart; the camera frames the plot and ticks track zoom. */
    public void showLinePlot(PlotFrame frame, List<Series> series, PlotStyle style) {
        List<Series> seriesCopy = List.copyOf(series);
        builder = zoom -> LinePlot.build(frame, seriesCopy, retick(style, zoom));
        frameTo(frame); // setCamera fires the listener, which performs the first publish
    }

    /**
     * Publish a 2D complex false-colour map (rasterized once) with frame
     * chrome on top. The chrome re-ticks on zoom; the image layer instance is
     * reused across re-publishes, so its GPU texture is never re-uploaded.
     */
    public void showFieldMap(PlotFrame frame, ComplexField2D field, ComplexColorMap map, PlotStyle style) {
        ImageLayer image = FieldRaster.imageLayer(field, map, frame);
        builder = zoom -> {
            List<Layer> layers = new ArrayList<>();
            layers.add(image);
            layers.addAll(frame.chrome(retick(style, zoom)));
            return layers;
        };
        frameTo(frame);
    }

    /** How a continuous-function field map chooses its rasterization resolution. */
    public enum RenderResolution {
        /** Rasterize once over the whole frame at a fixed baseline resolution. */
        FIXED,
        /**
         * Match the viewport: re-rasterize the <em>visible</em> world rect at
         * the viewport's pixel density on every resize, zoom, and pan, so
         * zooming in reveals real function detail rather than magnified texels.
         */
        DISPLAY
    }

    /** Baseline grid for {@link RenderResolution#FIXED}. */
    private static final int FIXED_RES = 512;
    /** Fallback pixel size before the viewport has rendered once. */
    private static final int DEFAULT_PX = 512;
    /** Upper bound on samples per axis, so a deep zoom can't request a huge buffer. */
    private static final int MAX_RES_PER_AXIS = 2048;

    /**
     * Publish a 2D complex false-colour map from a continuous
     * {@link ComplexFunction}, choosing its rasterization resolution per
     * {@code mode}. With {@link RenderResolution#DISPLAY} the visible region
     * is re-sampled at the viewport's pixel density whenever the size, zoom,
     * or pan changes — driven by {@link SceneStates#onViewportResize} and the
     * camera-change listener wired in the constructor. All re-rasterization
     * runs on the GLFW thread inside those listeners and publishes lock-free.
     */
    public void showFieldMap(PlotFrame frame, ComplexFunction fn, ComplexColorMap map,
                             PlotStyle style, RenderResolution mode) {
        if (frame == null || fn == null || map == null || style == null || mode == null) {
            throw new IllegalArgumentException("required args != null");
        }
        if (mode == RenderResolution.FIXED) {
            ImageLayer image = FieldRaster.functionTile(fn, map, frame,
                frame.wx0(), frame.wy0(), frame.wx1(), frame.wy1(), FIXED_RES, FIXED_RES, false);
            builder = zoom -> {
                List<Layer> layers = new ArrayList<>();
                layers.add(image);
                layers.addAll(frame.chrome(retick(style, zoom)));
                return layers;
            };
            frameTo(frame);
            return;
        }
        // DISPLAY: the builder ignores its zoom arg and reads the live camera +
        // viewport size, so the camera-change and resize listeners both route
        // through it. Resize fires from the renderer; the constructor's
        // camera listener handles zoom/pan and the initial publish in frameTo.
        builder = zoom -> buildDisplayResLayers(frame, fn, map, style);
        SceneStates.onViewportResize(view, sz -> {
            Function<Float, List<Layer>> b = builder;
            if (b != null) SceneStates.publish(view, new SceneSnapshot(b.apply(0f)));
        });
        frameTo(frame);
    }

    private List<Layer> buildDisplayResLayers(PlotFrame frame, ComplexFunction fn,
                                              ComplexColorMap map, PlotStyle style) {
        CameraSpec cam = SceneStates.cameraOf(view);
        SceneStates.ViewportPx px = SceneStates.viewportPxOf(view);
        int pxW = px != null ? px.width()  : DEFAULT_PX;
        int pxH = px != null ? px.height() : DEFAULT_PX;

        List<Layer> layers = new ArrayList<>();
        Tile t = displayTile(frame, cam.target().x(), cam.target().y(), cam.orthoScale(),
                             pxW, pxH, MAX_RES_PER_AXIS);
        if (t != null) {
            layers.add(FieldRaster.functionTile(fn, map, frame,
                t.x0(), t.y0(), t.x1(), t.y1(), t.w(), t.h(), true));
        }
        layers.addAll(frame.chrome(retick(style, cam.orthoScale())));
        return layers;
    }

    /** The world sub-rect to rasterize and how many samples it gets. */
    record Tile(float x0, float y0, float x1, float y1, int w, int h) {}

    /**
     * Visible-rect tile for display-resolution rasterization: the ortho
     * camera's visible world rectangle clipped to the frame, sized so the
     * clipped region gets roughly one sample per on-screen pixel (capped).
     * Pure; package-visible for tests. Returns {@code null} when the camera
     * has panned the frame entirely out of view.
     *
     * @param orthoScale half the visible world height (ortho half-extent)
     */
    static Tile displayTile(PlotFrame f, float targetX, float targetY, float orthoScale,
                            int pxW, int pxH, int cap) {
        if (pxW <= 0 || pxH <= 0 || !(orthoScale > 0f)) return null;
        float halfH = orthoScale;
        float halfW = orthoScale * ((float) pxW / pxH);
        float vx0 = targetX - halfW, vx1 = targetX + halfW;
        float vy0 = targetY - halfH, vy1 = targetY + halfH;

        float tx0 = Math.max(vx0, f.wx0()), tx1 = Math.min(vx1, f.wx1());
        float ty0 = Math.max(vy0, f.wy0()), ty1 = Math.min(vy1, f.wy1());
        if (!(tx1 > tx0) || !(ty1 > ty0)) return null;

        int w = clampRes(Math.round(pxW * (tx1 - tx0) / (vx1 - vx0)), cap);
        int h = clampRes(Math.round(pxH * (ty1 - ty0) / (vy1 - vy0)), cap);
        return new Tile(tx0, ty0, tx1, ty1, w, h);
    }

    private static int clampRes(int v, int cap) {
        return Math.max(1, Math.min(cap, v));
    }

    /** Publish an arbitrary static layer list and frame the camera to {@code frame}. */
    public void show(PlotFrame frame, List<Layer> layers) {
        List<Layer> snapshot = List.copyOf(layers);
        builder = zoom -> snapshot;
        frameTo(frame);
    }

    /** Point the ortho camera at {@code frame} and record the base zoom. */
    private void frameTo(PlotFrame frame) {
        CameraSpec spec = CameraRig.fitToBounds(CameraSpec.defaultOrtho(), frame.worldMin(), frame.worldMax());
        baseScale = spec.orthoScale();
        SceneStates.setCamera(view, spec);
    }

    /** Scale the tick targets with zoom: closer (smaller orthoScale) → more ticks. */
    private PlotStyle retick(PlotStyle style, float zoom) {
        float ratio = zoom > 1e-6f ? baseScale / zoom : 1f;
        ratio = Math.max(0.5f, Math.min(3f, ratio));
        int xt = Math.max(2, Math.round(style.targetXTicks() * ratio));
        int yt = Math.max(2, Math.round(style.targetYTicks() * ratio));
        return style.withTargetTicks(xt, yt);
    }
}
