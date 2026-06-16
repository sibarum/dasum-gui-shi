package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.vis.math.CameraRig;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.scene.ImageLayer;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.SceneStates;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Drives a 2D false-colour view of a 3D complex volume from a {@link Component.Slider}:
 * the slider fraction selects a slice along a chosen {@link ComplexField3D.Axis3},
 * which is rasterized through a {@link ComplexColorMap} and published to a
 * {@code SceneView}. Scrubbing the slider re-rasterizes and re-publishes;
 * same-dimension frames reuse the GPU texture (one {@code glTexSubImage2D}).
 *
 * <p>Slider callbacks fire on the GLFW thread. For large volumes pass an
 * {@link Executor} so the per-slice rasterization runs off-thread; the final
 * {@link SceneStates#publish} is lock-free and safe from any thread.
 */
public final class FieldSlicePlot {

    private final Component.SceneView view;
    private final PlotFrame frame;
    private final ComplexField3D field;
    private final ComplexField3D.Axis3 axis;
    private final ComplexColorMap map;
    private final PlotStyle style;
    private final Component.Slider slider;
    private final Executor executor; // nullable → render inline

    public FieldSlicePlot(Component.SceneView view, PlotFrame frame, ComplexField3D field,
                          ComplexField3D.Axis3 axis, ComplexColorMap map, PlotStyle style,
                          Component.Slider slider, Executor executor) {
        if (view == null || frame == null || field == null || axis == null
            || map == null || style == null || slider == null) {
            throw new IllegalArgumentException("required args != null");
        }
        this.view = view;
        this.frame = frame;
        this.field = field;
        this.axis = axis;
        this.map = map;
        this.style = style;
        this.slider = slider;
        this.executor = executor;
    }

    /**
     * Frame the camera, subscribe to the slider, and render the initial slice.
     * Call once after building the layout.
     */
    public void bind() {
        CameraSpec spec = CameraRig.fitToBounds(CameraSpec.defaultOrtho(), frame.worldMin(), frame.worldMax());
        SceneStates.setCamera(view, spec);
        slider.value().subscribe(v -> render());
        render();
    }

    /** Slice index currently selected by the slider. */
    public int currentIndex() {
        int count = field.sliceCount(axis);
        int idx = Math.round(slider.fraction() * (count - 1));
        return Math.max(0, Math.min(count - 1, idx));
    }

    /** Rasterize the current slice and publish (off-thread when an executor was given). */
    public void render() {
        ComplexField2D slice = field.slice(axis, currentIndex());
        Runnable task = () -> {
            ImageLayer image = FieldRaster.imageLayer(slice, map, frame);
            List<Layer> layers = new ArrayList<>();
            layers.add(image);
            layers.addAll(frame.chrome(style));
            SceneStates.publish(view, new SceneSnapshot(layers));
        };
        if (executor != null) executor.execute(task);
        else task.run();
    }
}
