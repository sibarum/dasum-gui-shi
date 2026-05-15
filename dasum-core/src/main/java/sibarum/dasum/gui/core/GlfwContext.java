package sibarum.dasum.gui.core;

import sibarum.dasum.gui.natives.glfw.Glfw;
import sibarum.dasum.gui.natives.glfw.GlfwCallbacks;

/**
 * Lifecycle wrapper around {@code glfwInit} / {@code glfwTerminate}. Wires
 * an error callback that prints to stderr; replace by setting your own
 * {@link GlfwCallbacks#setErrorListener} before or after init.
 */
public final class GlfwContext implements AutoCloseable {

    public static GlfwContext init() {
        GlfwCallbacks.setErrorListener((code, message) ->
            System.err.println("[GLFW] error " + Integer.toHexString(code) + ": " + message));
        Glfw.glfwSetErrorCallback(GlfwCallbacks.ERROR_CALLBACK_STUB);

        if (Glfw.glfwInit() == 0) {
            throw new IllegalStateException("glfwInit failed");
        }
        return new GlfwContext();
    }

    private GlfwContext() {}

    @Override
    public void close() {
        Glfw.glfwTerminate();
    }
}
