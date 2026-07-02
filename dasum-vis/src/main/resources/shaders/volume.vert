#version 330 core
layout(location = 0) in vec3 a_pos;   // unit cube corner in [-1, 1]
uniform mat4 u_mvp;
uniform vec3 u_center;
uniform vec3 u_half;                  // per-axis half-extent (the field is a box, not a cube)
out vec3 v_world;

void main() {
    v_world = u_center + a_pos * u_half;
    gl_Position = u_mvp * vec4(v_world, 1.0);
}
