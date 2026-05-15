package sibarum.dasum.gui.core.theme;

/**
 * Semantic role used to derive widget colors from the active {@link Theme}.
 * Mirrors Bootstrap's variant taxonomy — one neutral plus five semantic
 * shades. Variants only drive colors at this stage; they may be extended
 * later to influence borders, shadows, or animations.
 */
public enum Variant {
    DEFAULT,
    PRIMARY,
    SUCCESS,
    WARNING,
    ERROR,
    INFO
}
