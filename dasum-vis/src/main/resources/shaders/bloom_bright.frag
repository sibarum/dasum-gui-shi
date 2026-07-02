#version 330 core
in vec2 v_uv;
uniform sampler2D u_tex;      // HDR scene
uniform float u_threshold;    // luminance where bloom starts
uniform float u_knee;         // soft-knee width
out vec4 fragColor;

// Soft-knee bright-pass (COD-style): fades in around the threshold instead of a hard cut.
void main() {
    vec3 c = texture(u_tex, v_uv).rgb;
    float l = max(c.r, max(c.g, c.b));
    float soft = clamp(l - u_threshold + u_knee, 0.0, 2.0 * u_knee);
    soft = soft * soft / (4.0 * u_knee + 1e-4);
    float contrib = max(soft, l - u_threshold) / max(l, 1e-4);
    fragColor = vec4(c * contrib, 1.0);
}
