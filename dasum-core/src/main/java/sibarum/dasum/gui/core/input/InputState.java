package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.natives.glfw.Glfw;

/**
 * Process-global mouse + modifier-key state. Single-window: there is
 * one mouse pointer relative to one framebuffer, so global is fine.
 * Multi-window would require keying by window handle — out of scope.
 *
 * <p>Modifier tracking is opt-in but should be wired by every app: in
 * the GLFW key callback and mouse-button callback, call
 * {@link #setMods(int)} with the {@code mods} bitmask GLFW supplies.
 * The framework uses this in
 * {@link TextInputController#onCharInput(int)} to reject character
 * events that arrived as a side effect of a modifier-chord keypress
 * (e.g. Ctrl+Space on Windows generates a spurious space char event
 * that would otherwise insert a space into the focused editable text).
 */
public final class InputState {

    private static double mouseX = -1d;
    private static double mouseY = -1d;
    private static boolean mouseInWindow = false;
    private static boolean leftButtonHeld = false;
    private static int modBits = 0;

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

    /**
     * Record the modifier bitmask GLFW supplied to the most recent key
     * or mouse-button event. Apps should call this once at the top of
     * their key listener and mouse-button listener.
     */
    public static void setMods(int mods) {
        modBits = mods;
    }

    /** Last-recorded modifier bitmask; bits are {@link Glfw#GLFW_MOD_CONTROL} etc. */
    public static int modBits() { return modBits; }

    public static boolean ctrlHeld()  { return (modBits & Glfw.GLFW_MOD_CONTROL) != 0; }
    public static boolean shiftHeld() { return (modBits & Glfw.GLFW_MOD_SHIFT)   != 0; }
    public static boolean altHeld()   { return (modBits & Glfw.GLFW_MOD_ALT)     != 0; }
    public static boolean superHeld() { return (modBits & Glfw.GLFW_MOD_SUPER)   != 0; }
}
