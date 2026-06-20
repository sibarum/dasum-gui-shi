package sibarum.dasum.gui.core.input.wheel;

/**
 * A mouse-wheel handler registered with the {@link WheelRouter}. Handlers
 * are tried in priority order (highest first); the first one to return
 * {@code true} consumes the event and stops the cascade. A handler that
 * returns {@code false} declines, letting the wheel fall through to the
 * next handler and ultimately to the framework's built-in scroll-container
 * routing (which always runs last and cannot be removed).
 *
 * <h2>Threading</h2>
 * {@link #onWheel} always runs on the GUI thread (it fires from the GLFW
 * scroll callback inside the event loop). A handler may therefore read
 * GUI-thread state ({@code InputState}, {@code FocusState},
 * {@code ScrollStates}, the layout) directly. It must be fast and must not
 * block. Handlers may be <em>registered</em> from any thread
 * (see {@link WheelRouter#addHandler}), but must never mutate GUI state
 * from another thread — publish + invalidate instead, the same contract as
 * {@code SceneStates}.
 */
@FunctionalInterface
public interface WheelHandler {

    /**
     * @return {@code true} to consume the event and stop the cascade;
     *         {@code false} to decline and let it fall through.
     */
    boolean onWheel(WheelEvent e);
}
