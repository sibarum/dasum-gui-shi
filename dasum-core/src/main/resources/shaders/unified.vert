#version 330 core
// The one UI vertex shader. Every 2D primitive — flat fill (quad/triangle),
// rounded/bordered rect, and MSDF glyph — flows through this single pipeline in
// painter's (submission) order, so cross-primitive z-order is correct by
// construction (no separate buckets to mis-order). The fragment shader branches
// on a_kind at its one point of divergence: how coverage is computed.
layout(location = 0) in vec2  a_pos;         // screen-space position (px, Y-down)
layout(location = 1) in float a_kind;        // 0 = flat, 1 = rounded, 2 = glyph
layout(location = 2) in vec4  a_color;       // fill / glyph tint (per-vertex)
layout(location = 3) in vec2  a_coord;       // rounded: local px from center; glyph: atlas uv
layout(location = 4) in vec2  a_halfSize;    // rounded: half extent (px)
layout(location = 5) in vec4  a_radii;       // rounded: corner radii tl, tr, br, bl (px)
layout(location = 6) in vec4  a_border;      // rounded: border color
layout(location = 7) in float a_borderWidth; // rounded: inset border thickness (px)
layout(location = 8) in float a_edgePx;      // glyph: SDF edge shift (screen px)

uniform mat4 u_projection;

flat out float v_kind;
out      vec4  v_color;
out      vec2  v_coord;
flat out vec2  v_halfSize;
flat out vec4  v_radii;
flat out vec4  v_border;
flat out float v_borderWidth;
out      float v_edgePx;

void main() {
    v_kind        = a_kind;
    v_color       = a_color;
    v_coord       = a_coord;
    v_halfSize    = a_halfSize;
    v_radii       = a_radii;
    v_border      = a_border;
    v_borderWidth = a_borderWidth;
    v_edgePx      = a_edgePx;
    gl_Position   = u_projection * vec4(a_pos, 0.0, 1.0);
}
