package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.core.render.ShaderUtil;
import sibarum.dasum.gui.natives.gl.Gl;

import static sibarum.dasum.gui.natives.gl.Gl.GL_BLEND;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_TEST;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FRAMEBUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_ONE_MINUS_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_SRC_ALPHA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE0;

/**
 * HDR + bloom post-process for a whole window frame. {@link #begin} binds an offscreen HDR target
 * (RGBA16F + depth) so the frame renders into it with values free to exceed 1; {@link #end} extracts
 * the bright parts (soft-knee), blurs them (separable Gaussian, ping-pong at half resolution), and
 * composites bloom + the HDR scene back to the default framebuffer with an ACES tone-map. GL/main
 * thread only. Threshold / intensity / iterations are tunable.
 */
public final class BloomPass implements AutoCloseable {

    private final HdrTarget scene = new HdrTarget(true);    // full-res, with depth (3D renders here)
    private final HdrTarget blurA = new HdrTarget(false);   // half-res ping
    private final HdrTarget blurB = new HdrTarget(false);   // half-res pong
    private final FullscreenQuad quad = new FullscreenQuad();

    private int brightProg, blurProg, compProg;
    private int uBrightTex, uThreshold, uKnee;
    private int uBlurTex, uBlurDir;
    private int uScene, uBloom, uIntensity;

    // Tunables (values that read well are found by eye; these are the starting point).
    private float threshold = 1.0f;
    private float knee = 0.6f;
    private float intensity = 0.9f;
    private int iterations = 5;

    private boolean initialized = false;

    public void init() {
        if (initialized) return;
        quad.init();
        String vs = ShaderUtil.readResource("/shaders/fullscreen.vert");
        brightProg = ShaderUtil.buildProgram(vs, ShaderUtil.readResource("/shaders/bloom_bright.frag"));
        blurProg   = ShaderUtil.buildProgram(vs, ShaderUtil.readResource("/shaders/bloom_blur.frag"));
        compProg   = ShaderUtil.buildProgram(vs, ShaderUtil.readResource("/shaders/bloom_composite.frag"));
        uBrightTex = loc(brightProg, "u_tex");
        uThreshold = loc(brightProg, "u_threshold");
        uKnee      = loc(brightProg, "u_knee");
        uBlurTex   = loc(blurProg, "u_tex");
        uBlurDir   = loc(blurProg, "u_dir");
        uScene     = loc(compProg, "u_scene");
        uBloom     = loc(compProg, "u_bloom");
        uIntensity = loc(compProg, "u_intensity");
        initialized = true;
    }

    private static int loc(int prog, String name) {
        int l = Gl.glGetUniformLocation(prog, name);
        if (l < 0) throw new IllegalStateException("bloom uniform missing: " + name);
        return l;
    }

    /** Bind the HDR scene target; the caller then renders the frame (2D + 3D) into it. */
    public void begin(int w, int h) {
        scene.resize(w, h);
        scene.bind();   // FBO bound + viewport w×h; caller's glClear clears it
    }

    /** Bloom + composite the rendered HDR frame to the default framebuffer (w×h). */
    public void end(int w, int h) {
        int hw = Math.max(1, w / 2), hh = Math.max(1, h / 2);
        blurA.resize(hw, hh);
        blurB.resize(hw, hh);

        Gl.glDisable(GL_BLEND);       // post passes overwrite; no alpha blending
        Gl.glDisable(GL_DEPTH_TEST);

        // Bright-pass: HDR scene -> blurA (half res).
        blurA.bind();
        Gl.glUseProgram(brightProg);
        scene.bindColor(0);
        Gl.glUniform1i(uBrightTex, 0);
        Gl.glUniform1f(uThreshold, threshold);
        Gl.glUniform1f(uKnee, knee);
        quad.draw();

        // Separable Gaussian, ping-pong: H (blurA->blurB) then V (blurB->blurA), N times.
        Gl.glUseProgram(blurProg);
        Gl.glUniform1i(uBlurTex, 0);
        for (int i = 0; i < iterations; i++) {
            blurB.bind();
            blurA.bindColor(0);
            Gl.glUniform2f(uBlurDir, 1f / hw, 0f);
            quad.draw();

            blurA.bind();
            blurB.bindColor(0);
            Gl.glUniform2f(uBlurDir, 0f, 1f / hh);
            quad.draw();
        }
        // Bloom result now in blurA.

        // Composite + tone-map to the default framebuffer.
        Gl.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        Gl.glViewport(0, 0, w, h);
        Gl.glUseProgram(compProg);
        scene.bindColor(0);
        Gl.glUniform1i(uScene, 0);
        blurA.bindColor(1);
        Gl.glUniform1i(uBloom, 1);
        Gl.glUniform1f(uIntensity, intensity);
        quad.draw();

        // Restore the state the 2D pipeline assumes for the next frame.
        Gl.glActiveTexture(GL_TEXTURE0);
        Gl.glEnable(GL_BLEND);
        Gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void close() {
        scene.close();
        blurA.close();
        blurB.close();
        quad.close();
        if (brightProg != 0) { Gl.glDeleteProgram(brightProg); brightProg = 0; }
        if (blurProg != 0)   { Gl.glDeleteProgram(blurProg); blurProg = 0; }
        if (compProg != 0)   { Gl.glDeleteProgram(compProg); compProg = 0; }
    }
}
