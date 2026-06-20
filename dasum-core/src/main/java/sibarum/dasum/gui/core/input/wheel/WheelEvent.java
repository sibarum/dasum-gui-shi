package sibarum.dasum.gui.core.input.wheel;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.LayoutResult;

/**
 * One mouse-wheel event, resolved once by the {@link WheelRouter} on the
 * GUI thread and passed to every {@link WheelHandler} in the cascade.
 *
 * <p>The router precomputes the things handlers commonly need so each one
 * doesn't repeat the work: the hit-tested component under the cursor, the
 * active input root + its layout, and the shift-adjusted scroll delta in
 * <b>em</b> (the framework's public coordinate unit). Handlers that want
 * the unprocessed wheel input — a 3D viewport zooming per notch, a table
 * stepping by row height — read {@link #rawXOff} / {@link #rawYOff}
 * instead.
 *
 * @param rawXOff   raw GLFW horizontal wheel offset, in notches (sign as GLFW reports)
 * @param rawYOff   raw GLFW vertical wheel offset, in notches
 * @param emDx      horizontal scroll delta in em, sign-corrected and shift-axis-swapped,
 *                  ready to feed {@code ScrollPosition.scrollBy}
 * @param emDy      vertical scroll delta in em (zero when {@code shiftDown})
 * @param mouseXPx  cursor x in pixels ({@code InputState.mouseX})
 * @param mouseYPx  cursor y in pixels
 * @param hit       deepest interactive component under the cursor, or {@code null}
 * @param root      active input root ({@code OverlayStack.activeInputRoot})
 * @param layout    layout for {@code root}
 * @param shiftDown whether shift was held when the wheel turned
 */
public record WheelEvent(
        double rawXOff,
        double rawYOff,
        float emDx,
        float emDy,
        double mouseXPx,
        double mouseYPx,
        Component hit,
        Component root,
        LayoutResult layout,
        boolean shiftDown
) {}
