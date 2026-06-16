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
