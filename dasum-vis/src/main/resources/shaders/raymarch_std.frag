#version 330 core
// Standard raymarch harness for RaymarchLayer (RaymarchShader.standard).
// Everything except the field is here; the caller's float map(vec3 p)
// (local coordinates, box center at origin) is spliced in at the marker.
// A fully custom shader can replace this whole file — the only hard
// contract with the renderer is: read `in vec3 v_world`, read whichever
// system uniforms you need, write gl_FragDepth on a hit, discard on a miss.
in vec3 v_world;

// ---- system uniforms (supplied by the renderer every frame) ----
uniform mat4  u_mvp;
uniform vec3  u_center;
uniform vec3  u_halfExtent;
uniform vec3  u_eye;
uniform vec3  u_forward;     // view direction, for orthographic rays
uniform int   u_ortho;      // 1 = parallel rays along u_forward
uniform vec3  u_lightDir;   // unit, toward the light; camera-anchored
uniform int   u_viewMode;   // 0 LIT, 1 NORMALS, 2 AO, 3 STEPS

// ---- shape uniforms (supplied via RaymarchLayer.uniforms) ----
uniform vec4  u_color;       // base colour, alpha = colour.a * layer opacity
uniform int   u_maxSteps;    // march iteration cap
uniform float u_relax;       // step multiplier; < 1 for loose (non-exact) bounds
uniform float u_epsScale;    // hit-tolerance multiplier
uniform float u_normalEps;   // normal tap, as a fraction of the box half-extent

out vec4 fragColor;

const float EPS = 1e-3;

// Characteristic world size of the shape — scales AO/shadow/normal probes.
float wscale() { return max(max(u_halfExtent.x, u_halfExtent.y), u_halfExtent.z); }

// ---- caller-supplied field: float map(vec3 p) in local coordinates ----
//@SDF@

float sdf(vec3 worldP) { return map(worldP - u_center); }

// Tetrahedron-offset gradient (4 taps). Vertices sum to zero so an exact
// SDF gives an unbiased normal; the tap is larger than the hit tolerance
// to avoid reading field noise as shading banding.
vec3 normalAt(vec3 p) {
    float h = u_normalEps * wscale();
    const vec2 k = vec2(1.0, -1.0);
    return normalize(
        k.xyy * sdf(p + k.xyy * h) +
        k.yyx * sdf(p + k.yyx * h) +
        k.yxy * sdf(p + k.yxy * h) +
        k.xxx * sdf(p + k.xxx * h));
}

// Normal-march AO: probe along the normal; nearby geometry returns less
// free distance than the probe height.
float ao(vec3 p, vec3 n) {
    float base = 0.03 * wscale();
    float occ = 0.0, sca = 1.0, wsum = 0.0;
    for (int i = 1; i <= 5; i++) {
        float hh = base * float(i);
        float d = sdf(p + n * hh);
        occ += clamp((hh - d) / hh, 0.0, 1.0) * sca;
        wsum += sca;
        sca *= 0.7;
    }
    return clamp(1.0 - 0.9 * occ / wsum, 0.05, 1.0);
}

// Soft shadow toward the key light; continuous penumbra, no binary cutoff.
float softShadow(vec3 p, vec3 n) {
    float ws = wscale();
    float res = 1.0;
    vec3 ro = p + n * (0.02 * ws);
    float t = 0.04 * ws;
    for (int i = 0; i < 32; i++) {
        float d = sdf(ro + u_lightDir * t);
        res = min(res, 12.0 * d / t);
        if (res < 0.0) break;
        t += clamp(abs(d), 0.015 * ws, 0.25 * ws);
        if (t > 3.0 * ws) break;
    }
    return clamp(res, 0.0, 1.0);
}

vec3 heat(float x) {
    x = clamp(x, 0.0, 1.0);
    return clamp(vec3(x * 2.0, 1.0 - abs(x - 0.5) * 2.0, 1.0 - x * 2.0), 0.0, 1.0);
}

// Ray / AABB slab intersection against the bounding box.
vec2 boxSpan(vec3 ro, vec3 rd) {
    vec3 lo = u_center - u_halfExtent;
    vec3 hi = u_center + u_halfExtent;
    vec3 inv = 1.0 / rd;
    vec3 t0 = (lo - ro) * inv;
    vec3 t1 = (hi - ro) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    return vec2(max(max(tmin.x, tmin.y), tmin.z),
                min(min(tmax.x, tmax.y), tmax.z));
}

void main() {
    float ws = wscale();
    vec3 ro, rd;
    if (u_ortho == 1) {
        rd = normalize(u_forward);
        ro = v_world - rd * (4.0 * ws);
    } else {
        ro = u_eye;
        rd = normalize(v_world - u_eye);
    }

    vec2 span = boxSpan(ro, rd);
    if (span.x > span.y) discard;
    float t = max(span.x, 0.0);
    float tEnd = span.y;

    bool hit = false;
    vec3 p = ro;
    int steps = 0;
    for (int i = 0; i < 512; i++) {
        if (i >= u_maxSteps || t > tEnd) break;
        steps = i + 1;
        p = ro + rd * t;
        float d = sdf(p);
        if (d < EPS * u_epsScale * max(t, 1.0)) { hit = true; break; }
        t += d * u_relax;
    }
    if (!hit) discard;

    // Refine onto the surface for stable shading taps.
    for (int i = 0; i < 3; i++) p += rd * sdf(p);

    vec3 n = normalAt(p);
    vec3 viewDir = -rd;
    vec3 halfVec = normalize(u_lightDir + viewDir);
    float diff = max(dot(n, u_lightDir), 0.0);
    float fill = max(dot(n, -u_lightDir), 0.0) * 0.10;
    // Specular gated to the lit hemisphere so grazing back-faces don't glint.
    float spec = pow(max(dot(n, halfVec), 0.0), 48.0) * 0.85 * smoothstep(0.0, 0.12, diff);
    float occ = ao(p, n);
    float shadow = 0.2 + 0.8 * softShadow(p, n);

    vec3 albedo = u_color.rgb;
    vec3 col = albedo * ((0.30 + 1.05 * diff * shadow) * occ + fill * occ)
             + vec3(spec * shadow) * occ;

    if (u_viewMode == 1)      col = n * 0.5 + 0.5;
    else if (u_viewMode == 2) col = vec3(occ);
    else if (u_viewMode == 3) col = heat(float(steps) / float(u_maxSteps));

    fragColor = vec4(col, u_color.a);

    vec4 clip = u_mvp * vec4(p, 1.0);
    gl_FragDepth = clamp((clip.z / clip.w) * 0.5 + 0.5, 0.0, 1.0);
}
