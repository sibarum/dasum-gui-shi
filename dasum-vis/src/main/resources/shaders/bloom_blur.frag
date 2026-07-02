#version 330 core
in vec2 v_uv;
uniform sampler2D u_tex;
uniform vec2 u_dir;   // one texel step along the blur axis (1/w, 0) or (0, 1/h)
out vec4 fragColor;

// Separable 9-tap Gaussian; run once per axis (ping-pong) for a 2D blur.
void main() {
    float w[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    vec3 result = texture(u_tex, v_uv).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        result += texture(u_tex, v_uv + u_dir * float(i)).rgb * w[i];
        result += texture(u_tex, v_uv - u_dir * float(i)).rgb * w[i];
    }
    fragColor = vec4(result, 1.0);
}
