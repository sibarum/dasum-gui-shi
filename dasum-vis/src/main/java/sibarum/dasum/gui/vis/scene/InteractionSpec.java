package sibarum.dasum.gui.vis.scene;

/**
 * Per-component camera-interaction policy, read by the viewport input
 * controller. Published via {@link SceneStates#setInteraction}; absent
 * spec behaves like {@link #defaults()}.
 *
 * <p>Zoom clamps apply to whichever quantity the camera mode scrolls:
 * orbit {@code distance} in perspective, {@code orthoScale} in
 * orthographic.
 *
 * @param mode          how mouse input drives the camera
 * @param zoomMin       lower clamp for distance / orthoScale
 * @param zoomMax       upper clamp for distance / orthoScale
 * @param pitchClampRad maximum |pitch| in radians (ORBIT_3D only)
 * @param panBounds     world-XY rectangle the ortho pan target is confined
 *                      to (PAN_ZOOM_2D only), or {@code null} for free pan
 */
public record InteractionSpec(Mode mode, float zoomMin, float zoomMax, float pitchClampRad,
                              PanBounds panBounds) {

    public enum Mode {
        /** Drag orbits (perspective) or pans (ortho); scroll zooms. The historical behavior. */
        ORBIT_3D,
        /** Drag pans in the view plane; scroll zooms anchored at the cursor. For 2D scenes. */
        PAN_ZOOM_2D,
        /** Camera ignores mouse input entirely; click-picking still works. */
        LOCKED
    }

    /**
     * A world-space XY rectangle that bounds how far an ortho 2D view may be
     * panned — the "keep the content on screen" fence. The viewport controller
     * clamps the camera target so the visible world rect stays inside
     * {@code [minX, maxX] × [minY, maxY]}; on an axis where the content is
     * smaller than the viewport it centres instead (docs semantics in
     * {@code SceneViewController#clampTarget}).
     */
    public record PanBounds(float minX, float minY, float maxX, float maxY) {
        public PanBounds {
            if (!(maxX >= minX)) throw new IllegalArgumentException("maxX >= minX");
            if (!(maxY >= minY)) throw new IllegalArgumentException("maxY >= minY");
        }
    }

    /** Just shy of ±90° — avoids the lookAt up-vector degeneracy. */
    public static final float DEFAULT_PITCH_CLAMP = (float) (Math.PI * 0.5 - 0.01);

    public InteractionSpec {
        if (mode == null) throw new IllegalArgumentException("mode != null");
        if (!(zoomMin > 0f)) throw new IllegalArgumentException("zoomMin > 0");
        if (zoomMax < zoomMin) throw new IllegalArgumentException("zoomMax >= zoomMin");
        if (!(pitchClampRad > 0f)) throw new IllegalArgumentException("pitchClampRad > 0");
    }

    /** Historical behavior: free orbit, permissive clamps. */
    public static InteractionSpec defaults() {
        return new InteractionSpec(Mode.ORBIT_3D, 1e-4f, Float.MAX_VALUE, DEFAULT_PITCH_CLAMP, null);
    }

    /** 2D pan/zoom with cursor-anchored zoom — charts, fractals, images. */
    public static InteractionSpec panZoom2d() {
        return new InteractionSpec(Mode.PAN_ZOOM_2D, 1e-4f, Float.MAX_VALUE, DEFAULT_PITCH_CLAMP, null);
    }

    /** Camera fixed; clicks still pick. */
    public static InteractionSpec locked() {
        return new InteractionSpec(Mode.LOCKED, 1e-4f, Float.MAX_VALUE, DEFAULT_PITCH_CLAMP, null);
    }

    public InteractionSpec withZoomRange(float min, float max) {
        return new InteractionSpec(mode, min, max, pitchClampRad, panBounds);
    }

    public InteractionSpec withPitchClamp(float rad) {
        return new InteractionSpec(mode, zoomMin, zoomMax, rad, panBounds);
    }

    /** Confine ortho panning to the world-XY rectangle {@code [minX,maxX]×[minY,maxY]}. */
    public InteractionSpec withPanBounds(float minX, float minY, float maxX, float maxY) {
        return new InteractionSpec(mode, zoomMin, zoomMax, pitchClampRad,
                new PanBounds(minX, minY, maxX, maxY));
    }
}
