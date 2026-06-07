#version 330 core
in vec2 v_uv;
uniform sampler2D u_texture;
uniform float u_opacity;
out vec4 fragColor;

void main() {
    vec4 texel = texture(u_texture, v_uv);
    fragColor = vec4(texel.rgb, texel.a * u_opacity);
}
