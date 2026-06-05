#version 330 core
layout(location = 0) in vec2 a_pos;
layout(location = 1) in vec2 a_uv;
layout(location = 2) in vec4 a_color;
layout(location = 3) in float a_edgePx;
uniform mat4 u_projection;
out vec2 v_uv;
out vec4 v_color;
out float v_edgePx;
void main() {
    v_uv = a_uv;
    v_color = a_color;
    v_edgePx = a_edgePx;
    gl_Position = u_projection * vec4(a_pos, 0.0, 1.0);
}
