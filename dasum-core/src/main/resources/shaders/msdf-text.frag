#version 330 core
in vec2 v_uv;
in vec4 v_color;
uniform sampler2D u_atlas;
uniform float u_distanceRange;
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
    float opacity = clamp(screenPxDistance + 0.5, 0.0, 1.0);
    fragColor = vec4(v_color.rgb, v_color.a * opacity);
}
