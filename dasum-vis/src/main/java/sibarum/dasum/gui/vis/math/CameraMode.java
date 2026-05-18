package sibarum.dasum.gui.vis.math;

/** Projection mode for a point-cloud viewport. */
public enum CameraMode {
    /** 2D top-down view; {@link CameraSpec#orthoScale} controls world-units per viewport-height. */
    ORTHOGRAPHIC,
    /** 3D view orbiting around {@link CameraSpec#target}. */
    PERSPECTIVE
}
