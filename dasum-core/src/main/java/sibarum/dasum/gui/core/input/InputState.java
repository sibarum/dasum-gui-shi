package sibarum.dasum.gui.core.input;

/**
 * Process-global mouse-pixel state. Single-window: there is one mouse pointer
 * relative to one framebuffer, so global is fine. Multi-window would require
 * keying by window handle — out of scope.
 */
public final class InputState {

    private static double mouseX = -1d;
    private static double mouseY = -1d;
    private static boolean mouseInWindow = false;
    private static boolean leftButtonHeld = false;

    private InputState() {}

    public static double mouseX() { return mouseX; }
    public static double mouseY() { return mouseY; }
    public static boolean mouseInWindow() { return mouseInWindow; }
    public static boolean leftButtonHeld() { return leftButtonHeld; }

    public static void updateMousePos(double x, double y) {
        mouseX = x;
        mouseY = y;
        mouseInWindow = true;
    }

    public static void setLeftButtonHeld(boolean held) {
        leftButtonHeld = held;
    }

    public static void markMouseExited() {
        mouseInWindow = false;
    }
}
