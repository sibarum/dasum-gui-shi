package sibarum.dasum.gui.natives.glfw;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@SuppressWarnings("restricted")
public final class GlfwCallbacks {

    /**
     * Single shared arena that lives for the program lifetime.
     * GLFW retains pointers to upcall stubs — releasing them while GLFW holds them = crash.
     */
    private static final Arena UPCALL_ARENA = Arena.ofShared();

    private static final Linker LINKER = Linker.nativeLinker();

    private static volatile ErrorListener errorListener;
    private static volatile KeyListener keyListener;
    private static volatile FramebufferSizeListener fbSizeListener;
    private static volatile CursorPosListener cursorPosListener;
    private static volatile MouseButtonListener mouseButtonListener;
    private static volatile ScrollListener scrollListener;
    private static volatile CharListener charListener;
    private static volatile WindowFocusListener windowFocusListener;
    private static volatile CursorEnterListener cursorEnterListener;

    public static final MemorySegment ERROR_CALLBACK_STUB;
    public static final MemorySegment KEY_CALLBACK_STUB;
    public static final MemorySegment FB_SIZE_CALLBACK_STUB;
    public static final MemorySegment CURSOR_POS_CALLBACK_STUB;
    public static final MemorySegment MOUSE_BUTTON_CALLBACK_STUB;
    public static final MemorySegment SCROLL_CALLBACK_STUB;
    public static final MemorySegment CHAR_CALLBACK_STUB;
    public static final MemorySegment WINDOW_FOCUS_CALLBACK_STUB;
    public static final MemorySegment CURSOR_ENTER_CALLBACK_STUB;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodHandle errorHandle = lookup.findStatic(
                GlfwCallbacks.class, "onError",
                MethodType.methodType(void.class, int.class, MemorySegment.class)
            );
            MethodHandle keyHandle = lookup.findStatic(
                GlfwCallbacks.class, "onKey",
                MethodType.methodType(void.class, MemorySegment.class, int.class, int.class, int.class, int.class)
            );
            MethodHandle fbHandle = lookup.findStatic(
                GlfwCallbacks.class, "onFramebufferSize",
                MethodType.methodType(void.class, MemorySegment.class, int.class, int.class)
            );
            MethodHandle cursorPosHandle = lookup.findStatic(
                GlfwCallbacks.class, "onCursorPos",
                MethodType.methodType(void.class, MemorySegment.class, double.class, double.class)
            );
            MethodHandle mouseBtnHandle = lookup.findStatic(
                GlfwCallbacks.class, "onMouseButton",
                MethodType.methodType(void.class, MemorySegment.class, int.class, int.class, int.class)
            );
            MethodHandle scrollHandle = lookup.findStatic(
                GlfwCallbacks.class, "onScroll",
                MethodType.methodType(void.class, MemorySegment.class, double.class, double.class)
            );
            MethodHandle charHandle = lookup.findStatic(
                GlfwCallbacks.class, "onChar",
                MethodType.methodType(void.class, MemorySegment.class, int.class)
            );
            MethodHandle windowFocusHandle = lookup.findStatic(
                GlfwCallbacks.class, "onWindowFocus",
                MethodType.methodType(void.class, MemorySegment.class, int.class)
            );
            MethodHandle cursorEnterHandle = lookup.findStatic(
                GlfwCallbacks.class, "onCursorEnter",
                MethodType.methodType(void.class, MemorySegment.class, int.class)
            );

            ERROR_CALLBACK_STUB = LINKER.upcallStub(
                errorHandle,
                FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS),
                UPCALL_ARENA
            );
            KEY_CALLBACK_STUB = LINKER.upcallStub(
                keyHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
                UPCALL_ARENA
            );
            FB_SIZE_CALLBACK_STUB = LINKER.upcallStub(
                fbHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT),
                UPCALL_ARENA
            );
            CURSOR_POS_CALLBACK_STUB = LINKER.upcallStub(
                cursorPosHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE),
                UPCALL_ARENA
            );
            MOUSE_BUTTON_CALLBACK_STUB = LINKER.upcallStub(
                mouseBtnHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
                UPCALL_ARENA
            );
            SCROLL_CALLBACK_STUB = LINKER.upcallStub(
                scrollHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE),
                UPCALL_ARENA
            );
            CHAR_CALLBACK_STUB = LINKER.upcallStub(
                charHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT),
                UPCALL_ARENA
            );
            WINDOW_FOCUS_CALLBACK_STUB = LINKER.upcallStub(
                windowFocusHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT),
                UPCALL_ARENA
            );
            CURSOR_ENTER_CALLBACK_STUB = LINKER.upcallStub(
                cursorEnterHandle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT),
                UPCALL_ARENA
            );
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private GlfwCallbacks() {}

    public static void setErrorListener(ErrorListener l) { errorListener = l; }
    public static void setKeyListener(KeyListener l) { keyListener = l; }
    public static void setFramebufferSizeListener(FramebufferSizeListener l) { fbSizeListener = l; }
    public static void setCursorPosListener(CursorPosListener l) { cursorPosListener = l; }
    public static void setMouseButtonListener(MouseButtonListener l) { mouseButtonListener = l; }
    public static void setScrollListener(ScrollListener l) { scrollListener = l; }
    public static void setCharListener(CharListener l) { charListener = l; }
    public static void setWindowFocusListener(WindowFocusListener l) { windowFocusListener = l; }
    public static void setCursorEnterListener(CursorEnterListener l) { cursorEnterListener = l; }

    private static void onError(int code, MemorySegment messagePtr) {
        ErrorListener l = errorListener;
        if (l == null) return;
        try {
            l.onError(code, readCString(messagePtr));
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onKey(MemorySegment window, int key, int scancode, int action, int mods) {
        KeyListener l = keyListener;
        if (l == null) return;
        try { l.onKey(window.address(), key, scancode, action, mods); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onFramebufferSize(MemorySegment window, int width, int height) {
        FramebufferSizeListener l = fbSizeListener;
        if (l == null) return;
        try { l.onResize(window.address(), width, height); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onCursorPos(MemorySegment window, double x, double y) {
        CursorPosListener l = cursorPosListener;
        if (l == null) return;
        try { l.onCursorPos(window.address(), x, y); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onMouseButton(MemorySegment window, int button, int action, int mods) {
        MouseButtonListener l = mouseButtonListener;
        if (l == null) return;
        try { l.onMouseButton(window.address(), button, action, mods); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onScroll(MemorySegment window, double xOffset, double yOffset) {
        ScrollListener l = scrollListener;
        if (l == null) return;
        try { l.onScroll(window.address(), xOffset, yOffset); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onChar(MemorySegment window, int codepoint) {
        CharListener l = charListener;
        if (l == null) return;
        try { l.onChar(window.address(), codepoint); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onWindowFocus(MemorySegment window, int focused) {
        WindowFocusListener l = windowFocusListener;
        if (l == null) return;
        try { l.onWindowFocus(window.address(), focused != 0); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static void onCursorEnter(MemorySegment window, int entered) {
        CursorEnterListener l = cursorEnterListener;
        if (l == null) return;
        try { l.onCursorEnter(window.address(), entered != 0); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private static String readCString(MemorySegment ptr) {
        if (ptr == null || ptr.address() == 0L) return "";
        MemorySegment reinterpreted = ptr.reinterpret(Long.MAX_VALUE);
        long len = 0;
        while (reinterpreted.get(JAVA_BYTE, len) != 0) len++;
        byte[] bytes = new byte[(int) len];
        for (int i = 0; i < len; i++) bytes[i] = reinterpreted.get(JAVA_BYTE, i);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @FunctionalInterface public interface ErrorListener           { void onError(int code, String message); }
    @FunctionalInterface public interface KeyListener             { void onKey(long window, int key, int scancode, int action, int mods); }
    @FunctionalInterface public interface FramebufferSizeListener { void onResize(long window, int width, int height); }
    @FunctionalInterface public interface CursorPosListener       { void onCursorPos(long window, double x, double y); }
    @FunctionalInterface public interface MouseButtonListener     { void onMouseButton(long window, int button, int action, int mods); }
    @FunctionalInterface public interface ScrollListener          { void onScroll(long window, double xOffset, double yOffset); }
    @FunctionalInterface public interface CharListener            { void onChar(long window, int codepoint); }
    @FunctionalInterface public interface WindowFocusListener     { void onWindowFocus(long window, boolean focused); }
    @FunctionalInterface public interface CursorEnterListener     { void onCursorEnter(long window, boolean entered); }
}
