#version 330 core

in      vec2  v_local;       // fragment position relative to rect center (px, Y-down)
flat in vec2  v_halfSize;    // half extent of the rect (px)
flat in vec4  v_radii;       // tl, tr, br, bl (px)
flat in vec4  v_fill;
flat in vec4  v_border;
flat in float v_borderWidth;

out vec4 fragColor;

// Signed distance to a rounded box with per-corner radii.
// p is relative to the box center; b is the half extent. Negative inside.
float sdRoundBox(vec2 p, vec2 b, vec4 radii) {
    // Select the radius for the quadrant this fragment falls in.
    // radii = (tl, tr, br, bl); Y-down: y < 0 is the top half.
    float r;
    if (p.x >= 0.0) {
        r = (p.y >= 0.0) ? radii.z : radii.y; // right: bottom-right / top-right
    } else {
        r = (p.y >= 0.0) ? radii.w : radii.x; // left:  bottom-left  / top-left
    }
    r = min(r, min(b.x, b.y)); // clamp so an over-specified radius can't invert
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}

void main() {
    float d = sdRoundBox(v_local, v_halfSize, v_radii);

    // Anti-aliased outer coverage: 1 well inside, 0 well outside the edge.
    float aa = max(fwidth(d), 1e-4);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (coverage <= 0.0) discard;

    // Border band occupies [-borderWidth, 0]; deeper than that is pure fill.
    // inner: 0 deep inside (fill), 1 within the border band.
    float inner = (v_borderWidth > 0.0)
        ? smoothstep(-v_borderWidth - aa, -v_borderWidth + aa, d)
        : 0.0;

    vec4 color = mix(v_fill, v_border, inner);
    fragColor = vec4(color.rgb, color.a * coverage);
}
