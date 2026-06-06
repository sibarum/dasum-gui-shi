#version 330 core
in vec3 v_color;
uniform float u_opacity;
out vec4 fragColor;

void main() {
    // SDF-style round-dot mask: discard fragments outside the inscribed circle.
    vec2 d = gl_PointCoord - vec2(0.5);
    if (dot(d, d) > 0.25) discard;
    fragColor = vec4(v_color, u_opacity);
}
