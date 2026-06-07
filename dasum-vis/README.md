# dasum-vis

Optional visualization module — 3D scene viewports rendered through OpenGL inside the same `Component` tree as the rest of the GUI. A scene is a list of blend-mode **layers** (points, lines, triangles, images, in-scene text) plus **VexelRay** raymarched signed-distance-field shapes. Built for ML / training visualizations where a worker thread publishes data continuously and the UI just keeps up.

## Why it's its own module

`dasum-core` is 2D-only and intentionally has no 3D math, no JOML dependency, no depth-test code. Putting scene rendering in `dasum-core` would bloat the classpath of every consumer regardless of whether they need it. So this module is opt-in:

```xml
<dependency>
    <groupId>sibarum.dasum.gui</groupId>
    <artifactId>dasum-vis</artifactId>
</dependency>
```

The `Component.SceneView` variant lives in `dasum-core` (so layout and hit-test treat it as a first-class component) but its renderer is registered from `dasum-vis` via the `CustomRenderers` extension point — `dasum-core` never imports anything from `dasum-vis`.

## Quick start

```java
// once, after Gl.load():
DasumVis.init();

Component.SceneView viewport = new Component.SceneView(
    Em.of(12f), Em.of(8f), Em.ZERO, backgroundColor, true, 1);

// A scene is an ordered list of layers, drawn in painter's order.
float[] xyz = ...;   // N*3 row-major positions
float[] rgb = ...;   // N*3 per-point colour, or null for the default
SceneStates.publish(viewport, SceneSnapshot.of(
    new PointLayer(xyz, rgb),                       // scatter
    new LineLayer(axisEndpoints, axisColors)        // axes / wireframe on top
));
SceneStates.setCamera(viewport, CameraSpec.defaultPerspective());

// optional: react to clicks on individual points
PointHandlers.onPointClick(viewport, hit ->
    System.out.println("Picked layer " + hit.layerIndex() + " point " + hit.pointIndex()));
```

Drop the `viewport` anywhere in your component tree (inside a `Flex`, a `GraphSurface` node, an `OverlayStack` modal, …). Drag orbits the camera (or pans in 2D mode); scroll-wheel zooms (cursor-anchored in 2D mode); click-without-drag picks the nearest point inside an 8-pixel tolerance.

## Layers

A `SceneSnapshot` holds a `List<Layer>` drawn in order — later layers blend over earlier ones (painter's model). `Layer` is sealed:

| Layer | Geometry | Notes |
|---|---|---|
| `PointLayer` | `float[]` xyz + optional per-point RGB + optional per-point size | round screen-space dots; `defaultSizePx` for points with no explicit size |
| `LineLayer` | endpoint pairs + optional per-endpoint RGB | 1px segments, per-endpoint colour gradient — axes, wireframes, edges |
| `TriangleLayer` | vertex triples + optional per-vertex RGB | the universal fill — bars, heatmap cells, pie wedges, thick polylines |
| `ImageLayer` | `byte[]` RGBA + world-space quad corners + `smooth` flag | textured quad; same-dimension republish streams via `glTexSubImage2D` (fractals, heatmaps, volume slices) |
| `TextLayer` | string + world anchor + em height + align + `billboard` | MSDF glyphs in world space; billboards orient in the vertex shader (no re-upload on camera move) |
| `VexelRayLayer` | a built-in `Field` + params (+ a `CsgBox` op list) | a raymarched signed-distance field — see below |

Every layer carries a `BlendMode` (`ALPHA`, `ADDITIVE`, `SCREEN`, `MULTIPLY`, `MAX`, `MIN`, `OPAQUE`) and a scalar `opacity`. Commutative modes (`ADDITIVE`/`MAX`/`MIN`) are order-independent — the right choice for translucent 3D, where unsorted `ALPHA` pops as the camera orbits. `MAX` over a colormapped point/voxel layer is maximum-intensity projection. Layers have the standard ownership contract: **don't mutate a published layer's backing arrays** — and GPU upload is skipped per layer whose reference is unchanged between snapshots, so reuse untouched layer instances and replace only what changed.

## VexelRay — raymarched fields

A `VexelRayLayer` sphere-traces a signed-distance field inside a bounding cube and shades the hit surface (Blinn-Phong + soft shadows + ambient occlusion, camera-anchored key light). The hit writes real `gl_FragDepth`, so the other (uploaded-geometry) layers depth-compose correctly against the computed surface — axis lines pierce a Mandelbulb, text occludes behind it.

This is the fixed-function tier: a built-in `Field` menu, all parameters as uniforms, so **the shader never recompiles** when a shape changes — animate any parameter through the transition system at zero cost.

```java
SceneStates.publish(viewport, SceneSnapshot.of(
    VexelRayLayer.of(VexelRayLayer.Field.MANDELBULB).withMaxSteps(128),
    VexelRayLayer.csgBoxes(List.of(                       // boolean box program
        new CsgBox(CsgBox.Op.UNION,         center, halfExtents),
        new CsgBox(CsgBox.Op.SMOOTH_UNION,  c2, he2, /*k*/0.1f),
        new CsgBox(CsgBox.Op.SUBTRACT,      c3, he3)
    ), /*globalRounding*/ 0.04f).withCenter(new Vec3(2f, 0f, 0f))
));
```

`Field` values: `SPHERE`, `BOX`, `TORUS`, `BLOBS` (smooth-union), `MANDELBULB`, `CSG_BOXES` (a `CsgBox` op-list program — union / subtract / intersect, hard or smooth, plus global edge rounding), `ALIEN_PLANT` (folded-IFS flora with phyllotaxis spin and bioluminescent tips). `maxSteps` is the quality/cost knob. Fractal fields carry per-field tuning (step relaxation, distance-estimate compensation) internal to the shader.

## Threading model

The load-bearing design constraint: a worker updates snapshots at thousands of Hz and the UI must display them without blocking. Built on the framework's lock-free `Invalidator.invalidate()` (coalesces cross-thread wake events).

```
┌─────────── Worker thread ────────────┐    ┌──────── Main / render thread ────────┐
│                                      │    │                                       │
│  SceneStates.publish(component,      │    │  SceneRenderer.render(...)            │
│     newScene)                        │    │     - reads sceneRef.get()            │
│     ↓                                │    │     - per layer: if reference         │
│   sceneRef.set(scene)  ◄────────────────────  unchanged, skip its upload          │
│     ↓                                │    │     - else upload to that layer's VBO │
│   Invalidator.invalidate()           │    │     - draw all layers in order        │
│     ↓                                │    │                                       │
│   (coalesced glfwPostEmptyEvent)     │    │                                       │
└──────────────────────────────────────┘    └───────────────────────────────────────┘
```

- Worker never blocks (`AtomicReference.set` + `Invalidator.invalidate()` are lock-free).
- Render thread never blocks (one `AtomicReference.get()` per visible viewport per frame).
- No callbacks cross the boundary — the publish path fires no user code.
- A worker can publish thousands of snapshots between two frames; the renderer draws exactly one, the latest at frame start. Re-upload happens **per layer**, only when that layer's reference identity changed — republishing a scene where one layer moved costs one layer's upload.

**Ownership contract**: after a layer is published, its backing arrays MUST NOT be mutated by the publisher; the renderer reads them lock-free on the GLFW main thread. Allocate fresh arrays per publish (or reuse the same immutable layer instance).

## Camera + interaction

- `CameraSpec` — immutable camera (mode, target, distance, yaw, pitch, ortho-scale, FOV, near/far); `with*` setters return new instances; `defaultOrtho()` / `defaultPerspective()`.
- `CameraMath` — the single source of MVP composition (`mvp`), billboard basis (`viewBasis`), and ray origin/direction (`eye`, `forward`), shared by the renderer and the picker so screen mapping never drifts.
- `CameraRig` — `fitToBounds(spec, min, max)` frames a bounding box; `front`/`top`/`side`/`iso` presets. Pairs with the transition system for animated camera moves.
- `InteractionSpec` (per component, via `SceneStates.setInteraction`) — `ORBIT_3D` (default), `PAN_ZOOM_2D` (drag-pan + cursor-anchored zoom, for 2D/chart/fractal views), or `LOCKED` (camera frozen, click-pick still works), with zoom clamps + pitch clamp.
- `SceneStates.onCameraChange(c, listener)` — fires on every camera change (user navigation or programmatic), so view-dependent content (fractal recompute, chart tick re-derivation) can rebuild.

## Legacy point-cloud API (compat)

The original n-dimensional point-cloud API still works as a thin layer over the scene model:

```java
PointCloudStates.publish(viewport, new PointCloudSnapshot(
    dim, count, positions, colors, labels, projection));   // n-D + projection dims
PointCloudStates.setCamera(viewport, CameraSpec.defaultPerspective());
```

`PointCloudStates` converts the snapshot to a `SceneSnapshot` (one point layer + one line layer, projecting n-D positions to 3D via `SceneCompat`), caches the conversion by snapshot reference identity, and forwards to `SceneStates` — so the renderer's per-layer identity-skip still works. `snapshotOf` still returns the original `PointCloudSnapshot`. New code should build `SceneSnapshot`s and use `SceneStates` directly.

## JOML lives inside this module only

JOML's types (`Matrix4f`, `Vector3f`) are mutable for performance — exactly what a per-frame render loop wants, but a footgun across threads. So JOML is contained:

- The public API uses immutable records: `Vec3`, `Vec4`, `CameraSpec`, the layer records, `SceneSnapshot`.
- `CameraMath` returns `float[16]` / `float[]`, never a JOML reference.
- The renderer and picker allocate JOML scratch only on the GLFW main thread.

## Component integration

`Component.SceneView` carries only layout + appearance; scene, camera, and interaction live in `SceneStates`, GPU buffers in the renderer's cache. `DasumVis.init()` registers the renderer and a `Components.registerCleaner` that releases `SceneStates`, the compat cache, `PointHandlers`, and the per-component GL buffers on detach.

The renderer wraps its GL work in a `ViewportScope` (try-with-resources: flush the 2D batcher, push scissor, retarget `glViewport`, depth-clear, and restore everything on close). A debug `GlStateGuard` (`-Ddasum.debug.gl=true`, in `dasum-core`) flags any GL state a custom renderer leaks.

## Rendering details

- **Per-layer materials**: `PointMaterial` (point sprites with per-vertex size + round-dot discard), `FlatMaterial` (lines + triangles), `ImageMaterial` (textured quad), `SceneTextMaterial` (world-space MSDF), `VexelRayMaterial` (the sphere tracer). One program each, bound per layer.
- **Per-(component, layer) GPU cache** (`SceneGlBuffers`) with per-kind slot pooling — a worker streaming fresh layers every frame reuses VAOs/VBOs (and image textures via `glTexSubImage2D`) with no GL object churn.
- **`glViewport` per rect** then restored, so a small-rect viewport (a thumbnail in a corner) maps NDC to its rect, not the whole framebuffer.
- **Depth test** in perspective mode; the depth buffer is cleared scissored to the rect so multiple viewports don't fight. Translucent layers test depth but don't write it.

## Picking

`PointPicker` projects every `PointLayer` point through `CameraMath.mvp` (the same matrix the renderer uses), filters behind-camera and out-of-frustum, and takes the screen-nearest within tolerance. O(total points) per click — microseconds for thousands. `PointHit` is `(layerIndex, pointIndex, worldPosition)`. Triangle/field picking is future work; the SDF makes field picking a cursor-ray march (the renderer already does it per fragment).

## Thumbnail-and-overlay pattern

The layout pass maps each `Component` to one rect per frame, so a single `SceneView` can't appear in both the main tree and an overlay at once. The demo's pattern: keep the viewport in a `DynamicChildren` slot; to expand, move it into a modal overlay and drop a placeholder in the slot; the overlay's `onDismiss` swaps it back. Scene, camera, and GPU buffers all follow the component identity — free, no framework changes.

## Aspirational / later phases

- **VexelRay R2** — a composable `Field` tree (CSG, domain ops, `Mix` morphing, `Iterate`) compiled to GLSL and cached by structure hash; the `CSG_BOXES` op-list and `ALIEN_PLANT` are the fixed-function preview.
- **Texture-backed fields** — cubemap heightfields, layered interval shells, voxel grids (3D textures) for arbitrary-topology / dense-tensor manifolds.
- **Decal cache** — forward-splat cached surface points (position + gradient surfels) and march only disocclusions, for a large interaction speedup on heavy fields.
- **Chart / fractal / tensor wrappers** — app-facing facades built on the layer model.
- Hover tooltips from point labels; PCA / t-SNE auto-projection for n > 3 dims; trail / trajectory rendering; render-limit decimation.

## Files of interest

```
dasum-vis/
├── math/      — Vec3, Vec4, CameraMode, CameraSpec, CameraMath, CameraRig
├── scene/     — BlendMode, Layer (sealed) + Point/Line/Triangle/Image/Text/VexelRay layers,
│               CsgBox, SceneSnapshot, SceneStates, SceneCompat, InteractionSpec
├── pointcloud/— PointCloudSnapshot, PointCloudStates (compat), SceneViewController,
│               PointHandlers, PointPicker
├── render/    — SceneRenderer, SceneGlBuffers, ViewportScope, and the per-layer materials
└── DasumVis.java — module bootstrap
```
