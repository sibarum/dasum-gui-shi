package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.natives.gl.Gl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static sibarum.dasum.gui.natives.gl.Gl.GL_COMPILE_STATUS;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FALSE;
import static sibarum.dasum.gui.natives.gl.Gl.GL_FRAGMENT_SHADER;
import static sibarum.dasum.gui.natives.gl.Gl.GL_LINK_STATUS;
import static sibarum.dasum.gui.natives.gl.Gl.GL_VERTEX_SHADER;

public final class ShaderUtil {

    private ShaderUtil() {}

    public static String readResource(String classpathPath) {
        try (InputStream in = ShaderUtil.class.getResourceAsStream(classpathPath)) {
            if (in == null) throw new IllegalStateException("Resource not found: " + classpathPath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading " + classpathPath + ": " + e.getMessage(), e);
        }
    }

    public static int buildProgram(String vertexSource, String fragmentSource) {
        int vs = compile(GL_VERTEX_SHADER, vertexSource, "vertex");
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSource, "fragment");
        int program = Gl.glCreateProgram();
        Gl.glAttachShader(program, vs);
        Gl.glAttachShader(program, fs);
        Gl.glLinkProgram(program);
        if (Gl.glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = Gl.glGetProgramInfoLog(program);
            Gl.glDeleteProgram(program);
            throw new IllegalStateException("Program link failed: " + log);
        }
        Gl.glDeleteShader(vs);
        Gl.glDeleteShader(fs);
        return program;
    }

    private static int compile(int type, String source, String label) {
        int shader = Gl.glCreateShader(type);
        Gl.glShaderSource(shader, source);
        Gl.glCompileShader(shader);
        if (Gl.glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = Gl.glGetShaderInfoLog(shader);
            Gl.glDeleteShader(shader);
            throw new IllegalStateException(label + " shader compile failed: " + log);
        }
        return shader;
    }
}
