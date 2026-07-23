#version 330 core
in vec2 v_uv;
uniform sampler2D u_atlas;
uniform float u_distanceRange;
uniform vec4 u_color;
uniform vec4 u_outlineColor;   // rgb + alpha (alpha already premultiplied by layer opacity)
uniform float u_outlineWidth;  // outline half-width in screen pixels (0 = none)
out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float screenPxRange() {
    vec2 unitRange = vec2(u_distanceRange) / vec2(textureSize(u_atlas, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(v_uv);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    vec3 msd = texture(u_atlas, v_uv).rgb;
    float sd = median(msd.r, msd.g, msd.b);
    float screenPxDistance = screenPxRange() * (sd - 0.5);
    float fill = clamp(screenPxDistance + 0.5, 0.0, 1.0);   // glyph interior coverage
    if (u_outlineWidth <= 0.0) {
        // No outline: the plain-glyph path, unchanged.
        fragColor = vec4(u_color.rgb, u_color.a * fill);
        return;
    }
    // Outline: a second, wider coverage grown outward by the outline width. The silhouette is the
    // outer coverage; colour is the fill inside the glyph, the outline colour in the surrounding
    // band. Both edges are the MSDF's screen-space AA falloff, so the corona is crisp at any zoom.
    float silhouette = clamp(screenPxDistance + 0.5 + u_outlineWidth, 0.0, 1.0);
    vec3 rgb = mix(u_outlineColor.rgb, u_color.rgb, fill);
    float a  = silhouette * mix(u_outlineColor.a, u_color.a, fill);
    fragColor = vec4(rgb, a);
}
