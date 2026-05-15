package sibarum.dasum.gui.core.window;

import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.natives.glfw.Glfw;
import sibarum.dasum.gui.natives.glfw.GlfwCallbacks;

import java.lang.foreign.MemorySegment;

import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_CONTEXT_VERSION_MAJOR;
import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_CONTEXT_VERSION_MINOR;
import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_OPENGL_CORE_PROFILE;
import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_OPENGL_FORWARD_COMPAT;
import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_OPENGL_PROFILE;
import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_RESIZABLE;
import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_TRUE;
import static sibarum.dasum.gui.natives.glfw.Glfw.GLFW_VISIBLE;

public final class Window implements AutoCloseable {

    private final MemorySegment handle;
    private int framebufferWidth;
    private int framebufferHeight;
    private float contentScaleX = 1f;
    private float contentScaleY = 1f;

    private Window(MemorySegment handle, int fbWidth, int fbHeight, float sx, float sy) {
        this.handle = handle;
        this.framebufferWidth = fbWidth;
        this.framebufferHeight = fbHeight;
        this.contentScaleX = sx;
        this.contentScaleY = sy;
    }

    public static Window create(int width, int height, String title) {
        Glfw.glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        Glfw.glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        Glfw.glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        Glfw.glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        Glfw.glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        Glfw.glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        MemorySegment handle = Glfw.glfwCreateWindow(width, height, title);
        if (handle == null || handle.address() == 0L) {
            throw new IllegalStateException("glfwCreateWindow returned NULL");
        }

        Glfw.glfwMakeContextCurrent(handle);
        Glfw.glfwSwapInterval(1);

        int[] fb = Glfw.glfwGetFramebufferSize(handle);
        float[] scale = Glfw.glfwGetWindowContentScale(handle);

        Window w = new Window(handle, fb[0], fb[1], scale[0], scale[1]);

        Glfw.glfwSetFramebufferSizeCallback(handle, GlfwCallbacks.FB_SIZE_CALLBACK_STUB);
        GlfwCallbacks.setFramebufferSizeListener((win, width2, height2) -> {
            if (win == handle.address()) {
                w.framebufferWidth = width2;
                w.framebufferHeight = height2;
                Invalidator.invalidate();
            }
        });

        Glfw.glfwSetKeyCallback(handle, GlfwCallbacks.KEY_CALLBACK_STUB);
        Glfw.glfwSetCursorPosCallback(handle, GlfwCallbacks.CURSOR_POS_CALLBACK_STUB);
        Glfw.glfwSetMouseButtonCallback(handle, GlfwCallbacks.MOUSE_BUTTON_CALLBACK_STUB);
        Glfw.glfwSetScrollCallback(handle, GlfwCallbacks.SCROLL_CALLBACK_STUB);
        Glfw.glfwSetCharCallback(handle, GlfwCallbacks.CHAR_CALLBACK_STUB);
        Glfw.glfwSetWindowFocusCallback(handle, GlfwCallbacks.WINDOW_FOCUS_CALLBACK_STUB);
        Glfw.glfwSetCursorEnterCallback(handle, GlfwCallbacks.CURSOR_ENTER_CALLBACK_STUB);

        return w;
    }

    public MemorySegment handle() { return handle; }
    public int framebufferWidth() { return framebufferWidth; }
    public int framebufferHeight() { return framebufferHeight; }
    public float contentScaleX() { return contentScaleX; }
    public float contentScaleY() { return contentScaleY; }

    public boolean shouldClose() { return Glfw.glfwWindowShouldClose(handle); }
    public void requestClose() { Glfw.glfwSetWindowShouldClose(handle, true); }
    public void swapBuffers() { Glfw.glfwSwapBuffers(handle); }

    @Override
    public void close() {
        Glfw.glfwDestroyWindow(handle);
    }
}
