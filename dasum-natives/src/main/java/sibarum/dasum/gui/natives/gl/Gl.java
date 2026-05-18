package sibarum.dasum.gui.natives.gl;

import sibarum.dasum.gui.natives.glfw.Glfw;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Minimal OpenGL 3.3 core profile binding. Function pointers are resolved
 * via glfwGetProcAddress after a GL context becomes current, so call
 * {@link #load()} once per process before invoking any gl* method.
 */
@SuppressWarnings("restricted")
public final class Gl {

    public static final int GL_NO_ERROR             = 0;
    public static final int GL_FALSE                = 0;
    public static final int GL_TRUE                 = 1;

    public static final int GL_POINTS               = 0x0000;
    public static final int GL_TRIANGLES            = 0x0004;
    public static final int GL_UNSIGNED_BYTE        = 0x1401;
    public static final int GL_UNSIGNED_INT         = 0x1405;
    public static final int GL_FLOAT                = 0x1406;
    public static final int GL_TEXTURE_2D           = 0x0DE1;
    public static final int GL_BLEND                = 0x0BE2;
    public static final int GL_SCISSOR_TEST         = 0x0C11;
    public static final int GL_DEPTH_TEST           = 0x0B71;
    public static final int GL_PROGRAM_POINT_SIZE   = 0x8642;
    public static final int GL_SRC_ALPHA            = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA  = 0x0303;
    public static final int GL_TEXTURE0             = 0x84C0;
    public static final int GL_RGBA                 = 0x1908;
    public static final int GL_RGBA8               = 0x8058;
    public static final int GL_RED                  = 0x1903;
    public static final int GL_R8                   = 0x8229;
    public static final int GL_LINEAR               = 0x2601;
    public static final int GL_NEAREST              = 0x2600;
    public static final int GL_CLAMP_TO_EDGE        = 0x812F;
    public static final int GL_TEXTURE_MIN_FILTER   = 0x2800;
    public static final int GL_TEXTURE_MAG_FILTER   = 0x2801;
    public static final int GL_TEXTURE_WRAP_S       = 0x2802;
    public static final int GL_TEXTURE_WRAP_T       = 0x2803;

    public static final int GL_COLOR_BUFFER_BIT     = 0x4000;
    public static final int GL_DEPTH_BUFFER_BIT     = 0x0100;

    public static final int GL_VERTEX_SHADER        = 0x8B31;
    public static final int GL_FRAGMENT_SHADER      = 0x8B30;
    public static final int GL_COMPILE_STATUS       = 0x8B81;
    public static final int GL_LINK_STATUS          = 0x8B82;
    public static final int GL_INFO_LOG_LENGTH      = 0x8B84;

    public static final int GL_ARRAY_BUFFER         = 0x8892;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    public static final int GL_STATIC_DRAW          = 0x88E4;
    public static final int GL_DYNAMIC_DRAW         = 0x88E8;
    public static final int GL_STREAM_DRAW          = 0x88E0;

    private static final Linker LINKER = Linker.nativeLinker();

    private static MethodHandle GL_CLEAR;
    private static MethodHandle GL_CLEAR_COLOR;
    private static MethodHandle GL_VIEWPORT;
    private static MethodHandle GL_SCISSOR;
    private static MethodHandle GL_GET_ERROR;
    private static MethodHandle GL_ENABLE;
    private static MethodHandle GL_DISABLE;
    private static MethodHandle GL_BLEND_FUNC;

    private static MethodHandle GL_CREATE_SHADER;
    private static MethodHandle GL_SHADER_SOURCE;
    private static MethodHandle GL_COMPILE_SHADER;
    private static MethodHandle GL_GET_SHADERIV;
    private static MethodHandle GL_GET_SHADER_INFO_LOG;
    private static MethodHandle GL_DELETE_SHADER;

    private static MethodHandle GL_CREATE_PROGRAM;
    private static MethodHandle GL_ATTACH_SHADER;
    private static MethodHandle GL_LINK_PROGRAM;
    private static MethodHandle GL_GET_PROGRAMIV;
    private static MethodHandle GL_GET_PROGRAM_INFO_LOG;
    private static MethodHandle GL_USE_PROGRAM;
    private static MethodHandle GL_DELETE_PROGRAM;
    private static MethodHandle GL_GET_UNIFORM_LOCATION;
    private static MethodHandle GL_UNIFORM_1I;
    private static MethodHandle GL_UNIFORM_1F;
    private static MethodHandle GL_UNIFORM_4F;
    private static MethodHandle GL_UNIFORM_MATRIX_4FV;

    private static MethodHandle GL_GEN_VERTEX_ARRAYS;
    private static MethodHandle GL_BIND_VERTEX_ARRAY;
    private static MethodHandle GL_DELETE_VERTEX_ARRAYS;

    private static MethodHandle GL_GEN_BUFFERS;
    private static MethodHandle GL_BIND_BUFFER;
    private static MethodHandle GL_BUFFER_DATA;
    private static MethodHandle GL_BUFFER_SUB_DATA;
    private static MethodHandle GL_DELETE_BUFFERS;

    private static MethodHandle GL_VERTEX_ATTRIB_POINTER;
    private static MethodHandle GL_ENABLE_VERTEX_ATTRIB_ARRAY;

    private static MethodHandle GL_DRAW_ARRAYS;
    private static MethodHandle GL_DRAW_ELEMENTS;

    private static MethodHandle GL_GEN_TEXTURES;
    private static MethodHandle GL_BIND_TEXTURE;
    private static MethodHandle GL_TEX_IMAGE_2D;
    private static MethodHandle GL_TEX_PARAMETERI;
    private static MethodHandle GL_ACTIVE_TEXTURE;
    private static MethodHandle GL_DELETE_TEXTURES;

    private static volatile boolean loaded = false;

    private Gl() {}

    public static synchronized void load() {
        if (loaded) return;

        GL_CLEAR              = h("glClear",            FunctionDescriptor.ofVoid(JAVA_INT));
        GL_CLEAR_COLOR        = h("glClearColor",       FunctionDescriptor.ofVoid(JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));
        GL_VIEWPORT           = h("glViewport",         FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
        GL_SCISSOR            = h("glScissor",          FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
        GL_GET_ERROR          = h("glGetError",         FunctionDescriptor.of(JAVA_INT));
        GL_ENABLE             = h("glEnable",           FunctionDescriptor.ofVoid(JAVA_INT));
        GL_DISABLE            = h("glDisable",          FunctionDescriptor.ofVoid(JAVA_INT));
        GL_BLEND_FUNC         = h("glBlendFunc",        FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));

        GL_CREATE_SHADER      = h("glCreateShader",     FunctionDescriptor.of(JAVA_INT, JAVA_INT));
        GL_SHADER_SOURCE      = h("glShaderSource",     FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        GL_COMPILE_SHADER     = h("glCompileShader",    FunctionDescriptor.ofVoid(JAVA_INT));
        GL_GET_SHADERIV       = h("glGetShaderiv",      FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS));
        GL_GET_SHADER_INFO_LOG= h("glGetShaderInfoLog", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        GL_DELETE_SHADER      = h("glDeleteShader",     FunctionDescriptor.ofVoid(JAVA_INT));

        GL_CREATE_PROGRAM     = h("glCreateProgram",    FunctionDescriptor.of(JAVA_INT));
        GL_ATTACH_SHADER      = h("glAttachShader",     FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
        GL_LINK_PROGRAM       = h("glLinkProgram",      FunctionDescriptor.ofVoid(JAVA_INT));
        GL_GET_PROGRAMIV      = h("glGetProgramiv",     FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS));
        GL_GET_PROGRAM_INFO_LOG = h("glGetProgramInfoLog", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        GL_USE_PROGRAM        = h("glUseProgram",       FunctionDescriptor.ofVoid(JAVA_INT));
        GL_DELETE_PROGRAM     = h("glDeleteProgram",    FunctionDescriptor.ofVoid(JAVA_INT));
        GL_GET_UNIFORM_LOCATION = h("glGetUniformLocation", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
        GL_UNIFORM_1I         = h("glUniform1i",        FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
        GL_UNIFORM_1F         = h("glUniform1f",        FunctionDescriptor.ofVoid(JAVA_INT, JAVA_FLOAT));
        GL_UNIFORM_4F         = h("glUniform4f",        FunctionDescriptor.ofVoid(JAVA_INT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));
        GL_UNIFORM_MATRIX_4FV = h("glUniformMatrix4fv", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_BYTE, ADDRESS));

        GL_GEN_VERTEX_ARRAYS    = h("glGenVertexArrays",    FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
        GL_BIND_VERTEX_ARRAY    = h("glBindVertexArray",    FunctionDescriptor.ofVoid(JAVA_INT));
        GL_DELETE_VERTEX_ARRAYS = h("glDeleteVertexArrays", FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));

        GL_GEN_BUFFERS        = h("glGenBuffers",       FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
        GL_BIND_BUFFER        = h("glBindBuffer",       FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
        GL_BUFFER_DATA        = h("glBufferData",       FunctionDescriptor.ofVoid(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT));
        GL_BUFFER_SUB_DATA    = h("glBufferSubData",    FunctionDescriptor.ofVoid(JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS));
        GL_DELETE_BUFFERS     = h("glDeleteBuffers",    FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));

        GL_VERTEX_ATTRIB_POINTER     = h("glVertexAttribPointer",     FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_BYTE, JAVA_INT, ADDRESS));
        GL_ENABLE_VERTEX_ATTRIB_ARRAY= h("glEnableVertexAttribArray", FunctionDescriptor.ofVoid(JAVA_INT));

        GL_DRAW_ARRAYS        = h("glDrawArrays",       FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT));
        GL_DRAW_ELEMENTS      = h("glDrawElements",     FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));

        GL_GEN_TEXTURES       = h("glGenTextures",      FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
        GL_BIND_TEXTURE       = h("glBindTexture",      FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
        GL_TEX_IMAGE_2D       = h("glTexImage2D",       FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
        GL_TEX_PARAMETERI     = h("glTexParameteri",    FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT));
        GL_ACTIVE_TEXTURE     = h("glActiveTexture",    FunctionDescriptor.ofVoid(JAVA_INT));
        GL_DELETE_TEXTURES    = h("glDeleteTextures",   FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));

        loaded = true;
    }

    private static MethodHandle h(String name, FunctionDescriptor desc) {
        MemorySegment addr = Glfw.glfwGetProcAddress(name);
        if (addr == null || addr.address() == 0L) {
            throw new UnsatisfiedLinkError("GL function not available: " + name);
        }
        return LINKER.downcallHandle(addr, desc);
    }

    public static void glClear(int mask) {
        try { GL_CLEAR.invokeExact(mask); } catch (Throwable t) { throw rt(t); }
    }

    public static void glClearColor(float r, float g, float b, float a) {
        try { GL_CLEAR_COLOR.invokeExact(r, g, b, a); } catch (Throwable t) { throw rt(t); }
    }

    public static void glViewport(int x, int y, int w, int h) {
        try { GL_VIEWPORT.invokeExact(x, y, w, h); } catch (Throwable t) { throw rt(t); }
    }

    public static void glScissor(int x, int y, int w, int h) {
        try { GL_SCISSOR.invokeExact(x, y, w, h); } catch (Throwable t) { throw rt(t); }
    }

    public static int glGetError() {
        try { return (int) GL_GET_ERROR.invokeExact(); } catch (Throwable t) { throw rt(t); }
    }

    public static void glEnable(int cap)  { try { GL_ENABLE.invokeExact(cap);  } catch (Throwable t) { throw rt(t); } }
    public static void glDisable(int cap) { try { GL_DISABLE.invokeExact(cap); } catch (Throwable t) { throw rt(t); } }
    public static void glBlendFunc(int sfactor, int dfactor) {
        try { GL_BLEND_FUNC.invokeExact(sfactor, dfactor); } catch (Throwable t) { throw rt(t); }
    }

    public static int glCreateShader(int type) {
        try { return (int) GL_CREATE_SHADER.invokeExact(type); } catch (Throwable t) { throw rt(t); }
    }

    public static void glShaderSource(int shader, String source) {
        try (Arena arena = Arena.ofConfined()) {
            byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            MemorySegment srcSeg = arena.allocate(bytes.length + 1);
            MemorySegment.copy(bytes, 0, srcSeg, JAVA_BYTE, 0, bytes.length);
            srcSeg.set(JAVA_BYTE, bytes.length, (byte) 0);
            MemorySegment stringsArray = arena.allocate(ADDRESS);
            stringsArray.set(ADDRESS, 0, srcSeg);
            GL_SHADER_SOURCE.invokeExact(shader, 1, stringsArray, MemorySegment.NULL);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glCompileShader(int shader) {
        try { GL_COMPILE_SHADER.invokeExact(shader); } catch (Throwable t) { throw rt(t); }
    }

    public static int glGetShaderi(int shader, int pname) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_INT);
            GL_GET_SHADERIV.invokeExact(shader, pname, out);
            return out.get(JAVA_INT, 0);
        } catch (Throwable t) { throw rt(t); }
    }

    public static String glGetShaderInfoLog(int shader) {
        int len = glGetShaderi(shader, GL_INFO_LOG_LENGTH);
        if (len <= 0) return "";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(len);
            MemorySegment writtenLen = arena.allocate(JAVA_INT);
            GL_GET_SHADER_INFO_LOG.invokeExact(shader, len, writtenLen, buf);
            int actual = writtenLen.get(JAVA_INT, 0);
            byte[] bytes = new byte[actual];
            for (int i = 0; i < actual; i++) bytes[i] = buf.get(JAVA_BYTE, i);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glDeleteShader(int shader) {
        try { GL_DELETE_SHADER.invokeExact(shader); } catch (Throwable t) { throw rt(t); }
    }

    public static int glCreateProgram() {
        try { return (int) GL_CREATE_PROGRAM.invokeExact(); } catch (Throwable t) { throw rt(t); }
    }

    public static void glAttachShader(int program, int shader) {
        try { GL_ATTACH_SHADER.invokeExact(program, shader); } catch (Throwable t) { throw rt(t); }
    }

    public static void glLinkProgram(int program) {
        try { GL_LINK_PROGRAM.invokeExact(program); } catch (Throwable t) { throw rt(t); }
    }

    public static int glGetProgrami(int program, int pname) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_INT);
            GL_GET_PROGRAMIV.invokeExact(program, pname, out);
            return out.get(JAVA_INT, 0);
        } catch (Throwable t) { throw rt(t); }
    }

    public static String glGetProgramInfoLog(int program) {
        int len = glGetProgrami(program, GL_INFO_LOG_LENGTH);
        if (len <= 0) return "";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(len);
            MemorySegment writtenLen = arena.allocate(JAVA_INT);
            GL_GET_PROGRAM_INFO_LOG.invokeExact(program, len, writtenLen, buf);
            int actual = writtenLen.get(JAVA_INT, 0);
            byte[] bytes = new byte[actual];
            for (int i = 0; i < actual; i++) bytes[i] = buf.get(JAVA_BYTE, i);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glUseProgram(int program) {
        try { GL_USE_PROGRAM.invokeExact(program); } catch (Throwable t) { throw rt(t); }
    }

    public static void glDeleteProgram(int program) {
        try { GL_DELETE_PROGRAM.invokeExact(program); } catch (Throwable t) { throw rt(t); }
    }

    public static int glGetUniformLocation(int program, String name) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nameSeg = arena.allocateFrom(name);
            return (int) GL_GET_UNIFORM_LOCATION.invokeExact(program, nameSeg);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glUniform1i(int location, int value) {
        try { GL_UNIFORM_1I.invokeExact(location, value); } catch (Throwable t) { throw rt(t); }
    }

    public static void glUniform1f(int location, float value) {
        try { GL_UNIFORM_1F.invokeExact(location, value); } catch (Throwable t) { throw rt(t); }
    }

    public static void glUniform4f(int location, float x, float y, float z, float w) {
        try { GL_UNIFORM_4F.invokeExact(location, x, y, z, w); } catch (Throwable t) { throw rt(t); }
    }

    public static void glUniformMatrix4fv(int location, boolean transpose, float[] matrix) {
        if (matrix.length != 16) throw new IllegalArgumentException("matrix must be 16 floats");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(JAVA_FLOAT, 16);
            for (int i = 0; i < 16; i++) seg.setAtIndex(JAVA_FLOAT, i, matrix[i]);
            GL_UNIFORM_MATRIX_4FV.invokeExact(location, 1, (byte)(transpose ? 1 : 0), seg);
        } catch (Throwable t) { throw rt(t); }
    }

    public static int glGenVertexArray() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_INT);
            GL_GEN_VERTEX_ARRAYS.invokeExact(1, out);
            return out.get(JAVA_INT, 0);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glBindVertexArray(int vao) {
        try { GL_BIND_VERTEX_ARRAY.invokeExact(vao); } catch (Throwable t) { throw rt(t); }
    }

    public static void glDeleteVertexArray(int vao) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(JAVA_INT);
            seg.set(JAVA_INT, 0, vao);
            GL_DELETE_VERTEX_ARRAYS.invokeExact(1, seg);
        } catch (Throwable t) { throw rt(t); }
    }

    public static int glGenBuffer() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_INT);
            GL_GEN_BUFFERS.invokeExact(1, out);
            return out.get(JAVA_INT, 0);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glBindBuffer(int target, int buffer) {
        try { GL_BIND_BUFFER.invokeExact(target, buffer); } catch (Throwable t) { throw rt(t); }
    }

    public static void glBufferData(int target, float[] data, int usage) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(JAVA_FLOAT, data.length);
            for (int i = 0; i < data.length; i++) seg.setAtIndex(JAVA_FLOAT, i, data[i]);
            GL_BUFFER_DATA.invokeExact(target, (long) data.length * Float.BYTES, seg, usage);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glBufferData(int target, int[] data, int usage) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(JAVA_INT, data.length);
            for (int i = 0; i < data.length; i++) seg.setAtIndex(JAVA_INT, i, data[i]);
            GL_BUFFER_DATA.invokeExact(target, (long) data.length * Integer.BYTES, seg, usage);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glBufferDataNull(int target, long size, int usage) {
        try {
            GL_BUFFER_DATA.invokeExact(target, size, MemorySegment.NULL, usage);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glBufferSubData(int target, long offset, float[] data) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(JAVA_FLOAT, data.length);
            for (int i = 0; i < data.length; i++) seg.setAtIndex(JAVA_FLOAT, i, data[i]);
            GL_BUFFER_SUB_DATA.invokeExact(target, offset, (long) data.length * Float.BYTES, seg);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glDeleteBuffer(int buffer) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(JAVA_INT);
            seg.set(JAVA_INT, 0, buffer);
            GL_DELETE_BUFFERS.invokeExact(1, seg);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offsetBytes) {
        try {
            GL_VERTEX_ATTRIB_POINTER.invokeExact(index, size, type, (byte)(normalized ? 1 : 0), stride, MemorySegment.ofAddress(offsetBytes));
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glEnableVertexAttribArray(int index) {
        try { GL_ENABLE_VERTEX_ATTRIB_ARRAY.invokeExact(index); } catch (Throwable t) { throw rt(t); }
    }

    public static void glDrawArrays(int mode, int first, int count) {
        try { GL_DRAW_ARRAYS.invokeExact(mode, first, count); } catch (Throwable t) { throw rt(t); }
    }

    public static void glDrawElements(int mode, int count, int type, long indexOffsetBytes) {
        try { GL_DRAW_ELEMENTS.invokeExact(mode, count, type, MemorySegment.ofAddress(indexOffsetBytes)); }
        catch (Throwable t) { throw rt(t); }
    }

    public static int glGenTexture() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_INT);
            GL_GEN_TEXTURES.invokeExact(1, out);
            return out.get(JAVA_INT, 0);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glBindTexture(int target, int texture) {
        try { GL_BIND_TEXTURE.invokeExact(target, texture); } catch (Throwable t) { throw rt(t); }
    }

    public static void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, MemorySegment pixels) {
        MemorySegment data = (pixels == null) ? MemorySegment.NULL : pixels;
        try {
            GL_TEX_IMAGE_2D.invokeExact(target, level, internalFormat, width, height, border, format, type, data);
        } catch (Throwable t) { throw rt(t); }
    }

    public static void glTexParameteri(int target, int pname, int param) {
        try { GL_TEX_PARAMETERI.invokeExact(target, pname, param); } catch (Throwable t) { throw rt(t); }
    }

    public static void glActiveTexture(int unit) {
        try { GL_ACTIVE_TEXTURE.invokeExact(unit); } catch (Throwable t) { throw rt(t); }
    }

    public static void glDeleteTexture(int texture) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(JAVA_INT);
            seg.set(JAVA_INT, 0, texture);
            GL_DELETE_TEXTURES.invokeExact(1, seg);
        } catch (Throwable t) { throw rt(t); }
    }

    private static RuntimeException rt(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error err) throw err;
        return new RuntimeException(t);
    }
}
