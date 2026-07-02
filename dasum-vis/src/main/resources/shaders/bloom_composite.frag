#version 330 core
in vec2 v_uv;
uniform sampler2D u_scene;   // HDR scene
uniform sampler2D u_bloom;   // blurred bright pass
uniform float u_intensity;   // bloom strength
out vec4 fragColor;

// ACES filmic tone-map (Narkowicz approximation): HDR -> displayable [0,1].
vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 hdr = texture(u_scene, v_uv).rgb;
    vec3 bloom = texture(u_bloom, v_uv).rgb;
    fragColor = vec4(aces(hdr + bloom * u_intensity), 1.0);
}
