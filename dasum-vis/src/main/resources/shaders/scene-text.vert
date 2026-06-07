#version 330 core
layout(location = 0) in vec2 a_offset;  // glyph-local, world units, relative to anchor
layout(location = 1) in vec2 a_uv;
uniform mat4 u_mvp;
uniform vec3 u_anchor;
uniform vec3 u_right;   // billboard basis (identity = world XY plane)
uniform vec3 u_up;
out vec2 v_uv;

void main() {
    v_uv = a_uv;
    vec3 world = u_anchor + u_right * a_offset.x + u_up * a_offset.y;
    gl_Position = u_mvp * vec4(world, 1.0);
}
