package sibarum.dasum.gui.vis.render;

import sibarum.dasum.gui.natives.gl.Gl;

import java.lang.foreign.MemorySegment;

import static sibarum.dasum.gui.natives.gl.Gl.GL_CLAMP_TO_EDGE;
import static sibarum.dasum.gui.natives.gl.Gl.GL_COLOR_ATTACHMENT0;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_ATTACHMENT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_DEPTH_COMPONENT24;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FRAMEBUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_LINEAR;
import static sibarum.dasum.gui.natives.gl.Gl.GL_RENDERBUFFER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_RGBA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_RGBA16F;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_2D;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_MAG_FILTER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_MIN_FILTER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_WRAP_S;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_WRAP_T;

/**
 * An offscreen HDR render target: an FBO with an {@code RGBA16F} colour texture (LINEAR/clamp, so
 * downsampled reads for bloom are smooth) and — when {@code withDepth} — a {@code DEPTH_COMPONENT24}
 * renderbuffer so 3D depth-testing works while rendering into it. {@link #resize} recreates the GL
 * objects when the framebuffer size changes; {@link #bind} makes it current and sets the viewport.
 * Main/GL-thread only.
 */
final class HdrTarget implements AutoCloseable {

    private final boolean withDepth;
    private int fbo = 0;
    private int colorTex = 0;
    private int depthRb = 0;
    private int w = 0;
    private int h = 0;

    HdrTarget(boolean withDepth) { this.withDepth = withDepth; }

    int colorTex() { return colorTex; }
    int width() { return w; }
    int height() { return h; }

    /** (Re)allocate to {@code w x h} if the size changed; no-op otherwise. */
    void resize(int width, int height) {
        if (width == w && height == h && fbo != 0) return;
        close();
        w = Math.max(1, width);
        h = Math.max(1, height);

        fbo = Gl.glGenFramebuffer();
        Gl.glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        colorTex = Gl.glGenTexture();
        Gl.glBindTexture(GL_TEXTURE_2D, colorTex);
        Gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, MemorySegment.NULL);
        Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        Gl.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);

        if (withDepth) {
            depthRb = Gl.glGenRenderbuffer();
            Gl.glBindRenderbuffer(GL_RENDERBUFFER, depthRb);
            Gl.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
            Gl.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRb);
            Gl.glBindRenderbuffer(GL_RENDERBUFFER, 0);
        }

        Gl.glBindTexture(GL_TEXTURE_2D, 0);
        Gl.glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** Bind as the draw target and set the viewport to its size. */
    void bind() {
        Gl.glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        Gl.glViewport(0, 0, w, h);
    }

    /** Bind the colour texture to a sampler unit (for the post-process passes). */
    void bindColor(int unit) {
        Gl.glActiveTexture(Gl.GL_TEXTURE0 + unit);
        Gl.glBindTexture(GL_TEXTURE_2D, colorTex);
    }

    @Override
    public void close() {
        if (colorTex != 0) { Gl.glDeleteTexture(colorTex); colorTex = 0; }
        if (depthRb != 0)  { Gl.glDeleteRenderbuffer(depthRb); depthRb = 0; }
        if (fbo != 0)      { Gl.glDeleteFramebuffer(fbo); fbo = 0; }
        w = 0; h = 0;
    }
}
