#version 330 core
// The one UI fragment shader. Shared work: interpolate colour, compute the
// screen-space anti-aliasing fringe, blend. The single point of divergence is
// how COVERAGE is obtained:
//   kind 0 (flat)    -> coverage = 1 (hard-edged solid fill)
//   kind 1 (rounded) -> analytic rounded-box signed distance
//   kind 2 (glyph)   -> MSDF median sampled from the atlas
// Kind is uniform across a primitive's fragments, so the branch is warp-coherent
// (divergence only in the sliver of warps straddling a primitive edge).
flat in float v_kind;
in      vec4  v_color;
in      vec2  v_coord;
flat in vec2  v_halfSize;
flat in vec4  v_radii;
flat in vec4  v_border;
flat in float v_borderWidth;
in      float v_edgePx;

uniform sampler2D u_atlas;
uniform float u_distanceRange;

out vec4 fragColor;

// Signed distance to a rounded box with per-corner radii; p relative to center,
// b the half extent. Negative inside. (Unchanged from the old rounded shader.)
float sdRoundBox(vec2 p, vec2 b, vec4 radii) {
    float r;
    if (p.x >= 0.0) r = (p.y >= 0.0) ? radii.z : radii.y; // right: br / tr
    else            r = (p.y >= 0.0) ? radii.w : radii.x; // left:  bl / tl
    r = min(r, min(b.x, b.y));
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    int kind = int(v_kind + 0.5);
    vec4 outc;

    if (kind == 2) {
        // Glyph: MSDF coverage (unchanged from the old msdf shader).
        vec3 msd = texture(u_atlas, v_coord).rgb;
        float sd = median(msd.r, msd.g, msd.b);
        vec2 unitRange = vec2(u_distanceRange) / vec2(textureSize(u_atlas, 0));
        vec2 screenTexSize = vec2(1.0) / fwidth(v_coord);
        float spr = max(0.5 * dot(unitRange, screenTexSize), 1.0);
        float edge = min(v_edgePx, 0.45 * spr);
        float opacity = clamp(spr * (sd - 0.5) + edge + 0.5, 0.0, 1.0);
        outc = vec4(v_color.rgb, v_color.a * opacity);
    } else if (kind == 1) {
        // Rounded/bordered rect (unchanged from the old rounded shader).
        float d = sdRoundBox(v_coord, v_halfSize, v_radii);
        float aa = max(fwidth(d), 1e-4);
        float coverage = 1.0 - smoothstep(-aa, aa, d);
        float inner = (v_borderWidth > 0.0)
            ? smoothstep(-v_borderWidth - aa, -v_borderWidth + aa, d)
            : 0.0;
        vec4 color = mix(v_color, v_border, inner);
        outc = vec4(color.rgb, color.a * coverage);
    } else {
        // Flat solid fill — hard-edged, coverage 1 (matches the old solid shader).
        outc = v_color;
    }

    if (outc.a <= 0.0) discard;
    fragColor = outc;
}
