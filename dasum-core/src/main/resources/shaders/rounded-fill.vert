#version 330 core
layout(location = 0) in vec2  a_pos;         // screen-space vertex position (px, Y-down)
layout(location = 1) in vec2  a_local;       // position relative to the rect center (px)
layout(location = 2) in vec2  a_halfSize;    // half extent of the rect (px)
layout(location = 3) in vec4  a_radii;       // corner radii tl, tr, br, bl (px)
layout(location = 4) in vec4  a_fill;        // interior fill color (RGBA)
layout(location = 5) in vec4  a_border;      // border color (RGBA)
layout(location = 6) in float a_borderWidth; // inset border thickness (px)

uniform mat4 u_projection;

out      vec2  v_local;
flat out vec2  v_halfSize;
flat out vec4  v_radii;
flat out vec4  v_fill;
flat out vec4  v_border;
flat out float v_borderWidth;

void main() {
    v_local       = a_local;
    v_halfSize    = a_halfSize;
    v_radii       = a_radii;
    v_fill        = a_fill;
    v_border      = a_border;
    v_borderWidth = a_borderWidth;
    gl_Position   = u_projection * vec4(a_pos, 0.0, 1.0);
}
