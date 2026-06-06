package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import java.util.Arrays;

import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND;
import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND_DST_RGB;
import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND_EQUATION_RGB;
import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND_SRC_RGB;
import static sibarum.dasum.gui.natives.gl.Gl.GL_CURRENT_PROGRAM;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_TEST;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_WRITEMASK;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SCISSOR_BOX;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SCISSOR_TEST;
import static sibarum.dasum.gui.natives.gl.Gl.GL_VERTEX_ARRAY_BINDING;
import static sibarum.dasum.gui.natives.gl.Gl.GL_VIEWPORT;

/**
 * Debug-only GL-state leak detector. Wraps a custom-renderer invocation
 * (anything that touches global GL state outside the framework's 2D
 * batcher) and asserts on exit that every piece of state it captured on
 * entry is unchanged.
 *
 * <p>Catches the whole class of bugs that bit us when the point-cloud
 * renderer was forgetting to restore {@code glViewport}, leaving depth
 * test enabled, or unbinding the wrong VAO — each of which would
 * silently corrupt subsequent 2D rendering with no error message.
 *
 * <h2>Activation</h2>
 *
 * Gated by the system property {@code dasum.debug.gl} (default off).
 * Enable per-run with {@code -Ddasum.debug.gl=true}. When off,
 * {@link #snapshot()} returns {@code null} and {@link #assertUnchanged}
 * is a no-op — zero cost in release builds.
 *
 * <h2>State covered</h2>
 *
 * <ul>
 *   <li>{@link Gl#GL_VIEWPORT} (4 ints)</li>
 *   <li>{@link Gl#GL_SCISSOR_TEST} enable + {@link Gl#GL_SCISSOR_BOX}</li>
 *   <li>{@link Gl#GL_DEPTH_TEST} enable</li>
 *   <li>{@link Gl#GL_BLEND} enable</li>
 *   <li>{@link Gl#GL_BLEND_SRC_RGB} / {@link Gl#GL_BLEND_DST_RGB} /
 *       {@link Gl#GL_BLEND_EQUATION_RGB} (blend func + equation — added
 *       when per-layer BlendMode landed in dasum-vis)</li>
 *   <li>{@link Gl#GL_DEPTH_WRITEMASK} (depth writes)</li>
 *   <li>{@link Gl#GL_CURRENT_PROGRAM} (active shader)</li>
 *   <li>{@link Gl#GL_VERTEX_ARRAY_BINDING} (bound VAO)</li>
 * </ul>
 *
 * Doesn't cover depth func, texture bindings, or buffer bindings — those
 * would be the next-tier additions if a bug pattern proves they're needed.
 */
public final class GlStateGuard {

    /** {@code -Ddasum.debug.gl=true} flips this on. */
    public static final boolean ENABLED =
        Boolean.parseBoolean(System.getProperty("dasum.debug.gl", "false"));

    public record Snapshot(
        int[] viewport,           // 4 ints
        int scissorEnabled,       // 0 or 1
        int[] scissorBox,         // 4 ints
        int depthTestEnabled,     // 0 or 1
        int blendEnabled,         // 0 or 1
        int blendSrcRgb,          // blend src factor
        int blendDstRgb,          // blend dst factor
        int blendEquationRgb,     // FUNC_ADD / MIN / MAX / ...
        int depthWriteMask,       // 0 or 1
        int currentProgram,       // GL name or 0
        int vertexArrayBinding    // GL name or 0
    ) {}

    private GlStateGuard() {}

    /** Capture the current GL state, or {@code null} when guard is disabled. */
    public static Snapshot snapshot() {
        if (!ENABLED) return null;
        return new Snapshot(
            Gl.glGetIntegerv4(GL_VIEWPORT),
            Gl.glGetInteger(GL_SCISSOR_TEST),
            Gl.glGetIntegerv4(GL_SCISSOR_BOX),
            Gl.glGetInteger(GL_DEPTH_TEST),
            Gl.glGetInteger(GL_BLEND),
            Gl.glGetInteger(GL_BLEND_SRC_RGB),
            Gl.glGetInteger(GL_BLEND_DST_RGB),
            Gl.glGetInteger(GL_BLEND_EQUATION_RGB),
            Gl.glGetInteger(GL_DEPTH_WRITEMASK),
            Gl.glGetInteger(GL_CURRENT_PROGRAM),
            Gl.glGetInteger(GL_VERTEX_ARRAY_BINDING)
        );
    }

    /**
     * Compare the current GL state against {@code before}; print a diff
     * to stderr for each field that changed. {@code label} identifies
     * the wrapped call site (e.g. the renderer's class name or the
     * component variant) so the user can find the offender.
     * <p>
     * No-op when {@code before} is null (guard disabled) or when state
     * matches exactly.
     */
    public static void assertUnchanged(Snapshot before, String label) {
        if (before == null) return;
        Snapshot after = snapshot();
        if (after == null) return;

        StringBuilder diff = null;
        if (!Arrays.equals(before.viewport, after.viewport)) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_VIEWPORT: ").append(Arrays.toString(before.viewport))
                .append(" -> ").append(Arrays.toString(after.viewport));
        }
        if (before.scissorEnabled != after.scissorEnabled) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_SCISSOR_TEST: ").append(before.scissorEnabled)
                .append(" -> ").append(after.scissorEnabled);
        }
        // Only meaningful while the scissor test is enabled on either side —
        // a dirty box value with the test off has no rendering effect (the
        // framework's scissor stack always re-sets the box when it enables),
        // so flagging it would just be noise from every push/pop user.
        if ((before.scissorEnabled == 1 || after.scissorEnabled == 1)
                && !Arrays.equals(before.scissorBox, after.scissorBox)) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_SCISSOR_BOX: ").append(Arrays.toString(before.scissorBox))
                .append(" -> ").append(Arrays.toString(after.scissorBox));
        }
        if (before.depthTestEnabled != after.depthTestEnabled) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_DEPTH_TEST: ").append(before.depthTestEnabled)
                .append(" -> ").append(after.depthTestEnabled);
        }
        if (before.blendEnabled != after.blendEnabled) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_BLEND: ").append(before.blendEnabled)
                .append(" -> ").append(after.blendEnabled);
        }
        if (before.blendSrcRgb != after.blendSrcRgb || before.blendDstRgb != after.blendDstRgb) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_BLEND_FUNC: (").append(before.blendSrcRgb).append(", ").append(before.blendDstRgb)
                .append(") -> (").append(after.blendSrcRgb).append(", ").append(after.blendDstRgb).append(')');
        }
        if (before.blendEquationRgb != after.blendEquationRgb) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_BLEND_EQUATION: ").append(before.blendEquationRgb)
                .append(" -> ").append(after.blendEquationRgb);
        }
        if (before.depthWriteMask != after.depthWriteMask) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_DEPTH_WRITEMASK: ").append(before.depthWriteMask)
                .append(" -> ").append(after.depthWriteMask);
        }
        if (before.currentProgram != after.currentProgram) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_CURRENT_PROGRAM: ").append(before.currentProgram)
                .append(" -> ").append(after.currentProgram);
        }
        if (before.vertexArrayBinding != after.vertexArrayBinding) {
            diff = startDiff(diff, label);
            diff.append("\n  GL_VERTEX_ARRAY_BINDING: ").append(before.vertexArrayBinding)
                .append(" -> ").append(after.vertexArrayBinding);
        }

        if (diff != null) {
            System.err.println(diff);
        }
    }

    private static StringBuilder startDiff(StringBuilder existing, String label) {
        if (existing != null) return existing;
        return new StringBuilder("[dasum.debug.gl] state leaked across ").append(label).append(":");
    }
}
