#version 330 core
layout(location = 0) in vec2 a_offset;  // glyph-local, relative to anchor (world units, or px if u_pixelMode)
layout(location = 1) in vec2 a_uv;
uniform mat4 u_mvp;
uniform vec3 u_anchor;
uniform vec3 u_right;       // billboard basis (identity = world XY plane)
uniform vec3 u_up;
uniform vec2 u_viewportPx;  // viewport size in pixels (for u_pixelMode)
uniform int  u_pixelMode;   // 1 = glyph offsets are fixed screen pixels; 0 = world units
out vec2 v_uv;

void main() {
    v_uv = a_uv;
    if (u_pixelMode == 1) {
        // Screen-space billboard: project the anchor through the SAME MVP (so position, depth and
        // camera-sync are identical to world-space text), then offset the glyph corner by a FIXED
        // number of pixels in clip space. The * clip.w cancels the perspective divide, so the glyph
        // stays the same pixel size at any depth / aspect — no skew under a non-uniform fill camera.
        vec4 clip = u_mvp * vec4(u_anchor, 1.0);
        gl_Position = clip + vec4(a_offset * (2.0 / u_viewportPx) * clip.w, 0.0, 0.0);
    } else {
        vec3 world = u_anchor + u_right * a_offset.x + u_up * a_offset.y;
        gl_Position = u_mvp * vec4(world, 1.0);
    }
}
