#version 330 core
layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec3 a_color;
layout(location = 2) in float a_size;
uniform mat4 u_mvp;
// 0 = a_size is screen pixels (scatter dots); > 0 = a_size is a WORLD diameter and the
// point shrinks with distance — u_point_scale carries proj[1][1] * viewportHeightPx / 2.
uniform float u_point_scale;
out vec3 v_color;

void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
    gl_PointSize = u_point_scale > 0.0
        ? a_size * u_point_scale / max(gl_Position.w, 1e-4)   // perspective: world size / distance
        : a_size;                                             // fixed screen pixels
}
