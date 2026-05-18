# DasumGUIshi

A small, GPU-accelerated Java GUI framework targeting GraalVM native-image. Pure Java, no reflection, single-executable deployment, zero runtime dependencies on the host system beyond a graphics driver. Designed minimalist-core, take-only-what-you-need; rendering goes through OpenGL via hand-rolled Panama bindings; text via MSDF atlases.

## Status

Pre-1.0. Demo-driven — the `dasum-mvp-demo` module is the canonical "see what works." Public APIs are stable enough to write apps against but small breaking changes happen as the design settles. Single-window only by design (no plans for multi-window).

## Quick start

### Prerequisites

- **JDK 25** (or GraalVM 25). The build enforces this — older JDKs fail with a clear message.
- **Native-access flag at runtime**: `--enable-native-access=ALL-UNNAMED`. The demo's `exec:exec` config sets this automatically; you'll need it in your own apps too.
- **GLFW dynamic library on the runtime path.** Bundled for Windows x64 in `dasum-glfw/src/main/resources/natives/windows-x64/`. macOS / Linux users currently need to place a `glfw3.dylib` / `libglfw3.so` somewhere `NativeLibLoader` can find it (resource path or system `java.library.path`).
- **MSDF atlas tool** (`msdf-atlas-gen.exe`) bundled for Windows. macOS / Linux: activate the `msdf-prebuilt` Maven profile and place atlases at `dasum-mvp-demo/src/main/resources/dasum/atlas/` instead of regenerating.

### Build

```
mvn install
```

### Run the demo (JVM)

```
mvn -pl dasum-mvp-demo exec:exec
```

A 1280×800 window opens with three top-level tabs: **Node Editor**, **Widgets**, **Text**. The Node Editor tab includes a Point Cloud node — click to expand it into a full-screen modal viewport with orbit / zoom / point-pick.

### Run the demo (native-image)

```
mvn -pl dasum-mvp-demo package -P native
./dasum-mvp-demo/target/dasum-mvp-demo
```

Produces a single executable with no JVM dependency.

## Project layout

| Module | Purpose |
|---|---|
| `dasum-natives` | Hand-rolled Panama bindings for GLFW + a minimal OpenGL 3.3 core subset. `NativeLibLoader` extracts native libs from classpath resources at startup. |
| `dasum-glfw` | Holds the GLFW dynamic library (`glfw3.dll` etc.) as a classpath resource. Split out from `dasum-natives` so the bindings can be vendored / replaced without the binary. |
| `dasum-nfd` | Native file-dialog wrapper. Exposes `FileDialog.open` / `save` / `pickFolder` backed by [nativefiledialog-extended](https://github.com/btzy/nativefiledialog-extended); the platform's native picker, not an in-process imitation. |
| `dasum-core` | The framework. All packages under `sibarum.dasum.gui.core`. See below. |
| `dasum-vis` | Optional visualization module — n-dimensional point-cloud viewport with orbit/zoom camera, click-to-pick, thumbnail-or-expanded layout. Uses JOML internally, exposes immutable records. See [`dasum-vis/README.md`](dasum-vis/README.md). |
| `dasum-msdf-maven-plugin` | Build-time MSDF atlas generation. Text atlases from charset presets, icon atlases from named glyph subsets of icon fonts (Lucide / Material) with generated Java constants. See [`dasum-msdf-maven-plugin/README.md`](dasum-msdf-maven-plugin/README.md). |
| `dasum-mvp-demo` | The reference / showcase app. The best place to look when learning the API. |

### `dasum-core` packages

```
sibarum.dasum.gui.core
├── anim/        — Easing, Interpolator, Transition, Animated, AnimationManager
├── command/     — CommandRegistry + EverythingMenu (Ctrl+Space palette)
├── component/   — Component (sealed), Direction, JustifyContent, AlignItems
├── em/          — Em scalar, EmContext (root-em + zoom + DPI), EmRect
├── event/       — Invalidator (dirty-flag dynamic refresh)
├── graph/       — Node-editor primitives: GraphSurface*, Port*, Connection*, NodeBuilder, …
├── input/       — All input dispatchers and per-component state sidecars
├── json/        — Minimal JSON for the MSDF atlas metadata
├── layout/      — Layout (flex), HitTest, LayoutResult, PixelRect, Render
├── overlay/     — OverlayStack + Anchor (modal/popup mechanism)
├── reactive/    — Property<T> (observable values + subscribe)
├── render/      — Batcher, DrawCommand, materials (SolidFill, MsdfText), Color, Vec2
├── text/        — Atlas data, font groups, text metrics, glyph layout, word boundary
├── theme/       — Theme, Variant, Palette, Themed helpers
└── window/      — GlfwContext, Window
```

## Architectural model

### Pure-data components + identity-keyed sidecars

Every visible thing is a `Component` — a Java record. Components are pure data: a `Box` is just `(width, height, padding, color, children, interactive, flexGrow)`. Layout/render/hit-test are pure functions of the component tree.

Everything mutable lives in **identity-keyed sidecars** — process-global `IdentityHashMap<Component, State>` registries:

- `ScrollStates` — per-Scroll scroll position
- `TextStates` — per-Text caret / selection / content / undo stack
- `FocusState` — globally focused component
- `HoverState` — globally hovered component
- `GraphSurfacePositions` — per-(surface,child) em position
- `GraphSurfaceChildren` — per-surface dynamic children
- `GraphSurfaceZOrder` — per-surface z-bump
- `Ports` — per-port type / direction / node
- `Connections` — per-surface connection list
- `ConnectionSelection` — globally selected connection
- `Handlers` — per-component click / focus / blur / context-menu handlers

> **Why identity, not equals.** Records implement `equals` by value, so two `Box(10, 10, 0, RED)` instances are equal but distinct. Framework lookups must use reference identity (`==`) — using `Object.equals` would conflate visually-identical instances and break per-instance state (focus, scroll, drag). The `IdentityHashMap` choice is load-bearing. **If you write framework code that looks up a component, use `==`, never `equals`.**

### Em-first coordinates

All public APIs that take coordinates use **em**, not pixels. `1em` ≈ the root font-size, scaled by zoom and DPI. `EmContext.pixelsPerEm()` is the conversion factor; `Em.toPixels()` converts.

```java
Component.Box(Em.of(2f), Em.of(1.5f), Em.of(0.25f), RED)
GraphSurfacePositions.set(surface, node, 5f, 3f)  // em
ScrollStates.of(panel).scrollBy(0f, 1.5f)         // em
```

Pixel-typed APIs (`scrollByPx`, `pxX()`, etc.) exist as siblings only for framework wiring that already has pixel values (the GLFW callback site, scrollbar drag math, the Layout output). App code should not need them.

> The conversion direction is em→pixels. Avoid going the other way in app code; it usually means you're trying to do layout that the framework should do for you.

### Dynamic refresh

The event loop is `glfwWaitEventsTimeout`-based. Idle = 0% CPU. State changes that affect rendering call `Invalidator.invalidate()` to schedule the next frame. The animation system invalidates per frame while animations are running, then stops.

### Two-pass batched renderer

The `Batcher` has one accumulator per material — currently `SolidFillAccumulator` (rectangles + triangles) and `MsdfTextAccumulator` (signed-distance-field glyphs). `submit(DrawCommand)` dispatches by variant. `endFrame` flushes solid fills first, then glyphs — so text always sits on top of solid backgrounds within a flush.

`OverlayStack` rendering needs explicit `batcher.flush(projection)` between z-layers (main UI → backdrop → each overlay) so a later layer's solid fills aren't drawn under earlier text. See `App.main`'s render closure for the pattern.

### Single-window

`Window.create(width, height, title)` is the only window primitive. Multi-window is deliberately out of scope (GLFW's main-thread requirement on macOS makes it painful).

## Component variants

All under `sibarum.dasum.gui.core.component.Component` (sealed):

- **`Box`** — solid-color rectangle with fixed em size. Container or leaf.
- **`Flex`** — layout container; row or column, justify-content + align-items + gap + per-child flex-grow. `width`/`height` may be `null` (fill parent) or `Em.AUTO` (fit content). The workhorse for almost every UI.
- **`Scroll`** — viewport with overflow scrolling. Mouse-wheel + scrollbar drag.
- **`Text`** — text-rendering primitive. Optional `selectable` / `editable` / multiline / clip / wrap-width / phantom-cursor on hover. The foundation of every label and input.
- **`Checkbox`** — bound to a `Property<Boolean>`.
- **`Radio<T>`** — bound to a shared `Property<T>` (group-exclusive).
- **`Slider`** — horizontal or vertical, bound to a `Property<Float>`.
- **`Tabs`** — tab strip with header cells, content panes, active-index bound to a `Property<Integer>`.
- **`GraphSurface`** — 2D positioning container for the node editor. Children float at em positions from `GraphSurfacePositions`. Distinct from a future drawing-canvas component (which would be called `Canvas`).
- **`PointCloud`** — leaf 3D viewport whose data and camera state live in `PointCloudStates` (in `dasum-vis`). The variant lives in `dasum-core` so layout / hit-test treat it as a first-class component; the renderer is registered via `CustomRenderers` from `dasum-vis` so `dasum-core` stays 2D-only. See [`dasum-vis/README.md`](dasum-vis/README.md).

Variants are immutable; instance methods like `Flex.withFlexGrow(int)` return a new record with the field changed.

## Subsystems

### Input

GLFW callbacks land in `App.wireInput` (or your equivalent), which dispatches through a chain of static controllers — each returns `true` to consume:

```
ScrollbarController → TabsController → ConnectionDragController →
GraphSurfaceController → ConnectionSelectionController →
OverlayStack handling → default hover-based dispatch
```

State sidecars (`HoverState`, `FocusState`, `InputState`) track the current mouse / focus state. `Handlers.onClick(component, runnable)` registers per-component handlers; the dispatcher walks the hit path leaf-to-root, bubbling.

### Theme

`Theme.of(Variant)` returns a `Palette` for one of six built-in variants: `DEFAULT`, `PRIMARY`, `SUCCESS`, `WARNING`, `ERROR`, `INFO`. `Themed.button(...)`, `Themed.checkbox(...)`, `Themed.tabs(...)` are convenience factories that produce variant-styled components. `Theme.override(variant, palette)` lets apps replace any palette.

### Overlay (modal dialogs, popups, command palette)

`OverlayStack.push(new Overlay(component, anchor, modal, onDismiss))` stacks an overlay on top of the main UI. Modal overlays draw a backdrop and route input exclusively. Click-outside-modal pops automatically. `Anchor.CENTER` and `Anchor.at(emX, emY)` are the positioning options; `At` is clamped to the viewport.

### Command palette

`CommandRegistry.register(id, label, runnable)` registers a command. `EverythingMenu` (Ctrl+Space) opens a fuzzy-searchable list. Same flow used by built-in commands ("Zoom In") and app commands ("Add Constant Node").

### Reactive

`Property<T>` is the framework's observable. `.get()`, `.set(value)`, `.subscribe(consumer)`. Used everywhere a UI value needs to be bound: checkbox state, slider value, active tab index, etc.

### Animation

`anim/` provides easing functions, an `Interpolator`, and `Transition` / `Animated` for declarative tweens. `AnimationManager` ticks active animations per frame and stops invalidating when none are running. Coexists with `Property` — animate by changing the property's value over time.

### Em context

`EmContext` is process-global state: `rootEmPx` (default 16), `zoom`, `dpiScale`. `pixelsPerEm() = rootEmPx × zoom × dpiScale`. Set DPI scale at startup from `Window.contentScaleX()`. Ctrl+= / Ctrl+- / Ctrl+0 in the demo bind to `multiplyZoom` / `setZoom(1f)`.

### Point-cloud visualization (`dasum-vis`)

Optional module — depend on `dasum-vis` to render n-dimensional point clouds as 3D scenes inside any GUI panel. Each viewport is a `Component.PointCloud` with snapshot + camera state held in a per-component `AtomicReference`, so worker threads can publish data (typical for ML / training visualizations) without blocking the render thread. Drag-orbit, scroll-zoom, click-pick handlers. Same `Component.PointCloud` instance can be moved between a thumbnail location and a modal overlay via `DynamicChildren` — snapshot, camera, and GPU buffer all follow the component identity. JOML lives entirely inside `dasum-vis`; the public API is immutable records. See [`dasum-vis/README.md`](dasum-vis/README.md).

### Icon fonts

`dasum-msdf-maven-plugin` accepts an `<icons>` block per atlas. Point it at an icon font's TTF + manifest (Lucide `info.json`, Material `codepoints`, etc.), list the icons you actually use by name, and the plugin bakes a focused atlas + generates a Java `Icons` class with one `public static final int` per glyph. The icon atlas registers as a second `FontGroup` (default name `"icons"`) at app startup; `Icon.of(Icons.SEARCH, em, color)` produces a `Component.Text` that renders the glyph. See [`dasum-msdf-maven-plugin/README.md`](dasum-msdf-maven-plugin/README.md) for the manifest formats, name-normalization rules, and the collision policy.

### Context menus

Per-component opt-in via `Handlers.onContextMenu(component, provider)`. Provider returns `List<ContextMenuItem>` at right-click time (so items can reflect current state). Two built-in defaults:

- **`TextContextMenu.defaultProvider()`** — auto-applied to any selectable Text with no explicit provider. Cut / Copy / Paste / Select All.
- **`PortContextMenu.registerDefaults(node)`** — opt-in helper for graph nodes. Adds a "Disconnect" item to every port on the node.

Right-clicks resolve **innermost-out**: deepest hit first, falling back outward through ancestors. The first registered provider wins.

## Node editor

The headline use case the framework's been built around. Live in `graph/` + a few input controllers.

### `GraphSurface`

A 2D positioning container. Children float at em positions stored in `GraphSurfacePositions`. Dragging a child rewrites its position. Z-order via `GraphSurfaceZOrder.bumpToTop` (clicked child moves to front). Dynamic children added at runtime live in `GraphSurfaceChildren`; the framework walks the combined declared + dynamic list for layout / render / hit-test.

### Ports

A "port" is just a regular component (typically a small `Box`) marked via `Ports.declare(node, portComponent, type, direction, name)`. The framework's identity-keyed lookup makes a port discoverable from any handler that has its component reference.

- **`PortType`** — open registry; apps create their own. The framework supplies `PortType.ANY` (wildcard, connects to anything).
- **`PortDirection`** — `INPUT`, `OUTPUT`, `BIDIRECTIONAL`. Bidirectional is for graph-shaped relationships that aren't a data flow.

### Connections

`Connections.add(surface, fromPort, toPort)` registers a connection after passing three rules:

1. Direction (at least one end can output AND at least one can input).
2. Type (`PortTypeCompat.check(out, in)` — defaults to ANY-or-exact-id-match, override globally).
3. Application (`ConnectionRule.check(out, in)` — defaults to always-allow, override globally for arbitrary rules like same-node block, cycle prevention, fan-in limits).

Connections render as cubic Beziers with horizontal handles via `ConnectionRenderer`. Per-vertex color gradient from source port's color to target's.

### Drag-from-port-to-port

`ConnectionDragController` claims left-press on a port, draws an in-flight curve to the cursor, validates the drop target via `Connections.canConnect`, calls `Connections.add` on release. Visual feedback: gradient when over a compatible target, red tip when incompatible, faded when free-floating.

### Selection + deletion

Click a curve → `ConnectionSelectionController` selects it (white halo halo around the stroke). Delete or Backspace removes it. Right-click on a port → "Disconnect" via `PortContextMenu` removes everything incident on that port.

### NodeBuilder

```java
Component multiply = NodeBuilder.titled("Multiply")
    .input(NUMBER, "a")
    .input(NUMBER, "b")
    .output(NUMBER, "result")
    .background(new Color(0.22f, 0.32f, 0.22f, 1f))
    .build();
```

Sugar that produces a fit-to-content node with title at top, inputs on the left, outputs on the right, bidirectional ports along the bottom. For non-standard layouts (circular nodes, ports on top edges, custom port visuals), build the node tree by hand and call `Ports.declare` on each port directly.

## Writing an app

Smallest functional app — a window with a button that prints to stdout:

```java
public final class HelloApp {
    public static void main(String[] args) {
        try (GlfwContext ctx = GlfwContext.init();
             Window window = Window.create(640, 480, "Hello");
             Batcher batcher = new Batcher();
             CursorManager cursors = new CursorManager(window.handle().address())) {

            Gl.load();
            batcher.init();
            cursors.init();
            EmContext.setDpiScale(window.contentScaleX());

            try (Texture atlasTex = Texture.fromPngResource("/dasum/atlas/primary.png")) {
                AtlasData atlas = AtlasData.loadFromResource("/dasum/atlas/primary.json");
                FontGroups.register(FontGroup.of(FontGroups.DEFAULT, atlas, atlasTex));

                Component button = Themed.button("Click me", Em.of(8f), Variant.PRIMARY, 0);
                Handlers.onClick(button, () -> System.out.println("Clicked"));

                Component root = new Component.Flex(
                    null, null, Em.of(1f), new Color(0.1f, 0.1f, 0.12f, 1f),
                    Direction.COLUMN, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
                    List.of(button), false, 0
                );

                // …wire input callbacks (see dasum-mvp-demo/App.wireInput for the pattern)
                // …run the EventLoop
            }
        }
    }
}
```

Look at **`dasum-mvp-demo/src/main/java/sibarum/dasum/gui/demo/App.java`** for the full pattern — toolbar + tabs + node editor + text input + every widget the framework offers.

## Extending

### A custom widget

The component sealed type is `permits`-restricted, so you can't add a new variant from outside `dasum-core`. Compose from `Flex` / `Box` / `Text` / etc. and register click/focus/context handlers via `Handlers` — this gets you 90% of "custom widgets" in practice.

### A custom 3D / GPU component

If you need a new component variant that draws via OpenGL outside the 2D batcher (point clouds, mesh viewers, custom shaders), add the variant to `Component`'s sealed `permits` clause and register a `CustomRenderers.Renderer` for it from a separate module. The renderer receives the component, its layout rect, the batcher, the 2D projection, and the framebuffer dimensions; it's responsible for flushing the batcher, scissoring/viewport-ing to its rect, drawing, and restoring GL state. `dasum-vis`'s `PointCloudRenderer` is the worked example.

### A custom port type

```java
public static final PortType AUDIO = PortType.of("my.audio", "Audio", new Color(0.4f, 0.8f, 0.6f, 1f));
```

That's it. Use it in `NodeBuilder.input(AUDIO, "in")` or `Ports.declare(node, port, AUDIO, PortDirection.INPUT)`. Auto-compatible with itself and with `PortType.ANY`.

### Custom connection rules

```java
// Block cycles in your domain
ConnectionRule.override((out, in) -> !wouldCreateCycle(out, in));

// Type-level subtype relationships
PortTypeCompat.override((outType, inType) -> isAssignable(outType, inType));
```

Process-global. Set once at startup.

### Custom node spawning

Wrap a node-building lambda in a `Supplier<Component>` and bind it to a command:

```java
Supplier<Component> myNode = () -> NodeBuilder.titled("My Node")
    .input(NUMBER, "in")
    .output(NUMBER, "out")
    .build();

CommandRegistry.register("node.add.mynode", "Add My Node",
    () -> spawnOnto(surface, myNode.get(), defaultEmX, defaultEmY));
```

Spawned node appears via `GraphSurfaceChildren.add(surface, node)` plus `GraphSurfacePositions.set(...)`. See `App.spawnNode` for the full helper.

## Conventions to know

- **Identity, never equals, for component lookups.** Records compare by value; framework state is identity-keyed.
- **All public coordinates are em.** Don't introduce pixel-typed parameters on app-facing APIs. Use `Em.toPixels()` only at render/layout boundaries.
- **Context menus are opt-in.** Only selectable/editable Text gets a default. Everything else needs explicit `Handlers.onContextMenu`.
- **Hover indication is automatic for interactive components,** *except* `Tabs` (does its own per-cell hover) and `GraphSurface` (interactive for hit-testing, not a clickable surface).
- **Avoid AWT and ImageIO under native-image.** Use `PngDecoder` (in `render/`) for PNGs; clipboard goes through `Glfw.glfwGetClipboardString` / `glfwSetClipboardString`.
- **Use `Em.AUTO`, not `null`, when you mean "fit to content."** `null` width/height means "fill the parent's available extent." They produce very different layouts.

## Native-image notes

The demo's `native` profile produces a static executable via `org.graalvm.buildtools:native-maven-plugin`. Build args live in `src/main/resources/META-INF/native-image/native-image.properties` (so they're picked up automatically). Reachability metadata for the framework's FFM types is hand-derived; if you see `NoClassDefFoundError` for a Panama-related type in a native build, add it there.

Things to avoid in native-image:
- `java.awt.*`, `javax.imageio.*` (use `PngDecoder` and the MSDF atlas instead)
- Reflection-based serialization (use the framework's `json/Json` for simple cases)
- Dynamic classloading

## License

See `LICENSE.txt`.
