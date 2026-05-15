package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.natives.glfw.Glfw;

/**
 * Translates input events into value updates for {@link Component.Slider}.
 * Mirrors the {@link TextInputController} pattern: static API, internal
 * state for the currently-dragging slider.
 * <p>
 * Mouse: press on the slider jumps the thumb to the cursor and begins a
 * drag; cursor moves while dragging update the value continuously even
 * when the cursor leaves the slider rect; mouse-up ends the drag.
 * <p>
 * Keyboard (when the slider is focused): Left/Down decreases by 1% of
 * range, Right/Up increases by 1%, PageDown/PageUp step by 10%,
 * Home/End jump to {@code min}/{@code max}.
 */
public final class SliderController {

    private SliderController() {}

    private static Component.Slider dragging = null;

    public static boolean onMouseDown(Component hit, double cursorX, double cursorY) {
        if (!(hit instanceof Component.Slider sl) || !sl.interactive()) return false;
        dragging = sl;
        applyCursor(sl, cursorX, cursorY);
        return true;
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        if (dragging == null) return;
        applyCursor(dragging, cursorX, cursorY);
    }

    public static void onMouseUp() {
        dragging = null;
    }

    /** Drop any in-progress drag — used on window focus loss. */
    public static void cancelDrag() {
        dragging = null;
    }

    public static boolean isDragging() {
        return dragging != null;
    }

    public static boolean onKey(int key) {
        if (!(FocusState.focused() instanceof Component.Slider sl) || !sl.interactive()) return false;
        float range    = sl.max() - sl.min();
        if (range <= 0f) return false;
        float step     = range / 100f;
        float pageStep = range / 10f;
        float current  = sl.value().get();
        float newValue;
        switch (key) {
            case Glfw.GLFW_KEY_LEFT, Glfw.GLFW_KEY_DOWN -> newValue = current - step;
            case Glfw.GLFW_KEY_RIGHT, Glfw.GLFW_KEY_UP  -> newValue = current + step;
            case Glfw.GLFW_KEY_PAGE_DOWN -> newValue = current - pageStep;
            case Glfw.GLFW_KEY_PAGE_UP   -> newValue = current + pageStep;
            case Glfw.GLFW_KEY_HOME      -> newValue = sl.min();
            case Glfw.GLFW_KEY_END       -> newValue = sl.max();
            default -> { return false; }
        }
        sl.value().set(clamp(newValue, sl.min(), sl.max()));
        return true;
    }

    private static void applyCursor(Component.Slider sl, double cursorX, double cursorY) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect rect = lr.rectOf(sl);
        if (rect == null) return;

        float fraction;
        if (sl.horizontal()) {
            float w = rect.width();
            fraction = w > 0f ? (float) ((cursorX - rect.x()) / w) : 0f;
        } else {
            float h = rect.height();
            fraction = h > 0f ? (float) ((cursorY - rect.y()) / h) : 0f;
        }
        fraction = clamp(fraction, 0f, 1f);
        float newValue = sl.min() + fraction * (sl.max() - sl.min());
        sl.value().set(clamp(newValue, sl.min(), sl.max()));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
