#version 330 core
in vec3 v_color;
uniform float u_opacity;
// 0 = hard round dot (scatter); > 0 = Gaussian falloff — a soft blob that melts into a
// continuous volume under additive blending (volumetrics). The value scales the falloff width.
uniform float u_softness;
out vec4 fragColor;

void main() {
    // SDF-style round-dot mask: discard fragments outside the inscribed circle.
    vec2 d = gl_PointCoord - vec2(0.5);
    float r2 = dot(d, d);
    if (r2 > 0.25) discard;
    float a = u_opacity;
    if (u_softness > 0.0) {
        // Gaussian on the normalized radius (0 at centre → 1 at the dot edge): bright core,
        // fading smoothly to ~0 at the rim so overlapping blobs sum into a continuous glow.
        float rn2 = r2 * 4.0;               // (2r)^2, in [0, 1]
        a *= exp(-4.5 * rn2 / u_softness);
    }
    fragColor = vec4(v_color, a);
}
