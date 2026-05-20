#version 330 core
in vec3 v_color;
out vec4 fragColor;

// Per-fragment colour. OpenGL interpolates v_color linearly along the
// line between the two endpoint vertices, producing the per-endpoint
// gradient.
void main() {
    fragColor = vec4(v_color, 1.0);
}
