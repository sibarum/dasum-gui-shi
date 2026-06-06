#version 330 core
in vec3 v_color;
uniform float u_opacity;
out vec4 fragColor;

void main() {
    fragColor = vec4(v_color, u_opacity);
}
