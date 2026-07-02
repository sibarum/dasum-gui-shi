#version 330 core
in vec3 v_world;
uniform vec3 u_center;
uniform vec3 u_half;        // per-axis half-extent of the volume box
uniform vec3 u_eye;
uniform vec3 u_forward;
uniform int u_ortho;
uniform int u_max_steps;
uniform float u_opacity;
uniform sampler3D u_volume; // RGBA: rgb = emitted colour, a = density
out vec4 fragColor;

// Ray/box slab intersection: entry/exit t along the ray for [center-half, center+half].
vec2 boxSpan(vec3 ro, vec3 rd) {
    vec3 lo = u_center - u_half;
    vec3 hi = u_center + u_half;
    vec3 inv = 1.0 / rd;
    vec3 t0 = (lo - ro) * inv;
    vec3 t1 = (hi - ro) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    return vec2(max(max(tmin.x, tmin.y), tmin.z),
                min(min(tmax.x, tmax.y), tmax.z));
}

void main() {
    // The cube is drawn double-sided; march once per pixel (both faces yield the same span).
    if (!gl_FrontFacing) discard;

    vec3 ro, rd;
    if (u_ortho == 1) {
        rd = normalize(u_forward);
        ro = v_world - rd * (4.0 * max(max(u_half.x, u_half.y), u_half.z));
    } else {
        ro = u_eye;
        rd = normalize(v_world - u_eye);
    }

    vec2 span = boxSpan(ro, rd);
    float t0 = max(span.x, 0.0);
    float t1 = span.y;
    if (t1 <= t0) discard;

    int steps = u_max_steps;
    float dt = (t1 - t0) / float(steps);
    vec3 lo = u_center - u_half;
    vec3 extent = 2.0 * u_half;

    // Emissive accumulation: integrate colour*density along the ray (no self-occlusion — matches
    // the additive glow), so the field's structure sums up, tinted by whatever the producer encoded.
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 512; i++) {
        if (i >= steps) break;
        float t = t0 + (float(i) + 0.5) * dt;
        vec3 uvw = (ro + rd * t - lo) / extent;   // world -> [0,1]^3 texture coords
        vec4 texel = texture(u_volume, uvw);
        sum += texel.rgb * texel.a;
    }
    sum *= dt * u_opacity;
    fragColor = vec4(sum, 1.0);
}
