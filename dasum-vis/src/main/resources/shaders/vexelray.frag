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
uniform int u_fieldType;    // 0 SPHERE, 1 BOX, 2 TORUS, 3 BLOBS, 4 MANDELBULB, 5 CSG_BOXES
uniform vec4 u_params;
uniform vec4 u_color;       // base colour, alpha = color.a * layer opacity
uniform int u_maxSteps;
// CSG_BOXES program: 2 vec4s per op — (center.xyz, opcode), (halfExtents.xyz, smoothK).
uniform vec4 u_csg[96];
uniform int u_csgCount;     // ops (not vec4s)

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

float sdBoxAt(vec3 p, vec3 c, vec3 he) {
    vec3 q = abs(p - c) - he;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float smax(float a, float b, float k) {
    return -smin(-a, -b, k);
}

// Sequential left-fold over the box op-list: d = op(d, box_i).
// Opcodes: 0 union, 1 subtract, 2 intersect, 3/4/5 smooth variants.
// Global rounding (u_params.x) at the end fillets every edge at once.
float sdCsgBoxes(vec3 p) {
    float d = 1e9;
    for (int i = 0; i < 48; i++) {
        if (i >= u_csgCount) break;
        vec4 a = u_csg[i * 2];
        vec4 b = u_csg[i * 2 + 1];
        float di = sdBoxAt(p, a.xyz, b.xyz);
        int op = int(a.w + 0.5);
        if      (op == 0) d = min(d, di);
        else if (op == 1) d = max(d, -di);
        else if (op == 2) d = max(d, di);
        else if (op == 3) d = smin(d, di, b.w);
        else if (op == 4) d = smax(d, -di, b.w);
        else              d = smax(d, di, b.w);
    }
    return d - u_params.x;
}

float sdCapsuleY(vec3 p, float l, float r) {
    p.y -= clamp(p.y, 0.0, l);
    return length(p) - r;
}

// Orbit trap for the plant: normalized branch depth (0 = rootstock,
// 1 = outermost tips) of whichever generation owns the surface point.
// Written by sdAlienPlant; main() snapshots it at the hit point before
// the normal/AO/shadow taps clobber it.
float g_plantTrap = 0.0;

// Folded-IFS flora. Per generation: draw one tapered stem capsule
// (which, thanks to the mirror fold, stands in for 2^i branches), climb
// to its top, fold space so the two branch directions coincide, tilt
// outward, spin by the GOLDEN ANGLE (phyllotaxis -- the spiral
// arrangement real plants use), and shrink the domain. A glowing pod
// caps the final generation. Folds and uniform scales preserve distance
// bounds, so the march stays honest; the gentle height-proportional
// sway is the only Lipschitz cheat (covered by march relaxation).
// params: x = branch tilt (rad), y = shrink per generation,
//         z = generations, w = sway.
float sdAlienPlant(vec3 p) {
    p.y += 0.95;                       // root at the cube floor
    float tiltC = cos(u_params.x), tiltS = sin(u_params.x);
    const float GC = -0.737369;        // cos/sin of the golden angle 2.39996 rad
    const float GS =  0.675490;
    float shrink = u_params.y;
    int gens = int(u_params.z);
    float sway = u_params.w;

    float scale = 1.0;
    float d = 1e9;
    float seg = 0.55;
    g_plantTrap = 0.0;

    for (int i = 0; i < 12; i++) {
        if (i >= gens) break;
        // Organic lean, proportional to height within the generation.
        float sw = sway * p.y;
        float swc = cos(sw), sws = sin(sw);
        p.xy = mat2(swc, -sws, sws, swc) * p.xy;

        // Stem for this generation, tapered with depth.
        float stem = sdCapsuleY(p, seg, 0.085) * scale;
        if (stem < d) g_plantTrap = float(i);
        d = smin(d, stem, 0.05 * scale);

        // Climb, fold, tilt, spin, shrink.
        p.y -= seg;
        p.x = abs(p.x);                              // mirror fold: 2 branches per gen
        p.xy = mat2(tiltC, tiltS, -tiltS, tiltC) * p.xy;   // tilt child axis outward
        p.xz = mat2(GC, -GS, GS, GC) * p.xz;         // golden-angle spin
        p /= shrink;
        scale *= shrink;
    }
    // Pod at every outermost tip.
    float pod = (length(p - vec3(0.0, 0.12, 0.0)) - 0.20) * scale;
    if (pod < d) g_plantTrap = float(gens);
    d = smin(d, pod, 0.06 * scale);
    g_plantTrap /= max(float(gens), 1.0);
    return d;
}

float sdf(vec3 worldP) {
    vec3 p = worldP - u_center;
    if (u_fieldType == 0) return sdSphere(p);
    if (u_fieldType == 1) return sdBox(p);
    if (u_fieldType == 2) return sdTorus(p);
    if (u_fieldType == 3) return sdBlobs(p);
    if (u_fieldType == 4) return sdMandelbulb(p);
    if (u_fieldType == 5) return sdCsgBoxes(p);
    return sdAlienPlant(p);
}

vec3 normalAt(vec3 p) {
    // Tetrahedron-offset gradient -- 4 field taps. The offset is scale-
    // relative and LARGER than the hit tolerance: sampling below the
    // march's convergence radius reads field noise and shows up as
    // shading banding. Fractal distance estimates are noisy at fine
    // scales, so the bulb gets a 3x wider tap -- a low-pass filter on the
    // normal that trades sub-pixel surface detail for smooth lobes
    // (specular otherwise turns every DE jitter into glitter).
    // Wider taps for fractals (DE noise) AND booleans: hard min()/max()
    // keeps concave creases sharp -- the normal flips across one pixel,
    // and the AO probe direction flips with it (corner bisector = max
    // free space), painting bright/dark piping along junctions. Wider
    // taps blend the flip across a band.
    float h = (u_fieldType >= 4 ? 6e-3 : 2e-3) * u_scale;
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

// Some fields UNDERREPORT free space, and occlusion reads of the raw
// value see phantom occluders:
//  - Fractal DEs (bulb): dr grows fast, so the estimate is far smaller
//    than true distance everywhere in the halo -- blanket-darkens convex
//    lobes (factor ~2.3 empirical).
//  - Boolean max()/smin() fields (CSG): distance is to the nearest FACE
//    PLANE, not the surface, so probes along a crease's bisector normal
//    read ~cos(45 deg) of the truth -- a dark seam along every rounded
//    edge, convex ones included (1/cos(45) ~= 1.45).
// Compensate for *occlusion reads only*; the primary march keeps the
// conservative value, where pessimism equals correctness.
float deComp() {
    // Booleans get NO compensation: their bisector deficit shows up
    // exactly at concave creases, where compensating BRIGHTENS the
    // crease relative to its flanks (the piping artifact). Sharp creases
    // are instead handled by wider normal taps + the shape language's
    // smooth joins.
    if (u_fieldType == 4) return 2.3;
    return 1.0;
}

// Normal-march AO (iq-style): probe the field along the normal; nearby
// geometry along that axis returns less free distance than the probe
// height. Good for smooth, mostly-convex surfaces (bulb, blobs, plant).
// Blind to concave creases — the normal there points the most-OPEN way,
// so it can't see the converging walls (that's what hemisphereAO fixes).
float normalMarchAO(vec3 p, vec3 n) {
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

// Hemisphere AO with a direction-relative baseline — the correct answer
// for hard concave creases. Each tap compares the field at p + dir*r to
// the TANGENT-PLANE prediction r*dot(dir,n):
//   flat surface   -> sdf == prediction      -> 0  (bright)
//   convex edge    -> sdf  > prediction      -> clamped to 0 (bright)
//   concave crease -> walls intrude, sdf < prediction -> positive (dark)
// Because the converging walls are closest at the crease CENTRE, that's
// where the most tap directions are occluded — so the centre is the
// darkest point, not the brightest. The direction-relative baseline is
// the whole trick: it cancels surface curvature except where geometry
// actually closes in.
float hemisphereAO(vec3 p, vec3 n) {
    float comp = deComp();
    vec3 t = normalize(abs(n.y) < 0.99 ? cross(n, vec3(0.0, 1.0, 0.0))
                                       : cross(n, vec3(1.0, 0.0, 0.0)));
    vec3 b = cross(n, t);
    float r = 0.07 * u_scale;
    const float CT = 0.573; // cos(55°) — ring tilt off the normal
    const float ST = 0.819; // sin(55°)
    float occ = 0.0;
    for (int i = 0; i < 6; i++) {
        float a = 6.2831853 * float(i) / 6.0;
        vec3 dir = n * CT + (t * cos(a) + b * sin(a)) * ST;
        float e = r * CT;                       // flat-plane expectation for this dir
        occ += clamp((e - sdf(p + dir * r) * comp) / r, 0.0, 1.0);
    }
    // Plus the normal axis (convex-bump / contact occlusion).
    occ += clamp((r - sdf(p + n * r) * comp) / r, 0.0, 1.0);
    return clamp(1.0 - 1.4 * occ / 7.0, 0.05, 1.0);
}

float occlusion(vec3 p, vec3 n) {
    // Boolean shapes have hard concave creases the normal-march can't read
    // (it inverts them to bright); hemisphere sampling darkens the crease
    // centre correctly. Smooth fields keep the cheaper, already-tuned march.
    return u_fieldType == 5 ? hemisphereAO(p, n) : normalMarchAO(p, n);
}

// Soft shadow: march from the surface toward the light; the closer the
// ray grazes other geometry, the darker the penumbra. Crevices and
// self-occlusion darken from actual blocked light, not just curvature.
float softShadow(vec3 p, vec3 n) {
    float comp = deComp();
    float res = 1.0;
    // Start clear of the local surface. At concave junctions the ray
    // origin sits very close to the NEIGHBORING wall; a hard zero-return
    // there pixel-quantizes the penumbra into bright/dark piping along
    // the crease. So: bigger standoff, and NO binary cutoff -- the
    // penumbra term goes negative continuously and clamps, keeping the
    // contact shadow but smoothing its edge.
    vec3 ro2 = p + n * (0.02 * u_scale);
    float t2 = 0.04 * u_scale;
    for (int i = 0; i < 32; i++) {
        float d = sdf(ro2 + u_lightDir * t2) * comp;
        res = min(res, 12.0 * d / t2);
        if (res < 0.0) break; // fully dark; clamp below
        t2 += clamp(abs(d), 0.015 * u_scale, 0.25 * u_scale);
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
    // The plant's sway warp mildly inflates its Lipschitz bound -- a
    // small under-step keeps the march safe.
    float relax = fractal ? 0.6 : (u_fieldType == 6 ? 0.85 : 1.0);
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
    // Snapshot the plant's orbit trap at the refined hit -- the
    // normal/AO/shadow evaluations below all clobber the global.
    float plantTrap = g_plantTrap;

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

    vec3 albedo = u_color.rgb;
    if (u_fieldType == 6) {
        // Alien flora coloring from one base color: outer generations
        // hue-rotate toward the channel-cycled complement.
        vec3 tip = u_color.rgb.brg * 1.35 + vec3(0.06);
        albedo = mix(u_color.rgb, tip, smoothstep(0.15, 0.95, plantTrap));
    }

    vec3 col = albedo * ((0.30 + 1.05 * diff * shadow) * ao + fill * ao)
             + vec3(spec * shadow) * ao;

    if (u_fieldType == 6) {
        // Bioluminescence: emissive tip glow added AFTER lighting, so
        // pods shine from inside their own shadow.
        col += albedo * (pow(plantTrap, 3.0) * 0.85);
    }
    fragColor = vec4(col, u_color.a);

    // Real depth for the hit point so vector layers compose against the
    // field surface (perspective scenes; ortho scenes have no depth test).
    vec4 clip = u_mvp * vec4(p, 1.0);
    float ndcZ = clip.z / clip.w;
    gl_FragDepth = clamp(ndcZ * 0.5 + 0.5, 0.0, 1.0);
}
