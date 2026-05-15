package sibarum.dasum.gui.core.render;

/**
 * Column-major 4x4 projection matrices for OpenGL uniforms. We commit to a
 * top-left origin with Y growing downward (matching what every desktop UI
 * toolkit users expect); the orthographic matrix here folds the Y-flip into
 * the projection so component code can stay in screen-space pixels.
 */
public final class Projection {

    private Projection() {}

    /**
     * Orthographic projection mapping (0, 0) to the top-left of the viewport
     * and ({@code width}, {@code height}) to the bottom-right. Units are
     * pixels. Output is a 16-element column-major float array suitable for
     * {@code glUniformMatrix4fv}.
     */
    public static float[] orthoTopLeft(float width, float height) {
        float[] m = new float[16];
        m[0]  = 2f / width;
        m[5]  = -2f / height;
        m[10] = -1f;
        m[12] = -1f;
        m[13] = 1f;
        m[15] = 1f;
        return m;
    }
}
