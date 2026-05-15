package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static sibarum.dasum.gui.natives.gl.Gl.GL_CLAMP_TO_EDGE;
import static sibarum.dasum.gui.natives.gl.Gl.GL_LINEAR;
import static sibarum.dasum.gui.natives.gl.Gl.GL_RGBA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_RGBA8;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_2D;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_MAG_FILTER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_MIN_FILTER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_WRAP_S;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_WRAP_T;
import static sibarum.dasum.gui.natives.gl.Gl.GL_UNSIGNED_BYTE;

/**
 * GL texture wrapper. PNGs decode through {@link PngDecoder} (avoiding
 * {@code javax.imageio.ImageIO}, which transitively pulls in AWT and is
 * unsupported under native-image).
 *
 * <p>Pixel rows are flipped vertically during upload so that the (0, 0)
 * pixel of the source image ends up at texture coordinate (s=0, t=1).
 * This puts a yOrigin="bottom" atlas (msdf-atlas-gen's default) in
 * alignment with straightforward UV math: {@code v = y / height}.
 */
@SuppressWarnings("restricted")
public final class Texture implements AutoCloseable {

    private int id;
    private final int width;
    private final int height;

    private Texture(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public int id() { return id; }
    public int width() { return width; }
    public int height() { return height; }

    public static Texture fromPngResource(String classpathPath) {
        try (InputStream in = Texture.class.getResourceAsStream(classpathPath)) {
            if (in == null) throw new IllegalStateException("PNG resource not found: " + classpathPath);
            return fromPngStream(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading PNG " + classpathPath, e);
        }
    }

    public static Texture fromPngStream(InputStream in) throws IOException {
        PngDecoder.DecodedImage img = PngDecoder.decode(in);
        return fromRgba(img.rgba(), img.width(), img.height());
    }

    public static Texture fromRgba(byte[] rgba, int w, int h) {
        if (rgba.length != w * h * 4) {
            throw new IllegalArgumentException("RGBA byte[] length " + rgba.length +
                " does not match " + w + "x" + h + " * 4");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate((long) w * h * 4);
            // Copy with row-flip so PNG row 0 (visual top) ends up at GL t=1.
            int rowBytes = w * 4;
            for (int y = 0; y < h; y++) {
                int srcOff = (h - 1 - y) * rowBytes;
                long dstOff = (long) y * rowBytes;
                MemorySegment.copy(rgba, srcOff, seg, ValueLayout.JAVA_BYTE, dstOff, rowBytes);
            }

            int id = Gl.glGenTexture();
            Gl.glBindTexture(GL_TEXTURE_2D, id);
            Gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, seg);
            Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            Gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            Gl.glBindTexture(GL_TEXTURE_2D, 0);
            return new Texture(id, w, h);
        }
    }

    public void bind(int unit) {
        Gl.glActiveTexture(Gl.GL_TEXTURE0 + unit);
        Gl.glBindTexture(GL_TEXTURE_2D, id);
    }

    @Override
    public void close() {
        if (id != 0) {
            Gl.glDeleteTexture(id);
            id = 0;
        }
    }
}
