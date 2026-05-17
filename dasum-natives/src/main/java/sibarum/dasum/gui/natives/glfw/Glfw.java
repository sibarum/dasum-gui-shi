package sibarum.dasum.gui.natives.glfw;

import sibarum.dasum.gui.natives.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@SuppressWarnings("restricted")
public final class Glfw {

    public static final int GLFW_FALSE = 0;
    public static final int GLFW_TRUE  = 1;

    public static final int GLFW_RELEASE = 0;
    public static final int GLFW_PRESS   = 1;
    public static final int GLFW_REPEAT  = 2;

    public static final int GLFW_MOD_SHIFT    = 0x0001;
    public static final int GLFW_MOD_CONTROL  = 0x0002;
    public static final int GLFW_MOD_ALT      = 0x0004;
    public static final int GLFW_MOD_SUPER    = 0x0008;

    public static final int GLFW_KEY_SPACE         = 32;
    public static final int GLFW_KEY_MINUS         = 45;
    public static final int GLFW_KEY_EQUAL         = 61;
    public static final int GLFW_KEY_0             = 48;
    public static final int GLFW_KEY_1             = 49;
    public static final int GLFW_KEY_2             = 50;
    public static final int GLFW_KEY_3             = 51;
    public static final int GLFW_KEY_4             = 52;
    public static final int GLFW_KEY_5             = 53;
    public static final int GLFW_KEY_6             = 54;
    public static final int GLFW_KEY_7             = 55;
    public static final int GLFW_KEY_8             = 56;
    public static final int GLFW_KEY_9             = 57;
    public static final int GLFW_KEY_ESCAPE        = 256;
    public static final int GLFW_KEY_ENTER         = 257;
    public static final int GLFW_KEY_TAB           = 258;
    public static final int GLFW_KEY_BACKSPACE     = 259;
    public static final int GLFW_KEY_DELETE        = 261;
    public static final int GLFW_KEY_RIGHT         = 262;
    public static final int GLFW_KEY_LEFT          = 263;
    public static final int GLFW_KEY_DOWN          = 264;
    public static final int GLFW_KEY_UP            = 265;
    public static final int GLFW_KEY_PAGE_UP       = 266;
    public static final int GLFW_KEY_PAGE_DOWN     = 267;
    public static final int GLFW_KEY_HOME          = 268;
    public static final int GLFW_KEY_END           = 269;
    public static final int GLFW_KEY_LEFT_SHIFT    = 340;
    public static final int GLFW_KEY_LEFT_CONTROL  = 341;
    public static final int GLFW_KEY_LEFT_ALT      = 342;
    public static final int GLFW_KEY_LEFT_SUPER    = 343;
    public static final int GLFW_KEY_RIGHT_SHIFT   = 344;
    public static final int GLFW_KEY_RIGHT_CONTROL = 345;
    public static final int GLFW_KEY_RIGHT_ALT     = 346;
    public static final int GLFW_KEY_RIGHT_SUPER   = 347;

    public static final int GLFW_CONTEXT_VERSION_MAJOR = 0x00022002;
    public static final int GLFW_CONTEXT_VERSION_MINOR = 0x00022003;
    public static final int GLFW_OPENGL_PROFILE        = 0x00022008;
    public static final int GLFW_OPENGL_FORWARD_COMPAT = 0x00022006;
    public static final int GLFW_VISIBLE               = 0x00020004;
    public static final int GLFW_RESIZABLE             = 0x00020003;

    public static final int GLFW_OPENGL_CORE_PROFILE = 0x00032001;

    public static final int GLFW_MOUSE_BUTTON_LEFT   = 0;
    public static final int GLFW_MOUSE_BUTTON_RIGHT  = 1;
    public static final int GLFW_MOUSE_BUTTON_MIDDLE = 2;

    public static final int GLFW_ARROW_CURSOR     = 0x00036001;
    public static final int GLFW_IBEAM_CURSOR     = 0x00036002;
    public static final int GLFW_CROSSHAIR_CURSOR = 0x00036003;
    public static final int GLFW_HAND_CURSOR      = 0x00036004;
    public static final int GLFW_HRESIZE_CURSOR   = 0x00036005;
    public static final int GLFW_VRESIZE_CURSOR   = 0x00036006;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIB;

    static {
        Path resolved = NativeLibLoader.load("glfw3");
        LIB = (resolved != null)
            ? SymbolLookup.libraryLookup(resolved, Arena.global())
            : SymbolLookup.libraryLookup("glfw3", Arena.global());
    }

    private static MethodHandle dc(String name, FunctionDescriptor desc) {
        return LINKER.downcallHandle(
            LIB.find(name).orElseThrow(() -> new UnsatisfiedLinkError("glfw3: " + name)),
            desc
        );
    }

    private static MethodHandle dcOptional(String name, FunctionDescriptor desc) {
        return LIB.find(name).map(s -> LINKER.downcallHandle(s, desc)).orElse(null);
    }

    private static final MethodHandle GLFW_INIT                    = dc("glfwInit",                    FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle GLFW_TERMINATE               = dc("glfwTerminate",               FunctionDescriptor.ofVoid());
    private static final MethodHandle GLFW_WINDOW_HINT             = dc("glfwWindowHint",              FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
    private static final MethodHandle GLFW_CREATE_WINDOW           = dc("glfwCreateWindow",            FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_DESTROY_WINDOW          = dc("glfwDestroyWindow",           FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle GLFW_MAKE_CONTEXT_CURRENT    = dc("glfwMakeContextCurrent",      FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle GLFW_SWAP_BUFFERS            = dc("glfwSwapBuffers",             FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle GLFW_SWAP_INTERVAL           = dc("glfwSwapInterval",            FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle GLFW_WINDOW_SHOULD_CLOSE     = dc("glfwWindowShouldClose",       FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle GLFW_SET_WINDOW_SHOULD_CLOSE = dc("glfwSetWindowShouldClose",    FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    private static final MethodHandle GLFW_POLL_EVENTS             = dc("glfwPollEvents",              FunctionDescriptor.ofVoid());
    private static final MethodHandle GLFW_WAIT_EVENTS             = dc("glfwWaitEvents",              FunctionDescriptor.ofVoid());
    private static final MethodHandle GLFW_WAIT_EVENTS_TIMEOUT     = dc("glfwWaitEventsTimeout",       FunctionDescriptor.ofVoid(JAVA_DOUBLE));
    private static final MethodHandle GLFW_POST_EMPTY_EVENT        = dc("glfwPostEmptyEvent",          FunctionDescriptor.ofVoid());
    private static final MethodHandle GLFW_SET_ERROR_CALLBACK      = dc("glfwSetErrorCallback",        FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_KEY_CALLBACK        = dc("glfwSetKeyCallback",          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_FB_SIZE_CALLBACK    = dc("glfwSetFramebufferSizeCallback", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_CURSOR_POS_CALLBACK = dc("glfwSetCursorPosCallback",    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_MOUSE_BUTTON_CALLBACK = dc("glfwSetMouseButtonCallback", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_SCROLL_CALLBACK     = dc("glfwSetScrollCallback",       FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_CHAR_CALLBACK       = dc("glfwSetCharCallback",         FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_WINDOW_FOCUS_CALLBACK = dc("glfwSetWindowFocusCallback", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_CURSOR_ENTER_CALLBACK = dc("glfwSetCursorEnterCallback", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_CREATE_STANDARD_CURSOR  = dc("glfwCreateStandardCursor",    FunctionDescriptor.of(ADDRESS, JAVA_INT));
    private static final MethodHandle GLFW_DESTROY_CURSOR          = dc("glfwDestroyCursor",           FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle GLFW_SET_CURSOR              = dc("glfwSetCursor",               FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_GET_CLIPBOARD_STRING    = dc("glfwGetClipboardString",      FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_SET_CLIPBOARD_STRING    = dc("glfwSetClipboardString",      FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_GET_KEY                 = dc("glfwGetKey",                  FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle GLFW_GET_PROC_ADDRESS        = dc("glfwGetProcAddress",          FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_GET_WINDOW_CONTENT_SCALE= dc("glfwGetWindowContentScale",   FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_GET_FRAMEBUFFER_SIZE    = dc("glfwGetFramebufferSize",      FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

    private Glfw() {}

    public static int glfwInit() {
        try { return (int) GLFW_INIT.invokeExact(); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwTerminate() {
        try { GLFW_TERMINATE.invokeExact(); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwWindowHint(int hint, int value) {
        try { GLFW_WINDOW_HINT.invokeExact(hint, value); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment glfwCreateWindow(int w, int h, String title) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment titleSeg = arena.allocateFrom(title);
            MemorySegment win = (MemorySegment) GLFW_CREATE_WINDOW.invokeExact(
                w, h, titleSeg, MemorySegment.NULL, MemorySegment.NULL
            );
            return win;
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwDestroyWindow(MemorySegment window) {
        try { GLFW_DESTROY_WINDOW.invokeExact(window); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwMakeContextCurrent(MemorySegment window) {
        try { GLFW_MAKE_CONTEXT_CURRENT.invokeExact(window); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSwapBuffers(MemorySegment window) {
        try { GLFW_SWAP_BUFFERS.invokeExact(window); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSwapInterval(int interval) {
        try { GLFW_SWAP_INTERVAL.invokeExact(interval); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static boolean glfwWindowShouldClose(MemorySegment window) {
        try { return ((int) GLFW_WINDOW_SHOULD_CLOSE.invokeExact(window)) != 0; }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetWindowShouldClose(MemorySegment window, boolean value) {
        try { GLFW_SET_WINDOW_SHOULD_CLOSE.invokeExact(window, value ? 1 : 0); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwPollEvents() {
        try { GLFW_POLL_EVENTS.invokeExact(); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwWaitEvents() {
        try { GLFW_WAIT_EVENTS.invokeExact(); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwWaitEventsTimeout(double timeoutSeconds) {
        try { GLFW_WAIT_EVENTS_TIMEOUT.invokeExact(timeoutSeconds); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwPostEmptyEvent() {
        try { GLFW_POST_EMPTY_EVENT.invokeExact(); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetErrorCallback(MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_ERROR_CALLBACK.invokeExact(callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetKeyCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_KEY_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetFramebufferSizeCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_FB_SIZE_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetCursorPosCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_CURSOR_POS_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetMouseButtonCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_MOUSE_BUTTON_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetScrollCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_SCROLL_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetCharCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_CHAR_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetWindowFocusCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_WINDOW_FOCUS_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetCursorEnterCallback(MemorySegment window, MemorySegment callbackStub) {
        try {
            MemorySegment ignored = (MemorySegment) GLFW_SET_CURSOR_ENTER_CALLBACK.invokeExact(window, callbackStub);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment glfwCreateStandardCursor(int shape) {
        try { return (MemorySegment) GLFW_CREATE_STANDARD_CURSOR.invokeExact(shape); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwDestroyCursor(MemorySegment cursor) {
        try { GLFW_DESTROY_CURSOR.invokeExact(cursor); } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetCursor(MemorySegment window, MemorySegment cursor) {
        MemorySegment c = (cursor == null) ? MemorySegment.NULL : cursor;
        try { GLFW_SET_CURSOR.invokeExact(window, c); } catch (Throwable t) { throw rethrow(t); }
    }

    public static String glfwGetClipboardString(MemorySegment window) {
        try {
            MemorySegment ptr = (MemorySegment) GLFW_GET_CLIPBOARD_STRING.invokeExact(window);
            if (ptr == null || ptr.address() == 0L) return null;
            MemorySegment reinterpreted = ptr.reinterpret(Long.MAX_VALUE);
            long len = 0;
            while (reinterpreted.get(java.lang.foreign.ValueLayout.JAVA_BYTE, len) != 0) len++;
            byte[] bytes = new byte[(int) len];
            for (int i = 0; i < len; i++) bytes[i] = reinterpreted.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void glfwSetClipboardString(MemorySegment window, String value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocateFrom(value == null ? "" : value);
            GLFW_SET_CLIPBOARD_STRING.invokeExact(window, seg);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static int glfwGetKey(MemorySegment window, int key) {
        try { return (int) GLFW_GET_KEY.invokeExact(window, key); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment glfwGetProcAddress(String name) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nameSeg = arena.allocateFrom(name);
            return (MemorySegment) GLFW_GET_PROC_ADDRESS.invokeExact(nameSeg);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static float[] glfwGetWindowContentScale(MemorySegment window) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment xs = arena.allocate(JAVA_FLOAT);
            MemorySegment ys = arena.allocate(JAVA_FLOAT);
            GLFW_GET_WINDOW_CONTENT_SCALE.invokeExact(window, xs, ys);
            return new float[] { xs.get(JAVA_FLOAT, 0), ys.get(JAVA_FLOAT, 0) };
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static int[] glfwGetFramebufferSize(MemorySegment window) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment w = arena.allocate(JAVA_INT);
            MemorySegment h = arena.allocate(JAVA_INT);
            GLFW_GET_FRAMEBUFFER_SIZE.invokeExact(window, w, h);
            return new int[] { w.get(JAVA_INT, 0), h.get(JAVA_INT, 0) };
        } catch (Throwable t) { throw rethrow(t); }
    }

    // Platform-specific native window accessors. Looked up lazily because the
    // symbol only exists in the GLFW build for its own platform — calling
    // glfwGetWin32Window on macOS GLFW returns no symbol, and vice versa.
    private static final MethodHandle GLFW_GET_WIN32_WINDOW = dcOptional(
        "glfwGetWin32Window", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle GLFW_GET_COCOA_WINDOW = dcOptional(
        "glfwGetCocoaWindow", FunctionDescriptor.of(ADDRESS, ADDRESS));

    public static MemorySegment glfwGetWin32Window(MemorySegment window) {
        if (GLFW_GET_WIN32_WINDOW == null) {
            throw new UnsatisfiedLinkError("glfwGetWin32Window not available in this GLFW build");
        }
        try { return (MemorySegment) GLFW_GET_WIN32_WINDOW.invokeExact(window); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment glfwGetCocoaWindow(MemorySegment window) {
        if (GLFW_GET_COCOA_WINDOW == null) {
            throw new UnsatisfiedLinkError("glfwGetCocoaWindow not available in this GLFW build");
        }
        try { return (MemorySegment) GLFW_GET_COCOA_WINDOW.invokeExact(window); }
        catch (Throwable t) { throw rethrow(t); }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error err) throw err;
        return new RuntimeException(t);
    }
}
