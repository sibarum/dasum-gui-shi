# dasum-vis

Optional visualization module — n-dimensional point-cloud viewports rendered through OpenGL inside the same `Component` tree as the rest of the GUI. Built for ML / training visualizations where a worker thread publishes data continuously and the UI just keeps up.

## Why it's its own module

`dasum-core` is 2D-only and intentionally has no 3D math, no JOML dependency, no depth-test code. Putting point-cloud rendering in `dasum-core` would bloat the classpath of every consumer regardless of whether they need it. So this module is opt-in: depend on `dasum-vis` to get point clouds, leave it out otherwise.

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

// register the icons FontGroup or text FontGroup as usual; no extra setup
// for point clouds beyond DasumVis.init().

Component.SceneView viewport = new Component.SceneView(
    Em.of(12f), Em.of(8f), Em.ZERO, backgroundColor, true, 1);

// publish data — safe to call from any thread, including a training worker
float[] positions = ... ; // N*3 row-major xyz
PointCloudStates.publish(viewport, new PointCloudSnapshot(
    3, positions.length / 3, positions, null, null, null));

PointCloudStates.setCamera(viewport, CameraSpec.defaultPerspective());

// optional: react to clicks on individual points
PointHandlers.onPointClick(viewport, hit ->
    System.out.println("Picked " + hit.pointIndex()));
```

Drop the `viewport` anywhere in your component tree (inside a `Flex`, a `GraphSurface` node, an `OverlayStack` modal, …). Drag-orbits the camera (perspective) or pans the target (orthographic); scroll-wheel zooms; click-without-drag picks the nearest point inside an 8-pixel tolerance.

## Threading model

This is the load-bearing design constraint. The Ternion Studio use case has a training worker that updates `NodeRuntime` snapshots at thousands of Hz and the UI has to display them without blocking. The framework already had this problem solved at the invalidation layer — `Invalidator.invalidate()` is lock-free and coalesces cross-thread wake events. This module extends that pattern to bulk per-component state.

```
┌─────────── Worker thread ────────────┐    ┌──────── Main / render thread ────────┐
│                                      │    │                                       │
│  PointCloudStates.publish(component, │    │  PointCloudRenderer.render(...)       │
│     newSnapshot)                     │    │     - reads snapshotRef.get()         │
│     ↓                                │    │     - if reference unchanged: skip    │
│   snapshotRef.set(snapshot)  ◄──────────────────                                  │
│     ↓                                │    │     - otherwise upload to VBO         │
│   Invalidator.invalidate()           │    │     - draw GL_POINTS                  │
│     ↓                                │    │                                       │
│   (coalesced glfwPostEmptyEvent)     │    │                                       │
└──────────────────────────────────────┘    └───────────────────────────────────────┘
```

Properties this gives:

- Worker never blocks. `AtomicReference.set` + `Invalidator.invalidate()` are both lock-free.
- Render thread never blocks. One `AtomicReference.get()` per visible point cloud per frame.
- No callbacks cross the boundary. The publish path doesn't fire any user code.
- Worker can publish 10,000 snapshots between two frames — the renderer uploads exactly one, the latest at the moment the frame starts.
- Re-upload only when the snapshot reference identity changes (it's compared with `==`). High publish rates that happen to set the same reference twice cost nothing.

**Snapshot ownership contract**: after a `PointCloudSnapshot` is passed to `publish`, its backing `positions` / `colors` / `labels` arrays MUST NOT be mutated by the publisher. The renderer reads them lock-free on a different thread. The simplest way to honour this is to allocate fresh arrays per publish.

## JOML lives inside this module only

JOML's types (`Matrix4f`, `Vector3f`) are mutable for performance — exactly what a per-frame render loop wants. But mutable shared state across threads is a footgun. So JOML is contained to `dasum-vis` internals:

- The public API uses immutable records: `Vec3`, `Vec4`, `CameraSpec`, `PointCloudSnapshot`.
- The renderer (`PointCloudRenderer`) holds JOML scratch buffers as instance fields, used only on the GLFW main thread.
- The picker (`PointPicker`) allocates JOML matrices per pick (rare event), all on the main thread.
- No `Matrix4f` reference ever escapes `dasum-vis`.

If you find yourself wanting a JOML helper in app code, add a method to `Vec3`/`CameraSpec` that takes/returns records and does the JOML work internally.

## Public API

| Type | Purpose |
|---|---|
| `Vec3`, `Vec4` | Immutable float vectors. |
| `CameraMode` | `ORTHOGRAPHIC` or `PERSPECTIVE`. |
| `CameraSpec` | Immutable camera state: mode, target, distance, yaw, pitch, ortho-scale, FOV, near/far. With-style setters return new instances. `defaultOrtho()` / `defaultPerspective()` factories. |
| `PointCloudSnapshot` | Immutable frame of point data: dimensionality, count, positions (row-major `float[]`), optional per-point colors / labels, optional projection (which dims map to x/y/[z]). Validating canonical constructor enforces invariants. |
| `PointCloudStates` | Identity-keyed registry. `publish(c, snapshot)`, `setCamera(c, spec)`, `snapshotOf(c)`, `cameraOf(c)`, `clear(c)`. All thread-safe. |
| `PointHandlers` | Per-component click handlers. `onPointClick(c, Consumer<PointHit>)`. `PointHit` is `(int pointIndex, Vec3 worldPosition)`. |
| `SceneViewController` | Mouse-input dispatcher. Mirrors the `SliderController` static-API pattern; the host app calls `onMouseDown` / `onCursorMove` / `onMouseUp` / `onScroll` from its GLFW callbacks. |
| `Icon` | (in `dasum-core`) `Icon.of(int codepoint, Em size, Color color)` — companion helper for icon fonts; see `dasum-msdf-maven-plugin` README. |
| `DasumVis` | Module bootstrap. `DasumVis.init()` once at startup. |

## Component integration

`Component.SceneView` is a sealed-permitted variant in `dasum-core`. Its record carries only layout + appearance — the actual point data, camera, and GPU buffers live in side registries.

The renderer is wired in via `CustomRenderers`:

```java
// inside DasumVis.init():
CustomRenderers.register(Component.SceneView.class, renderer::asRenderer);
```

`Render.renderInOrder` dispatches `Component.SceneView` to whatever renderer is registered for its class (no-op if none — components placed before `DasumVis.init()` runs render as flat boxes, harmless). This keeps `dasum-core` 2D-only and free of OpenGL 3D state knowledge; `dasum-vis` is the one who knows about depth tests and MVP matrices.

`DasumVis.init()` also registers a `Components.registerCleaner` that releases `PointCloudStates`, `PointHandlers`, and the per-component GL buffer when a component is detached.

## Rendering details

- **Points as `GL_POINTS`** with `gl_PointSize` set from a uniform. Fragment shader discards corners with an SDF-style round-dot mask. Avoids needing instanced quads (`glDrawArraysInstanced` + `glVertexAttribDivisor`) which aren't in `Gl.java`.
- **Per-component VBO cache** (`PointCloudGlBuffers`). Skip the upload when the snapshot reference hasn't changed since last frame — a high-frequency worker that re-publishes the same data costs allocation only.
- **`glViewport` per rect**, then restored to the framebuffer. Without this, NDC `[-1, 1]` maps to the full framebuffer; a small-rect viewport (a thumbnail in a corner) would have all its points scissored away. This bit specifically: was a bug fixed during the node-thumbnail work — keep the restore call in if you modify the renderer.
- **Depth test** enabled only in perspective mode. The depth buffer is cleared scissored to the viewport rect, so multiple point clouds on screen don't fight each other.
- **Scissor** to the viewport rect (via `Batcher.scissor()`). Pushes and pops match the `ScrollContents` rendering pattern.

## Input handling

The `SceneViewController` mirrors `SliderController`'s static-API pattern. The host app (e.g. `dasum-mvp-demo/App.wireInput`) calls into it from GLFW callbacks. Returns `true` from `onMouseDown` / `onScroll` to consume the event.

**Click vs drag**: A 4-pixel squared threshold distinguishes the two. While the cursor stays within that bubble of the press position, the camera doesn't move and a release resolves as a click → `PointPicker.pickNearest` finds the nearest point and fires the registered `PointHandlers` handler. Cross the threshold and we commit to a drag; the eventual release does not fire a click.

`isDragging()` returns `true` for the entire duration of a press, regardless of whether the threshold has been crossed — the host's mouse-up dispatcher uses it to suppress generic click activation so a press-and-release on a viewport is routed through `PointHandlers` instead of `Handlers.activate`.

**Picking** is CPU-side: project every point through the same MVP the renderer uses, filter behind-camera (post-projection w ≤ 0) and out-of-frustum, measure screen-space distance to the cursor, take the nearest within the 8-pixel tolerance. O(N) per click — microseconds for thousands of points.

## Thumbnail-and-overlay pattern

The framework's layout pass maps each `Component` to exactly one rect per frame, so a single `Component.SceneView` instance can't appear simultaneously in the main tree and an overlay (the overlay rect overwrites the thumbnail rect). Two clean integrations:

**Option A — same instance, swap location** (the demo's pattern). Thumbnail slot is a `Flex` with empty declared children; the viewport is added via `DynamicChildren`. To expand: remove the viewport from the slot, drop a placeholder in, push an overlay whose `DynamicChildren` carries the viewport, register the overlay's `onDismiss` to swap back. Snapshot, camera, click handler, GPU buffer all follow the component identity. Free, no framework changes.

**Option B — two instances sharing a source** (not implemented, ~30 LOC refactor). Add a `PointCloudSource` opaque key; `PointCloudStates.publishToSource(src, snap)` plus `PointCloudStates.bind(viewport, src)`. Then multiple `Component.SceneView` instances render the same snapshot simultaneously while keeping per-component camera state. Right for thumbnail-next-to-detailed-inspector layouts where both need to stay visible.

Option A covers "expand to popup" cleanly. Reach for Option B only when both viewports must coexist.

## Aspirational / phase 2

Things explicitly out of scope for the initial implementation, designed-not-to-block but not yet wired:

- **Hover tooltips** showing per-point labels (`snapshot.labels`).
- **Matrix-shaped point positions** + cell-grid dim selector UI (current scope is vector positions, optional `projection` field picks dims for x/y/z).
- **PCA / t-SNE auto-projection** for n > 3 dims when no explicit `projection` is supplied.
- **Trail / trajectory rendering** — multiple snapshots overlaid with fading alpha.
- **Render-limit decimation** for very large clouds (current cap is whatever the GPU can chew through; ~10⁵ points is fine on consumer hardware).
- **Frustum culling** via JOML's `frustumPlanes`.
- **Variable point size per point** — currently one uniform pixel size for the whole cloud.
- **Gradient-magnitude coloring** — automatic mapping from a per-point scalar to colors via a colormap, for Ternion's `runningMaxGrad` use case.

## Files of interest

```
dasum-vis/
├── math/
│   ├── Vec3.java, Vec4.java       — immutable public math types
│   ├── CameraMode.java
│   └── CameraSpec.java
├── pointcloud/
│   ├── PointCloudSnapshot.java    — immutable data
│   ├── PointCloudStates.java      — AtomicReference registry
│   ├── SceneViewController.java  — mouse input
│   ├── PointHandlers.java         — click callbacks
│   └── PointPicker.java           — screen-space picking
├── render/
│   ├── PointCloudRenderer.java    — the CustomRenderers entry point
│   ├── PointCloudMaterial.java    — GL program + uniforms
│   └── PointCloudGlBuffers.java   — per-component VBO cache
└── DasumVis.java                  — module bootstrap
```
