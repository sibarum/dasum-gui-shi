package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.natives.glfw.Glfw;

import java.lang.foreign.MemorySegment;
import java.util.EnumMap;
import java.util.Map;

/**
 * Manages standard cursor handles + active cursor shape. Standard cursors
 * are created once at {@link #init(long)} and disposed on {@link #close()}.
 * {@link #setShape(CursorShape)} is idempotent — calls {@code glfwSetCursor}
 * only when the requested shape differs from the active one.
 */
@SuppressWarnings("restricted")
public final class CursorManager implements AutoCloseable {

    public enum CursorShape {
        ARROW, IBEAM, HAND, CROSSHAIR, HRESIZE, VRESIZE
    }

    private final MemorySegment windowHandle;
    private final Map<CursorShape, MemorySegment> cursors = new EnumMap<>(CursorShape.class);
    private CursorShape currentShape = null;

    public CursorManager(long windowAddress) {
        this.windowHandle = MemorySegment.ofAddress(windowAddress);
    }

    public void init() {
        cursors.put(CursorShape.ARROW,     Glfw.glfwCreateStandardCursor(Glfw.GLFW_ARROW_CURSOR));
        cursors.put(CursorShape.IBEAM,     Glfw.glfwCreateStandardCursor(Glfw.GLFW_IBEAM_CURSOR));
        cursors.put(CursorShape.HAND,      Glfw.glfwCreateStandardCursor(Glfw.GLFW_HAND_CURSOR));
        cursors.put(CursorShape.CROSSHAIR, Glfw.glfwCreateStandardCursor(Glfw.GLFW_CROSSHAIR_CURSOR));
        cursors.put(CursorShape.HRESIZE,   Glfw.glfwCreateStandardCursor(Glfw.GLFW_HRESIZE_CURSOR));
        cursors.put(CursorShape.VRESIZE,   Glfw.glfwCreateStandardCursor(Glfw.GLFW_VRESIZE_CURSOR));
        setShape(CursorShape.ARROW);
    }

    public void setShape(CursorShape shape) {
        if (shape == currentShape) return;
        MemorySegment handle = cursors.get(shape);
        if (handle == null) return;
        Glfw.glfwSetCursor(windowHandle, handle);
        currentShape = shape;
    }

    public CursorShape currentShape() { return currentShape; }

    @Override
    public void close() {
        for (MemorySegment c : cursors.values()) {
            if (c != null && c.address() != 0L) {
                Glfw.glfwDestroyCursor(c);
            }
        }
        cursors.clear();
    }
}
