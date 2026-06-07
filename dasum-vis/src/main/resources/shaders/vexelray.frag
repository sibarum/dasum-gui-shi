#version 330 core
// VexelRay R1 -- fixed-function sphere tracer. One built-in SDF menu
// selected by uniform; all parameters are uniforms, so the program never
// recompiles. R2 replaces sdf() with generated code from a Field tree.
in vec3 v_world;

uniform mat4 u_mvp;
uniform vec3 u_center;
uniform float u_scale;
uniform vec3 u_eye;
uniform vec3 u_forward;
uniform vec3 u_lightDir;    // unit, toward the light; camera-anchored by the renderer
uniform int u_ortho;        // 1 = parallel rays along u_forward
uniform int u_fieldType;    // 0 SPHERE, 1 BOX, 2 TORUS, 3 BLOBS, 4 MANDELBULB
uniform vec4 u_params;
uniform vec4 u_color;       // base colour, alpha = color.a * layer opacity
uniform int u_maxSteps;

out vec4 fragColor;

const float EPS = 1e-3;

// ---- built-in fields (field-local coordinates: cube center at origin) ----

float sdSphere(vec3 p) { return length(p) - u_params.x; }

float sdBox(vec3 p) {
    vec3 q = abs(p) - u_params.xyz;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdTorus(vec3 p) {
    vec2 q = vec2(length(p.xz) - u_params.x, p.y);
    return length(q) - u_params.y;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sdBlobs(vec3 p) {
    float s = u_params.x;   // spread
    float k = u_params.y;   // smooth-union blend radius
    float r = 0.45;
    float d = length(p - vec3( s, 0.0, 0.0)) - r;
    d = smin(d, length(p - vec3(-s * 0.5,  s * 0.85, 0.0)) - r, k);
    d = smin(d, length(p - vec3(-s * 0.5, -s * 0.85, 0.0)) - r, k);
    return d;
}

float sdMandelbulb(vec3 p) {
    float power = u_params.x;
    int iters = int(u_params.y);
    vec3 z = p;
    float dr = 1.0;
    float r = 0.0;
    for (int i = 0; i < 16; i++) {
        if (i >= iters) break;
        r = length(z);
        if (r > 2.0) break;
        // Triplex power: convert to spherical, raise to N, convert back.
        float theta = acos(clamp(z.z / max(r, 1e-9), -1.0, 1.0));
        float phi = atan(z.y, z.x);
        dr = pow(r, power - 1.0) * power * dr + 1.0;
        float zr = pow(r, power);
        theta *= power;
        phi *= power;
        z = zr * vec3(sin(theta) * cos(phi), sin(theta) * sin(phi), cos(theta)) + p;
    }
    return 0.5 * log(max(r, 1e-9)) * r / dr;
}

float sdf(vec3 worldP) {
    vec3 p = worldP - u_center;
    if (u_fieldType == 0) return sdSphere(p);
    if (u_fieldType == 1) return sdBox(p);
    if (u_fieldType == 2) return sdTorus(p);
    if (u_fieldType == 3) return sdBlobs(p);
    return sdMandelbulb(p);
}

vec3 normalAt(vec3 p) {
    // Tetrahedron-offset gradient -- 4 field taps. The offset is scale-
    // relative and LARGER than the hit tolerance: sampling below the
    // march's convergence radius reads field noise and shows up as
    // shading banding. Fractal distance estimates are noisy at fine
    // scales, so the bulb gets a 3x wider tap -- a low-pass filter on the
    // normal that trades sub-pixel surface detail for smooth lobes
    // (specular otherwise turns every DE jitter into glitter).
    float h = (u_fieldType == 4 ? 6e-3 : 2e-3) * u_scale;
    // The four tetrahedron vertices MUST sum to zero — (1,-1,-1),
    // (-1,-1,1), (-1,1,-1), (1,1,1) — or the estimator gains a constant
    // directional bias proportional to the residual f(p) at the shading
    // point. Exact-SDF primitives converge to f(p)~0 and hide such a
    // bias; fractal DEs leave a residual and shade one object-space side
    // brighter than the other.
    const vec2 k = vec2(1.0, -1.0);
    return normalize(
        k.xyy * sdf(p + k.xyy * h) +
        k.yyx * sdf(p + k.yyx * h) +
        k.yxy * sdf(p + k.yxy * h) +
        k.xxx * sdf(p + k.xxx * h));
}

// Fractal distance estimates UNDERREPORT free space in the halo just
// outside the surface (dr grows fast, so the DE is far smaller than the
// true distance even where nothing is nearby). Occlusion queries that
// read the raw DE there see phantom occluders and blanket-darken convex
// lobes -- inverting the perceived lighting (convex darker than concave).
// Compensate the DE for *occlusion reads only*; the primary march keeps
// the conservative value, where pessimism equals correctness.
float deComp() { return u_fieldType == 4 ? 2.3 : 1.0; }

// Ambient occlusion: probe the field along the normal; concave regions
// return less free distance than the probe height. Per-probe normalized
// (0..1 each) so the result is scale-independent; smooth by construction.
float occlusion(vec3 p, vec3 n) {
    float comp = deComp();
    float base = 0.03 * u_scale;
    float occ = 0.0;
    float sca = 1.0;
    float wsum = 0.0;
    for (int i = 1; i <= 5; i++) {
        float hh = base * float(i);
        float d = sdf(p + n * hh) * comp;
        occ += clamp((hh - d) / hh, 0.0, 1.0) * sca;
        wsum += sca;
        sca *= 0.7;
    }
    return clamp(1.0 - 0.9 * occ / wsum, 0.05, 1.0);
}

// Soft shadow: march from the surface toward the light; the closer the
// ray grazes other geometry, the darker the penumbra. Crevices and
// self-occlusion darken from actual blocked light, not just curvature.
float softShadow(vec3 p, vec3 n) {
    float comp = deComp();
    float res = 1.0;
    vec3 ro2 = p + n * (0.012 * u_scale);
    float t2 = 0.025 * u_scale;
    for (int i = 0; i < 32; i++) {
        float d = sdf(ro2 + u_lightDir * t2) * comp;
        if (d < 1e-4 * u_scale) return 0.0;
        res = min(res, 12.0 * d / t2);
        t2 += clamp(d, 0.01 * u_scale, 0.25 * u_scale);
        if (t2 > 3.0 * u_scale) break;
    }
    return clamp(res, 0.0, 1.0);
}

// Ray / AABB slab intersection against the bounding cube.
// Returns (tEnter, tExit); miss when tEnter > tExit.
vec2 cubeSpan(vec3 ro, vec3 rd) {
    vec3 lo = u_center - vec3(u_scale);
    vec3 hi = u_center + vec3(u_scale);
    vec3 inv = 1.0 / rd;
    vec3 t0 = (lo - ro) * inv;
    vec3 t1 = (hi - ro) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    return vec2(max(max(tmin.x, tmin.y), tmin.z),
                min(min(tmax.x, tmax.y), tmax.z));
}

void main() {
    vec3 ro;
    vec3 rd;
    if (u_ortho == 1) {
        rd = normalize(u_forward);
        // Start well behind the fragment so the span clamp handles entry.
        ro = v_world - rd * (4.0 * u_scale);
    } else {
        ro = u_eye;
        rd = normalize(v_world - u_eye);
    }

    vec2 span = cubeSpan(ro, rd);
    if (span.x > span.y) discard;
    float t = max(span.x, 0.0);
    float tEnd = span.y;

    // Fractal distance estimates (the bulb) are noisy upper bounds --
    // full-length steps overshoot the surface and produce crumbling /
    // ring artifacts. Under-step fractals and tighten their hit
    // tolerance; analytic primitives keep the exact march.
    bool fractal = u_fieldType == 4;
    float relax = fractal ? 0.6 : 1.0;
    float epsScale = fractal ? 0.35 : 1.0;

    bool hit = false;
    vec3 p = ro;
    for (int i = 0; i < 512; i++) {
        if (i >= u_maxSteps || t > tEnd) break;
        p = ro + rd * t;
        float d = sdf(p);
        if (d < EPS * epsScale * max(t, 1.0)) { hit = true; break; }
        t += d * relax;
    }
    if (!hit) discard;

    // Refine the hit: a few extra relaxation steps converge p onto the
    // surface well past the march tolerance, so the normal/AO taps read
    // a stable point.
    for (int i = 0; i < 3; i++) {
        p += rd * sdf(p);
    }

    // Blinn-Phong with field-derived occlusion: colored ambient +
    // diffuse, white specular highlight, soft shadows toward the key
    // light, AO in the crevices, and a weak cool fill from the opposite
    // side so fully shadowed faces don't go flat black.
    vec3 n = normalAt(p);
    vec3 viewDir = -rd;
    vec3 halfVec = normalize(u_lightDir + viewDir);
    float diff = max(dot(n, u_lightDir), 0.0);
    float fill = max(dot(n, -u_lightDir), 0.0) * 0.10;
    // Specular MUST be gated to the lit hemisphere: the half-vector can
    // align with a back-facing normal at grazing view angles, putting
    // phantom glints on the dark side -- which reads as inverted lighting.
    float spec = pow(max(dot(n, halfVec), 0.0), 48.0) * 0.85
               * smoothstep(0.0, 0.12, diff);
    float ao = occlusion(p, n);
    // Floored so fully shadowed lit-side faces never drop below the
    // ambient/fill of away-facing ones -- that ordering inversion is what
    // reads as "broken lighting".
    float shadow = 0.2 + 0.8 * softShadow(p, n);

    vec3 col = u_color.rgb * ((0.30 + 1.05 * diff * shadow) * ao + fill * ao)
             + vec3(spec * shadow) * ao;
    fragColor = vec4(col, u_color.a);

    // Real depth for the hit point so vector layers compose against the
    // field surface (perspective scenes; ortho scenes have no depth test).
    vec4 clip = u_mvp * vec4(p, 1.0);
    float ndcZ = clip.z / clip.w;
    gl_FragDepth = clamp(ndcZ * 0.5 + 0.5, 0.0, 1.0);
}
