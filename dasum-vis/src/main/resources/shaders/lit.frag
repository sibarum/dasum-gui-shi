#version 330 core
in vec3 v_color;
in vec3 v_normal;
uniform float u_opacity;
uniform vec3 u_light;      // world-space direction TO the key light (unit)
uniform float u_ambient;   // ambient floor in [0, 1]
out vec4 fragColor;

void main() {
    // Two-sided Lambert: a surface has no inherent facing, so light whichever
    // side faces the viewer (abs of the dot) — the back of a wave should not
    // read as unlit black. Colormap colour carries height; shade carries form.
    vec3 n = normalize(v_normal);
    float d = abs(dot(n, normalize(u_light)));
    float shade = u_ambient + (1.0 - u_ambient) * d;
    fragColor = vec4(v_color * shade, u_opacity);
}
