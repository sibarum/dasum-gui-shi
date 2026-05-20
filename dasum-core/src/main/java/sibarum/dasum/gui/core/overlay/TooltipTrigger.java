package sibarum.dasum.gui.core.overlay;

import sibarum.dasum.gui.natives.glfw.Glfw;

/**
 * When a tooltip becomes visible. {@link #ALWAYS} shows it whenever the
 * cursor dwells on a component with registered text; the modifier-keyed
 * variants additionally require the named key to be held.
 * <p>
 * {@code MOD_ALT} is the recommended non-default — Alt is reserved on most
 * native desktops for window-menu access, which dasum-gui-shi doesn't use,
 * so it's a clean repurpose. The state is read from
 * {@link sibarum.dasum.gui.core.input.InputState#modBits()} (which apps
 * must keep current by calling {@code InputState.setMods(mods)} from the
 * GLFW key + mouse-button callbacks — already standard wiring).
 */
public enum TooltipTrigger {
    ALWAYS  (0),
    MOD_ALT  (Glfw.GLFW_MOD_ALT),
    MOD_CTRL (Glfw.GLFW_MOD_CONTROL),
    MOD_SHIFT(Glfw.GLFW_MOD_SHIFT);

    private final int requiredModBit;

    TooltipTrigger(int requiredModBit) {
        this.requiredModBit = requiredModBit;
    }

    /**
     * Returns true if {@code currentModBits} satisfies this trigger.
     * {@link #ALWAYS} always returns true; the modifier variants require
     * the named bit to be set.
     */
    public boolean satisfiedBy(int currentModBits) {
        return requiredModBit == 0 || (currentModBits & requiredModBit) != 0;
    }
}
