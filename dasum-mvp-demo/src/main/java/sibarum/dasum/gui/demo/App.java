package sibarum.dasum.gui.demo;

import sibarum.dasum.gui.core.GlfwContext;
import sibarum.dasum.gui.core.command.CommandRegistry;
import sibarum.dasum.gui.core.command.EverythingMenu;
import sibarum.dasum.gui.core.dialog.FileDialog;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.EventLoop;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.graph.Connection;
import sibarum.dasum.gui.core.graph.ConnectionRule;
import sibarum.dasum.gui.core.graph.Connections;
import sibarum.dasum.gui.core.graph.GraphSurfacePositions;
import sibarum.dasum.gui.core.graph.NodeBuilder;
import sibarum.dasum.gui.core.graph.PortType;
import sibarum.dasum.gui.core.graph.Ports;
import sibarum.dasum.gui.core.graph.ConnectionSelection;
import sibarum.dasum.gui.core.graph.GraphSurfaceChildren;
import sibarum.dasum.gui.core.input.ConnectionDragController;
import sibarum.dasum.gui.core.input.ConnectionSelectionController;
import sibarum.dasum.gui.core.input.GraphSurfaceController;
import sibarum.dasum.gui.core.input.PortContextMenu;
import sibarum.dasum.gui.core.input.ContextMenuController;
import sibarum.dasum.gui.core.input.ContextMenuItem;
import sibarum.dasum.gui.core.input.CursorManager;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.input.HoverState;
import sibarum.dasum.gui.core.input.InputState;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.input.ScrollbarController;
import sibarum.dasum.gui.core.input.SliderController;
import sibarum.dasum.gui.core.input.TabsController;
import sibarum.dasum.gui.core.input.TextInputController;
import sibarum.dasum.gui.core.input.TextState;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.Layout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.layout.Render;
import sibarum.dasum.gui.core.overlay.Anchor;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.RenderStats;
import sibarum.dasum.gui.core.render.Texture;
import sibarum.dasum.gui.core.text.AtlasData;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.theme.Themed;
import sibarum.dasum.gui.core.theme.Variant;
import sibarum.dasum.gui.core.window.Window;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.natives.glfw.Glfw;
import sibarum.dasum.gui.natives.glfw.GlfwCallbacks;
import sibarum.dasum.gui.vis.DasumVis;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.pointcloud.PointCloudController;
import sibarum.dasum.gui.vis.pointcloud.PointCloudSnapshot;
import sibarum.dasum.gui.vis.pointcloud.PointCloudStates;

import java.util.List;

import static sibarum.dasum.gui.natives.gl.Gl.GL_COLOR_BUFFER_BIT;

/**
 * M9 deliverable: M8 layout plus a selectable Text paragraph in the content
 * area. Click/drag to select, arrow keys to move the caret, Shift extends
 * selection, Home/End jump to line bounds. Hovering selectable text shows
 * a dim phantom caret at the would-be insertion point. Cursor shape
 * changes to I-beam over selectable text.
 */
public final class App {

    private static final Color TOOLBAR_BG    = new Color(0.13f, 0.14f, 0.18f, 1f);
    private static final Color SIDEBAR_BG    = new Color(0.10f, 0.12f, 0.15f, 1f);
    private static final Color CONTENT_BG    = new Color(0.07f, 0.09f, 0.13f, 1f);
    private static final Color FRAME_BG      = new Color(0.05f, 0.06f, 0.09f, 1f);

    private static final Color BTN_RED     = new Color(0.85f, 0.35f, 0.35f, 1f);
    private static final Color BTN_ORANGE  = new Color(0.85f, 0.55f, 0.25f, 1f);
    private static final Color BTN_GREEN   = new Color(0.35f, 0.75f, 0.45f, 1f);
    private static final Color BTN_BLUE    = new Color(0.30f, 0.55f, 0.85f, 1f);
    private static final Color BTN_PURPLE  = new Color(0.55f, 0.40f, 0.80f, 1f);
    private static final Color BTN_NEUTRAL = new Color(0.40f, 0.42f, 0.48f, 1f);
    private static final Color LABEL_FG    = new Color(1f, 1f, 1f, 0.95f);
    private static final Color BODY_TEXT   = new Color(0.92f, 0.94f, 0.97f, 1.00f);
    private static final Color CB_BOX      = new Color(0.18f, 0.20f, 0.25f, 1f);
    private static final Color CB_CHECK    = new Color(0.30f, 0.80f, 0.50f, 1f);
    private static final Color RB_BOX      = new Color(0.18f, 0.20f, 0.25f, 1f);
    private static final Color RB_DOT      = new Color(0.60f, 0.65f, 0.95f, 1f);
    private static final Color SL_TRACK    = new Color(0.18f, 0.20f, 0.25f, 1f);
    private static final Color SL_FILL     = new Color(0.30f, 0.55f, 0.85f, 1f);
    private static final Color SL_THUMB    = new Color(0.90f, 0.92f, 0.97f, 1f);

    private static final float WHEEL_PIXELS_PER_STEP = 40f;

    // ---------- node-editor demo: port types + node factories ----------
    // Hoisted to class scope so they're shared between buildNodeEditorPane()
    // (initial setup) and the CommandRegistry / right-click "Add … Here"
    // actions that spawn nodes at runtime.
    private static final PortType NUMBER = PortType.of("number", "Number", new Color(0.85f, 0.55f, 0.25f, 1f));
    private static final PortType STRING = PortType.of("string", "String", new Color(0.30f, 0.55f, 0.85f, 1f));

    private static final java.util.function.Supplier<Component> CONSTANT_FACTORY = () -> NodeBuilder.titled("Constant")
        .output(NUMBER, "value")
        .background(new Color(0.20f, 0.32f, 0.42f, 1f))
        .build();
    private static final java.util.function.Supplier<Component> MULTIPLY_FACTORY = () -> NodeBuilder.titled("Multiply")
        .input(NUMBER, "a")
        .input(NUMBER, "b")
        .output(NUMBER, "result")
        .background(new Color(0.22f, 0.32f, 0.22f, 1f))
        .build();
    private static final java.util.function.Supplier<Component> TAG_FACTORY = () -> NodeBuilder.titled("Tag")
        .bidirectional(STRING, "label")
        .bidirectional(PortType.ANY, "ref")
        .background(new Color(0.32f, 0.22f, 0.32f, 1f))
        .build();

    /** Set by {@link #buildNodeEditorPane()} so command-registry actions can spawn onto it. */
    private static Component.GraphSurface DEMO_SURFACE = null;

    /** Press target for click dispatch; cleared on release. */
    private static Component pressTarget = null;

    public static void main(String[] args) {
        try (GlfwContext ctx = GlfwContext.init();
             Window window = Window.create(1280, 800, "DasumGUIshi — Demo");
             Batcher batcher = new Batcher();
             CursorManager cursors = new CursorManager(window.handle().address())) {

            Gl.load();
            batcher.init();
            cursors.init();
            DasumVis.init();
            EmContext.setDpiScale(window.contentScaleX());

            try (Texture primaryTexture = Texture.fromPngResource("/dasum/atlas/primary.png")) {
                AtlasData primaryAtlas = AtlasData.loadFromResource("/dasum/atlas/primary.json");
                FontGroups.register(FontGroup.of(FontGroups.DEFAULT, primaryAtlas, primaryTexture));

                Component root = buildUi();
                wireInput(window, cursors);
                registerCommands(window);

                RenderStats stats = new RenderStats();
                System.out.println("Demo: top-level tabs — Node Editor / Widgets / Text. Ctrl+Space opens the command palette; Ctrl+=/- zoom.");

                EventLoop loop = new EventLoop(window, () -> {
                    int fbW = window.framebufferWidth();
                    int fbH = window.framebufferHeight();
                    float[] projection = sibarum.dasum.gui.core.render.Projection.orthoTopLeft(fbW, fbH);

                    Gl.glViewport(0, 0, fbW, fbH);
                    Gl.glClearColor(0.03f, 0.03f, 0.05f, 1f);
                    Gl.glClear(GL_COLOR_BUFFER_BIT);

                    PixelRect viewport = new PixelRect(0f, 0f, fbW, fbH);
                    LayoutResult mainLayout = Layout.compute(root, viewport);
                    // Merge any active overlay layouts into a combined map so
                    // hit-testers + Render share a single LayoutResult.
                    java.util.Map<Component, PixelRect> mergedRects = new java.util.IdentityHashMap<>(mainLayout.rects());
                    OverlayStack.layoutInto(mergedRects, viewport);
                    LayoutResult layout = new LayoutResult(mergedRects);
                    LatestLayout.store(root, layout);

                    batcher.beginFrame(fbH);
                    Render.render(root, layout, batcher, projection);
                    // The batcher's per-material two-pass flush (solid fills,
                    // then MSDF text) means within a single batched frame, text
                    // from anywhere drawn on top of solid fills from anywhere.
                    // To make overlays correctly cover the main UI, each
                    // z-layer needs its own complete flush so its text doesn't
                    // race ahead of a later layer's background. Flush after the
                    // main UI, after the backdrop, and after each overlay.
                    if (OverlayStack.isActive()) {
                        batcher.flush(projection);
                        if (OverlayStack.anyModal()) {
                            batcher.submit(new sibarum.dasum.gui.core.render.DrawCommand.ColoredQuad(
                                viewport.x(), viewport.y(), viewport.width(), viewport.height(),
                                sibarum.dasum.gui.core.theme.Theme.overlayBackdrop()
                            ));
                            batcher.flush(projection);
                        }
                        for (OverlayStack.Overlay o : OverlayStack.active()) {
                            Render.render(o.component(), layout, batcher, projection);
                            batcher.flush(projection);
                        }
                    }
                    batcher.endFrame(projection);

                    stats.recordFrame(batcher.drawCallsThisFrame(), batcher.verticesThisFrame());
                });
                loop.run();

                System.out.println("Exited cleanly; total frames rendered: " + loop.renderedFrameCount()
                    + "; idle ticks: " + loop.idleFrameCount());
            }
        }
    }

    private static Component button(String label, Em width, Color bg, int flexGrow) {
        Component.Text labelComp = new Component.Text(label, Em.of(0.85f), LABEL_FG);
        return new Component.Flex(
            width, Em.of(2f), Em.of(0.3f), bg,
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(labelComp),
            true, 0
        ).withFlexGrow(flexGrow);
    }

    private static Component buildUi() {
        return new Component.Flex(
            null, null, Em.of(0.5f), FRAME_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.5f),
            List.of(buildToolbar(), buildMainTabs()),
            false, 0
        );
    }

    private static Component buildToolbar() {
        Component file    = button("File",    Em.of(4.5f), BTN_RED,    0);
        Component edit    = button("Edit",    Em.of(4.5f), BTN_ORANGE, 0);
        Component view    = button("View",    Em.of(4.5f), BTN_GREEN,  0);
        Component help    = button("Help",    Em.of(4.5f), BTN_BLUE,   0);
        Component account = button("Account", Em.of(0f),   BTN_PURPLE, 1);

        Handlers.onClick(file,    () -> CommandRegistry.invoke("file.open"));
        Handlers.onClick(edit,    () -> System.out.println("Edit clicked"));
        Handlers.onClick(view,    () -> System.out.println("View clicked"));
        Handlers.onClick(help,    () -> openHelpDialog());
        Handlers.onClick(account, () -> System.out.println("Account clicked"));

        return new Component.Flex(
            null, Em.of(3f), Em.of(0.5f), TOOLBAR_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(file, edit, view, help, account),
            false, 0
        );
    }

    /** Top-level Tabs widget — primary navigation. Fills the space below the toolbar. */
    private static Component buildMainTabs() {
        Property<Integer> activeTab = new Property<>(0);
        activeTab.subscribe(i -> System.out.println("Active tab: " + i));

        return Themed.tabs(
            null, null,
            Em.of(2.6f), Em.of(1.0f), Em.of(0.5f),
            Em.of(1.1f),
            List.of(
                new Component.Tabs.TabPanel("Node Editor", buildNodeEditorPane()),
                new Component.Tabs.TabPanel("Widgets",     buildWidgetsPane()),
                new Component.Tabs.TabPanel("Text",        buildTextPane()),
                new Component.Tabs.TabPanel("Point Cloud", buildPointCloudPane())
            ),
            activeTab,
            Variant.PRIMARY
        ).withFlexGrow(1);
    }

    /** Node-editor pane — full-pane GraphSurface with typed-port demo nodes. */
    private static Component buildNodeEditorPane() {
        // Block same-node connections — typical node-editor UX. The hook
        // sees the full Ports.Port records so apps can encode any algorithmic
        // rule (cycle prevention, fan-in limits, mode-gated ports, etc.).
        ConnectionRule.override((out, in) -> out.node() != in.node());

        Color surfaceBg = new Color(0.05f, 0.07f, 0.10f, 1f);

        Component constantNode = CONSTANT_FACTORY.get();
        Component multiplyNode = MULTIPLY_FACTORY.get();
        Component tagNode      = TAG_FACTORY.get();

        // null width/height = fill the tab pane (Layout.childExtentOrFill
        // for GraphSurface returns max(explicit, fillExtent)). interactive=true
        // so right-clicks on empty surface area land on the surface itself.
        Component.GraphSurface surface = new Component.GraphSurface(
            null, null, surfaceBg,
            List.of(constantNode, multiplyNode, tagNode), true, 0
        );
        DEMO_SURFACE = surface;
        GraphSurfacePositions.set(surface, constantNode,  2f, 2f);
        GraphSurfacePositions.set(surface, multiplyNode, 18f, 2f);
        GraphSurfacePositions.set(surface, tagNode,      36f, 2f);

        // Hardcoded data-only connection: Constant.value → Multiply.a.
        Connections.add(surface,
            Ports.byName(constantNode, "value").component(),
            Ports.byName(multiplyNode, "a").component());

        wireNodeContextMenu(constantNode);
        wireNodeContextMenu(multiplyNode);
        wireNodeContextMenu(tagNode);
        PortContextMenu.registerDefaults(constantNode);
        PortContextMenu.registerDefaults(multiplyNode);
        PortContextMenu.registerDefaults(tagNode);

        // Surface menu — empty-area right-click. "Add … Here" items spawn
        // at the cursor's surface-local em coords.
        Handlers.onContextMenu(surface, event -> {
            float emX = event.localEmX(surface);
            float emY = event.localEmY(surface);
            return List.of(
                new ContextMenuItem("Add Constant Here", () -> spawnNode(CONSTANT_FACTORY, emX, emY)),
                new ContextMenuItem("Add Multiply Here", () -> spawnNode(MULTIPLY_FACTORY, emX, emY)),
                new ContextMenuItem("Add Tag Here",      () -> spawnNode(TAG_FACTORY,      emX, emY)),
                ContextMenuItem.separator(),
                new ContextMenuItem("Clear All Connections",
                    () -> {
                        for (Connection c : Connections.on(surface)) Connections.remove(surface, c);
                        System.out.println("Connections cleared");
                    }),
                new ContextMenuItem("Auto-arrange", false, () -> {})
            );
        });

        return surface;
    }

    /**
     * Spawn a fresh node from {@code factory} onto {@link #DEMO_SURFACE} at the
     * given em position. Adds it to {@link GraphSurfaceChildren} (the runtime
     * dynamic-children sidecar) so layout/render/hit-test/ports pick it up,
     * positions it, and wires the same context menus the initial nodes use.
     */
    private static void spawnNode(java.util.function.Supplier<Component> factory, float emX, float emY) {
        Component.GraphSurface surface = DEMO_SURFACE;
        if (surface == null) return;
        Component node = factory.get();
        GraphSurfacePositions.set(surface, node, emX, emY);
        GraphSurfaceChildren.add(surface, node);
        wireNodeContextMenu(node);
        PortContextMenu.registerDefaults(node);
    }

    private static void wireNodeContextMenu(Component node) {
        Handlers.onContextMenu(node, event -> List.of(
            new ContextMenuItem("Delete Node",
                () -> System.out.println("Delete node (placeholder)")),
            new ContextMenuItem("Duplicate Node", false, () -> {})
        ));
    }

    /** Widgets pane — scrollable column with the basic interactive widgets. */
    private static Component buildWidgetsPane() {
        // ---- Variant buttons ----
        Component dfltBtn = Themed.button("Default", Em.of(6f), Variant.DEFAULT, 0);
        Component prmBtn  = Themed.button("Primary", Em.of(6f), Variant.PRIMARY, 0);
        Component sucBtn  = Themed.button("Success", Em.of(6f), Variant.SUCCESS, 0);
        Component wrnBtn  = Themed.button("Warning", Em.of(6f), Variant.WARNING, 0);
        Component errBtn  = Themed.button("Error",   Em.of(6f), Variant.ERROR,   0);
        Component infoBtn = Themed.button("Info",    Em.of(6f), Variant.INFO,    0);

        Handlers.onClick(dfltBtn, () -> System.out.println("Default clicked"));
        Handlers.onClick(prmBtn,  () -> System.out.println("Primary clicked"));
        Handlers.onClick(sucBtn,  () -> System.out.println("Success clicked"));
        Handlers.onClick(wrnBtn,  () -> System.out.println("Warning clicked"));
        Handlers.onClick(errBtn,  () -> System.out.println("Error clicked"));
        Handlers.onClick(infoBtn, () -> System.out.println("Info clicked"));

        Component variantButtonRow = inlineRow(List.of(dfltBtn, prmBtn, sucBtn, wrnBtn, errBtn, infoBtn));

        // ---- Variant checkboxes ----
        Property<Boolean> vCbDflt = new Property<>(true);
        Property<Boolean> vCbPrm  = new Property<>(true);
        Property<Boolean> vCbSuc  = new Property<>(true);
        Property<Boolean> vCbWrn  = new Property<>(true);
        Property<Boolean> vCbErr  = new Property<>(true);
        Property<Boolean> vCbInfo = new Property<>(true);

        Component variantCheckboxRow = inlineRow(List.of(
            Themed.checkbox(Em.of(1.2f), vCbDflt, Variant.DEFAULT), Themed.label("default", Em.of(0.9f), Variant.DEFAULT),
            Themed.checkbox(Em.of(1.2f), vCbPrm,  Variant.PRIMARY), Themed.label("primary", Em.of(0.9f), Variant.PRIMARY),
            Themed.checkbox(Em.of(1.2f), vCbSuc,  Variant.SUCCESS), Themed.label("success", Em.of(0.9f), Variant.SUCCESS),
            Themed.checkbox(Em.of(1.2f), vCbWrn,  Variant.WARNING), Themed.label("warning", Em.of(0.9f), Variant.WARNING),
            Themed.checkbox(Em.of(1.2f), vCbErr,  Variant.ERROR),   Themed.label("error",   Em.of(0.9f), Variant.ERROR),
            Themed.checkbox(Em.of(1.2f), vCbInfo, Variant.INFO),    Themed.label("info",    Em.of(0.9f), Variant.INFO)
        ));

        // ---- Plain checkboxes ----
        Property<Boolean> darkMode  = new Property<>(true);
        Property<Boolean> autoSave  = new Property<>(false);
        Property<Boolean> showStats = new Property<>(false);
        darkMode.subscribe(v -> System.out.println("Dark mode: " + v));

        Component.Checkbox darkCb  = new Component.Checkbox(Em.of(1.2f), CB_BOX, CB_CHECK, darkMode);
        Component.Checkbox autoCb  = new Component.Checkbox(Em.of(1.2f), CB_BOX, CB_CHECK, autoSave);
        Component.Checkbox statsCb = new Component.Checkbox(Em.of(1.2f), CB_BOX, CB_CHECK, showStats);

        Component checkboxRow = inlineRow(List.of(
            darkCb,  new Component.Text("Dark mode",  Em.of(1.0f), LABEL_FG),
            autoCb,  new Component.Text("Auto-save",  Em.of(1.0f), LABEL_FG),
            statsCb, new Component.Text("Show stats", Em.of(1.0f), LABEL_FG)
        ));

        // ---- Radios ----
        Property<String> theme = new Property<>("auto");
        theme.subscribe(v -> System.out.println("Theme: " + v));

        Component.Radio<String> rbLight = new Component.Radio<>(Em.of(1.2f), RB_BOX, RB_DOT, theme, "light");
        Component.Radio<String> rbDark  = new Component.Radio<>(Em.of(1.2f), RB_BOX, RB_DOT, theme, "dark");
        Component.Radio<String> rbAuto  = new Component.Radio<>(Em.of(1.2f), RB_BOX, RB_DOT, theme, "auto");

        Component radioRow = inlineRow(List.of(
            rbLight, new Component.Text("Light", Em.of(1.0f), LABEL_FG),
            rbDark,  new Component.Text("Dark",  Em.of(1.0f), LABEL_FG),
            rbAuto,  new Component.Text("Auto",  Em.of(1.0f), LABEL_FG)
        ));

        // ---- Slider ----
        Property<Float> volume = new Property<>(50f);
        volume.subscribe(v -> System.out.println("Volume: " + Math.round(v)));

        Component.Slider volumeSlider = new Component.Slider(
            Direction.ROW,
            Em.of(16f), Em.of(1.0f), Em.of(0.6f),
            SL_TRACK, SL_FILL, SL_THUMB,
            volume, 0f, 100f
        );
        Component sliderRow = inlineRow(List.of(
            new Component.Text("Volume:", Em.of(1.0f), LABEL_FG), volumeSlider
        ));

        // ---- Assemble sectioned column ----
        Component.Flex column = new Component.Flex(
            null, Em.AUTO, Em.of(1.2f), new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(1.4f),
            List.of(
                section("Variant buttons",    variantButtonRow),
                section("Variant checkboxes", variantCheckboxRow),
                section("Checkboxes",         checkboxRow),
                section("Radio group",        radioRow),
                section("Slider",             sliderRow)
            ),
            false, 0
        );

        return new Component.Scroll(
            null, null, Em.ZERO, CONTENT_BG,
            column, false, 1
        );
    }

    /**
     * Point-cloud pane — a unit-cube scatter rendered through dasum-vis.
     * Left-click drag orbits the camera; scroll wheel zooms; a perspective /
     * orthographic toggle lives in the top-right of the pane.
     */
    private static Component buildPointCloudPane() {
        Color cloudBg = new Color(0.04f, 0.05f, 0.08f, 1f);

        Component.PointCloud viewport = new Component.PointCloud(
            null, null, Em.ZERO, cloudBg, true, 1
        );

        // Build a 1000-point unit cube; a small per-axis colour bias makes
        // rotation/depth easy to read visually (red ~ x, green ~ y, blue ~ z).
        final int N = 1000;
        float[] positions = new float[N * 3];
        float[] colors    = new float[N * 3];
        java.util.Random rng = new java.util.Random(7);
        for (int i = 0; i < N; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2f;
            float y = (rng.nextFloat() - 0.5f) * 2f;
            float z = (rng.nextFloat() - 0.5f) * 2f;
            positions[i*3    ] = x;
            positions[i*3 + 1] = y;
            positions[i*3 + 2] = z;
            colors[i*3    ] = 0.5f + 0.5f * x;
            colors[i*3 + 1] = 0.5f + 0.5f * y;
            colors[i*3 + 2] = 0.5f + 0.5f * z;
        }
        PointCloudSnapshot snap = new PointCloudSnapshot(3, N, positions, colors, null, null);
        PointCloudStates.publish(viewport, snap);
        PointCloudStates.setCamera(viewport, CameraSpec.defaultPerspective());

        // Mode toggle button row.
        Component perspectiveBtn = button("Perspective", Em.of(8f), BTN_BLUE, 0);
        Component orthoBtn       = button("Orthographic", Em.of(8f), BTN_NEUTRAL, 0);
        Handlers.onClick(perspectiveBtn,
            () -> PointCloudStates.setCamera(viewport,
                  PointCloudStates.cameraOf(viewport).withMode(
                      sibarum.dasum.gui.vis.math.CameraMode.PERSPECTIVE)));
        Handlers.onClick(orthoBtn,
            () -> PointCloudStates.setCamera(viewport,
                  PointCloudStates.cameraOf(viewport).withMode(
                      sibarum.dasum.gui.vis.math.CameraMode.ORTHOGRAPHIC)));

        Component toolbar = new Component.Flex(
            null, Em.of(2.4f), Em.of(0.4f), TOOLBAR_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(perspectiveBtn, orthoBtn,
                    new Component.Text("Drag to rotate · Scroll to zoom", Em.of(0.9f), LABEL_FG)),
            false, 0
        );

        return new Component.Flex(
            null, null, Em.ZERO, CONTENT_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(toolbar, viewport),
            false, 1
        );
    }

    /** Text pane — the editable paragraph and its caret/selection/clipboard demo. */
    private static Component buildTextPane() {
        String paragraph =
            "Editable text component.\n" +
            "\n" +
            "Click to place the caret. Drag to select.\n" +
            "Type to insert. Backspace / Delete remove.\n" +
            "Enter inserts a newline.\n" +
            "Ctrl+C copy, Ctrl+X cut, Ctrl+V paste.\n" +
            "Right-click for the context menu (Cut/Copy/Paste/Select All).\n" +
            "Tab moves focus; ESC clears selection or focus.\n" +
            "\n" +
            "Hover (without clicking) to see the phantom caret.";

        Component.Text paragraphText = new Component.Text(
            paragraph, FontGroups.DEFAULT, Em.of(1.1f), BODY_TEXT,
            null, null, Em.of(1.2f),
            null, false,
            true, true, true, false, 0
        );

        return new Component.Scroll(
            null, null, Em.of(1f), CONTENT_BG,
            paragraphText, false, 1
        );
    }

    // ---------- pane composition helpers ----------

    /** A horizontal row of items used as the body of a section. */
    private static Component inlineRow(List<Component> items) {
        return new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.6f),
            items, false, 0
        );
    }

    /** Section: heading text above its content, stacked vertically. */
    private static Component section(String heading, Component content) {
        Component header = new Component.Text(heading, Em.of(1.0f), LABEL_FG);
        return new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.5f),
            List.of(header, content),
            false, 0
        );
    }

    private static void wireInput(Window window, CursorManager cursors) {
        GlfwCallbacks.setKeyListener((win, key, scancode, action, mods) -> {
            if (action != Glfw.GLFW_PRESS && action != Glfw.GLFW_REPEAT) return;
            boolean ctrl  = (mods & Glfw.GLFW_MOD_CONTROL) != 0;
            boolean shift = (mods & Glfw.GLFW_MOD_SHIFT)   != 0;

            // Open palette. Reachable both when the menu is closed and when
            // some other modal is open — current overlay is preserved beneath.
            if (ctrl && key == Glfw.GLFW_KEY_SPACE && !EverythingMenu.isOpen()) {
                EverythingMenu.open();
                return;
            }

            // Context menu (if open) intercepts arrows / Enter / Escape so
            // they don't fall through to focused widgets behind the popup.
            if (ContextMenuController.handleKey(key)) return;

            // Up/Down/Enter route through the menu's key handler before any
            // text-input handler so they don't move the query caret.
            if (EverythingMenu.handleKey(key)) return;

            // Clipboard shortcuts run first so they're not intercepted by other handlers.
            // GLFW key codes for letters match ASCII uppercase ('A'..'Z' = 65..90).
            if (ctrl && key == 'C') {
                if (TextInputController.onCopy(window.handle())) return;
            }
            if (ctrl && key == 'X') {
                if (TextInputController.onCut(window.handle())) return;
            }
            if (ctrl && key == 'V') {
                if (TextInputController.onPaste(window.handle())) return;
            }
            if (ctrl && key == 'A') {
                if (TextInputController.onSelectAll()) return;
            }
            if (ctrl && key == 'Z') {
                if (shift) {
                    if (TextInputController.onRedo()) return;
                } else {
                    if (TextInputController.onUndo()) return;
                }
            }
            if (ctrl && key == 'Y') {
                if (TextInputController.onRedo()) return;
            }

            // Editing keys.
            if (key == Glfw.GLFW_KEY_BACKSPACE && TextInputController.onBackspace(ctrl)) return;
            if (key == Glfw.GLFW_KEY_DELETE    && TextInputController.onDelete(ctrl))    return;
            // After the text handlers — if no editable text consumed the key,
            // fall through to removing a selected connection. Both Delete and
            // Backspace work; standard desktop convention varies, support both.
            if ((key == Glfw.GLFW_KEY_DELETE || key == Glfw.GLFW_KEY_BACKSPACE)
                && ConnectionSelectionController.onDelete()) return;
            if (key == Glfw.GLFW_KEY_ENTER     && TextInputController.onEnter())         return;

            // Space / Enter activates a focused Checkbox or Radio. Runs after
            // onEnter so an editable Text gets newlines first; Space passes
            // through here because it doesn't fire onCharInput when focus is
            // on a non-editable widget.
            if (key == Glfw.GLFW_KEY_SPACE || key == Glfw.GLFW_KEY_ENTER) {
                Component focused = FocusState.focused();
                if (focused instanceof Component.Checkbox || focused instanceof Component.Radio<?>) {
                    Component layoutRoot = LatestLayout.root();
                    Handlers.activate(focused, layoutRoot);
                    return;
                }
            }

            // Tab inserts literal '\t' only when the focused Text opted in;
            // otherwise it falls through to focus cycling below.
            if (key == Glfw.GLFW_KEY_TAB && TextInputController.onTab()) return;

            // Navigation keys (arrows / Home / End, optionally with Ctrl).
            if (TextInputController.onKey(key, shift, ctrl)) return;
            if (SliderController.onKey(key)) return;
            if (TabsController.onKey(key))   return;

            if (key == Glfw.GLFW_KEY_ESCAPE && action == Glfw.GLFW_PRESS) {
                if (ConnectionDragController.isDragging()) {
                    ConnectionDragController.cancelDrag();
                    return;
                }
                if (OverlayStack.isActive()) {
                    OverlayStack.pop();
                    return;
                }
                if (ConnectionSelection.has()) {
                    ConnectionSelection.clear();
                    return;
                }
                Component focused = FocusState.focused();
                if (focused instanceof Component.Text t && t.selectable() && TextStates.of(focused).hasSelection()) {
                    TextStates.of(focused).collapseToCaret();
                    Invalidator.invalidate();
                } else if (focused != null) {
                    FocusState.clear();
                } else {
                    Glfw.glfwSetWindowShouldClose(window.handle(), true);
                    Invalidator.invalidate();
                }
            } else if (key == Glfw.GLFW_KEY_TAB) {
                Component layoutRoot = OverlayStack.activeInputRoot(LatestLayout.root());
                if (layoutRoot != null) FocusState.cycle(layoutRoot, shift);
            } else if (ctrl && key == Glfw.GLFW_KEY_EQUAL) {
                EmContext.multiplyZoom(1.1f);
            } else if (ctrl && key == Glfw.GLFW_KEY_MINUS) {
                EmContext.multiplyZoom(1f / 1.1f);
            } else if (ctrl && key == Glfw.GLFW_KEY_0) {
                EmContext.setZoom(1f);
            }
        });

        GlfwCallbacks.setCursorPosListener((win, x, y) -> {
            InputState.updateMousePos(x, y);
            ScrollbarController.onCursorMove(x, y);
            if (ScrollbarController.isDragging()) return;
            ConnectionDragController.onCursorMove(x, y);
            if (ConnectionDragController.isDragging()) return;
            GraphSurfaceController.onCursorMove(x, y);
            if (GraphSurfaceController.isDragging()) return;
            PointCloudController.onCursorMove(x, y);
            if (PointCloudController.isDragging()) return;
            // Hover-follow keyboard selection when the context menu is open.
            ContextMenuController.onCursorMove(x, y);

            LayoutResult lr = LatestLayout.result();
            Component layoutRoot = LatestLayout.root();
            if (lr == null || layoutRoot == null) return;

            // When an overlay is active, hover & cursor target the overlay's
            // tree exclusively (modal behavior); when none, the main UI.
            Component hitRoot = OverlayStack.activeInputRoot(layoutRoot);
            Component hit = HitTest.test(hitRoot, lr, (float) x, (float) y);
            HoverState.update(hit);
            cursors.setShape(cursorShapeFor(hit));

            TextInputController.onCursorMove(hit, x, y);
            SliderController.onCursorMove(x, y);
            TabsController.onCursorMove(x, y);
        });

        GlfwCallbacks.setMouseButtonListener((win, button, action, mods) -> {
            // Right-click opens a context menu on press; nothing to do on release.
            if (button == Glfw.GLFW_MOUSE_BUTTON_RIGHT) {
                if (action == Glfw.GLFW_PRESS) {
                    ContextMenuController.onMouseDown(
                        InputState.mouseX(), InputState.mouseY(), mods, window.handle());
                }
                return;
            }
            if (button != Glfw.GLFW_MOUSE_BUTTON_LEFT) return;
            boolean pressed = (action == Glfw.GLFW_PRESS);
            InputState.setLeftButtonHeld(pressed);
            boolean shift = (mods & Glfw.GLFW_MOD_SHIFT) != 0;

            if (pressed) {
                // Scrollbar thumbs aren't components — give them first refusal
                // so a press on a thumb doesn't focus / select content below.
                if (ScrollbarController.onMouseDown(InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    return;
                }
                // Tab cells aren't components either — same treatment.
                if (TabsController.onMouseDown(InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    return;
                }
                // Port press starts a connection drag — must come before
                // GraphSurfaceController so it wins on the inner-most hit
                // (port) rather than GraphSurfaceController claiming the
                // outer node body.
                if (ConnectionDragController.onMouseDown(InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    return;
                }
                // Canvas children drag on press; consume the click so they
                // don't also focus / activate other dispatchers.
                if (GraphSurfaceController.onMouseDown(InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    return;
                }
                // Point-cloud viewports: drag orbits the camera. Consume
                // so the click doesn't also focus / hit the viewport's
                // background as a generic interactive component.
                if (PointCloudController.onMouseDown(HoverState.hovered(), InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    FocusState.set(HoverState.hovered());
                    return;
                }
                // Connection selection — only fires when the click's deepest
                // hit IS the GraphSurface (i.e., on empty surface area), so
                // it can't steal a curve hit from a node-body drag.
                if (ConnectionSelectionController.onMouseDown(InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    return;
                }
                // Overlay capture: route the press through the topmost overlay's
                // component tree. Click-outside on a modal dismisses it.
                if (OverlayStack.isActive()) {
                    LayoutResult lr = LatestLayout.result();
                    if (OverlayStack.isOutsideTopmost(lr, (float) InputState.mouseX(), (float) InputState.mouseY())) {
                        if (OverlayStack.anyModal()) OverlayStack.pop();
                        pressTarget = null;
                        return;
                    }
                    Component overlayRoot = OverlayStack.activeInputRoot(null);
                    Component hit = (lr != null && overlayRoot != null)
                        ? HitTest.test(overlayRoot, lr, (float) InputState.mouseX(), (float) InputState.mouseY())
                        : null;
                    pressTarget = hit;
                    if (hit != null) FocusState.set(hit);
                    TextInputController.onMouseDown(hit, InputState.mouseX(), InputState.mouseY(), shift);
                    SliderController.onMouseDown(hit, InputState.mouseX(), InputState.mouseY());
                    return;
                }
                Component hovered = HoverState.hovered();
                pressTarget = hovered;
                if (hovered != null) FocusState.set(hovered);
                else                 FocusState.clear();
                TextInputController.onMouseDown(hovered, InputState.mouseX(), InputState.mouseY(), shift);
                SliderController.onMouseDown(hovered, InputState.mouseX(), InputState.mouseY());
            } else {
                // Release ends any scrollbar / slider / canvas / connection drag
                // regardless of where the cursor is.
                boolean scrollDrag = ScrollbarController.isDragging();
                boolean sliderDrag = SliderController.isDragging();
                boolean canvasDrag = GraphSurfaceController.isDragging();
                boolean connectionDrag = ConnectionDragController.isDragging();
                boolean pointCloudDrag = PointCloudController.isDragging();
                ScrollbarController.onMouseUp();
                SliderController.onMouseUp();
                GraphSurfaceController.onMouseUp();
                ConnectionDragController.onMouseUp();
                PointCloudController.onMouseUp();

                // Release-on-same-target fires the click. Re-hit-test rather
                // than reading HoverState — HoverState only updates on cursor-
                // move events, so it's stale right after an overlay opens
                // (e.g. right-click → menu) when no cursor-move has fired yet.
                // The fresh hit-test also makes the check correct after any
                // mid-click layout change. Skip when the press began a drag —
                // the value has already been committed.
                LayoutResult lr2 = LatestLayout.result();
                Component dispatchRoot = OverlayStack.activeInputRoot(LatestLayout.root());
                Component released = (lr2 != null && dispatchRoot != null)
                    ? HitTest.test(dispatchRoot, lr2, (float) InputState.mouseX(), (float) InputState.mouseY())
                    : null;
                if (!scrollDrag && !sliderDrag && !canvasDrag && !connectionDrag && !pointCloudDrag && pressTarget != null && released == pressTarget) {
                    Handlers.activate(pressTarget, dispatchRoot);
                }
                pressTarget = null;
            }
        });

        GlfwCallbacks.setCharListener((win, codepoint) -> {
            TextInputController.onCharInput(codepoint);
        });

        GlfwCallbacks.setCursorEnterListener((win, entered) -> {
            if (!entered) {
                // Mouse left the window. Clear hover + phantom; cursor visual is
                // the OS's responsibility while outside our window. Drag selection
                // continues — TextInputController uses FocusState (preserved),
                // not HoverState, and GLFW keeps firing cursor-pos events while
                // a button is held even with the cursor outside the window.
                HoverState.clear();
                TextStates.clearAllHoverCarets();
                ScrollbarController.clearHover();
                TabsController.clearHover();
                cursors.setShape(CursorManager.CursorShape.ARROW);
                Invalidator.invalidate();
            }
        });

        GlfwCallbacks.setWindowFocusListener((win, focused) -> {
            if (!focused) {
                SliderController.cancelDrag();
                ScrollbarController.cancelDrag();
                GraphSurfaceController.cancelDrag();
                ConnectionDragController.cancelDrag();
                PointCloudController.cancelDrag();
                // Window lost OS focus. Clear hover and pending drag — the
                // mouseup likely happened in another window and we'll never
                // see it. Selection PERSISTS so users can alt-tab away to
                // a reference window and back without losing their selection.
                HoverState.clear();
                TextStates.clearAllHoverCarets();
                InputState.setLeftButtonHeld(false);
            }
            Invalidator.invalidate();
        });

        GlfwCallbacks.setScrollListener((win, xOff, yOff) -> {
            LayoutResult lr = LatestLayout.result();
            Component layoutRoot = OverlayStack.activeInputRoot(LatestLayout.root());
            if (lr == null || layoutRoot == null) return;
            // Point-cloud viewport gets first refusal — scroll over a
            // viewport zooms its camera rather than scrolling a container.
            Component pcHit = HitTest.test(layoutRoot, lr, (float) InputState.mouseX(), (float) InputState.mouseY());
            if (PointCloudController.onScroll(pcHit, yOff)) return;
            Component.Scroll target = HitTest.findScroll(layoutRoot, lr, (float) InputState.mouseX(), (float) InputState.mouseY());
            if (target == null) return;

            boolean shift = Glfw.glfwGetKey(window.handle(), Glfw.GLFW_KEY_LEFT_SHIFT)  == Glfw.GLFW_PRESS
                        || Glfw.glfwGetKey(window.handle(), Glfw.GLFW_KEY_RIGHT_SHIFT) == Glfw.GLFW_PRESS;
            double dx, dy;
            if (shift) { dx = -yOff * WHEEL_PIXELS_PER_STEP; dy = 0; }
            else        { dx = -xOff * WHEEL_PIXELS_PER_STEP; dy = -yOff * WHEEL_PIXELS_PER_STEP; }
            ScrollStates.of(target).scrollByPx((float) dx, (float) dy);
        });
    }

    private static void registerCommands(Window window) {
        CommandRegistry.register("help.open",   "Open Help Dialog",      App::openHelpDialog);
        CommandRegistry.register("zoom.in",     "Zoom In",                () -> EmContext.multiplyZoom(1.1f));
        CommandRegistry.register("zoom.out",    "Zoom Out",               () -> EmContext.multiplyZoom(1f / 1.1f));
        CommandRegistry.register("zoom.reset",  "Reset Zoom",             () -> EmContext.setZoom(1f));
        CommandRegistry.register("focus.clear", "Clear Focus",            FocusState::clear);
        List<FileDialog.Filter> demoFilters = List.of(
            FileDialog.Filter.of("Text", "txt", "md"),
            FileDialog.Filter.of("Images", "png", "jpg")
        );
        CommandRegistry.register("file.open",   "File: Open",             () ->
            FileDialog.open(window, demoFilters, null)
                .ifPresentOrElse(p -> System.out.println("Opened: " + p),
                                 () -> System.out.println("Open cancelled")));
        CommandRegistry.register("file.save",   "File: Save",             () ->
            FileDialog.save(window, demoFilters, null, "untitled.txt")
                .ifPresentOrElse(p -> System.out.println("Save to: " + p),
                                 () -> System.out.println("Save cancelled")));
        CommandRegistry.register("file.saveas", "File: Save As…",         () ->
            FileDialog.save(window, demoFilters, null, "untitled.txt")
                .ifPresentOrElse(p -> System.out.println("Save As: " + p),
                                 () -> System.out.println("Save As cancelled")));
        CommandRegistry.register("file.pickdir","File: Pick Folder",      () ->
            FileDialog.pickFolder(window, null)
                .ifPresentOrElse(p -> System.out.println("Folder: " + p),
                                 () -> System.out.println("Pick folder cancelled")));
        CommandRegistry.register("edit.find",   "Edit: Find",             () -> System.out.println("Edit: Find"));
        CommandRegistry.register("edit.replace","Edit: Replace",          () -> System.out.println("Edit: Replace"));
        // Node-spawn commands — discoverable via Ctrl+Space. Default to (3, 3)
        // em on the demo surface; the user can drag from there. (The surface
        // right-click "Add … Here" items spawn at cursor position instead.)
        CommandRegistry.register("node.add.constant", "Add Constant Node", () -> spawnNode(CONSTANT_FACTORY, 3f, 3f));
        CommandRegistry.register("node.add.multiply", "Add Multiply Node", () -> spawnNode(MULTIPLY_FACTORY, 3f, 3f));
        CommandRegistry.register("node.add.tag",      "Add Tag Node",      () -> spawnNode(TAG_FACTORY,      3f, 3f));
        CommandRegistry.register("view.stats",  "View: Toggle Stats",     () -> System.out.println("View: Toggle Stats"));
        CommandRegistry.register("view.theme",  "View: Switch Theme",     () -> System.out.println("View: Switch Theme"));
        CommandRegistry.register("quit",        "Quit DasumGUIshi",       () -> {
            Glfw.glfwSetWindowShouldClose(window.handle(), true);
            Invalidator.invalidate();
        });
    }

    private static void openHelpDialog() {
        Color dialogBg     = new Color(0.10f, 0.12f, 0.16f, 1f);
        Color dialogBorder = new Color(0.30f, 0.55f, 0.85f, 0.9f);

        Component title = new Component.Text("Help", Em.of(1.4f), LABEL_FG);
        Component body  = new Component.Text("This is a modal overlay rendered above the main UI. ESC or click outside the dialog to dismiss. Tab cycles focus within the overlay only.", Em.of(0.95f), BODY_TEXT)
            .withWrapWidth(Em.of(20f))
            .withClip(true);
        Component closeBtn = Themed.button("Close", Em.of(6f), Variant.PRIMARY, 0);

        Component.Flex dialog = new Component.Flex(
            Em.of(22f), Em.AUTO, Em.of(1.0f), dialogBg,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.75f),
            List.of(title, body, closeBtn),
            false, 0
        );

        // 1px-ish border by wrapping in an outer Flex tinted as a halo.
        Component.Flex framed = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.125f), dialogBorder,
            Direction.COLUMN, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(dialog),
            false, 0
        );

        Handlers.onClick(closeBtn, OverlayStack::pop);
        OverlayStack.push(new OverlayStack.Overlay(framed, Anchor.CENTER, true,
            () -> System.out.println("Help dialog dismissed")));
    }

    private static CursorManager.CursorShape cursorShapeFor(Component hit) {
        if (hit instanceof Component.Text t && t.selectable()) return CursorManager.CursorShape.IBEAM;
        // GraphSurface is interactive (so empty-area right-clicks land on it)
        // but it shouldn't read as "clickable" — keep the arrow cursor over
        // empty surface area. Nodes and ports inside the surface are still
        // interactive components and still show HAND on hover.
        if (hit instanceof Component.GraphSurface) return CursorManager.CursorShape.ARROW;
        if (hit != null) return CursorManager.CursorShape.HAND;
        return CursorManager.CursorShape.ARROW;
    }
}
