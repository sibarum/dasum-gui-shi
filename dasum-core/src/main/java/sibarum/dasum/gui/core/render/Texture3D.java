package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static sibarum.dasum.gui.natives.gl.Gl.GL_CLAMP_TO_EDGE;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FLOAT;
import static sibarum.dasum.gui.natives.gl.Gl.GL_LINEAR;
import static sibarum.dasum.gui.natives.gl.Gl.GL_RGBA;
import static sibarum.dasum.gui.natives.gl.Gl.GL_RGBA32F;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_3D;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_MAG_FILTER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_MIN_FILTER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_WRAP_R;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_WRAP_S;
import static sibarum.dasum.gui.natives.gl.Gl.GL_TEXTURE_WRAP_T;

/**
 * A floating-point 3D texture ({@code GL_RGBA32F}), the storage a volumetric raymarcher samples
 * with a {@code sampler3D}. The 3D sibling of {@link Texture}: LINEAR (trilinear) filtering so a
 * coarse voxel grid reads as a continuous field, CLAMP_TO_EDGE on all three axes. RGBA float voxels
 * are supplied row-major with x fastest, then y, then z ({@code index = (z*ny + y)*nx + x}).
 */
@SuppressWarnings("restricted")
public final class Texture3D implements AutoCloseable {

    private int id;
    private final int nx;
    private final int ny;
    private final int nz;

    private Texture3D(int id, int nx, int ny, int nz) {
        this.id = id;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    public int id() { return id; }
    public int nx() { return nx; }
    public int ny() { return ny; }
    public int nz() { return nz; }

    /** Upload {@code rgba} (length {@code nx*ny*nz*4}, x-fastest) as an RGBA32F 3D texture. */
    public static Texture3D fromRgbaFloats(float[] rgba, int nx, int ny, int nz) {
        long voxels = (long) nx * ny * nz;
        if (rgba.length != voxels * 4) {
            throw new IllegalArgumentException("RGBA float[] length " + rgba.length
                    + " does not match " + nx + "x" + ny + "x" + nz + " * 4");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(voxels * 4 * Float.BYTES);
            MemorySegment.copy(rgba, 0, seg, ValueLayout.JAVA_FLOAT, 0L, rgba.length);

            int id = Gl.glGenTexture();
            Gl.glBindTexture(GL_TEXTURE_3D, id);
            Gl.glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA32F, nx, ny, nz, 0, GL_RGBA, GL_FLOAT, seg);
            Gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            Gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            Gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            Gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            Gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
            Gl.glBindTexture(GL_TEXTURE_3D, 0);
            return new Texture3D(id, nx, ny, nz);
        }
    }

    public void bind(int unit) {
        Gl.glActiveTexture(Gl.GL_TEXTURE0 + unit);
        Gl.glBindTexture(GL_TEXTURE_3D, id);
    }

    @Override
    public void close() {
        if (id != 0) {
            Gl.glDeleteTexture(id);
            id = 0;
        }
    }
}
