#version 330 core
in vec3 v_color;
out vec4 fragColor;

void main() {
    // Round dot — discard fragments outside the inscribed circle of the
    // point quad. gl_PointCoord runs [0,1] across the point sprite.
    vec2 d = gl_PointCoord - vec2(0.5);
    if (dot(d, d) > 0.25) discard;
    fragColor = vec4(v_color, 1.0);
}
