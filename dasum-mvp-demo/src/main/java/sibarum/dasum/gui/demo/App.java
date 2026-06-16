package sibarum.dasum.gui.demo;

import sibarum.dasum.gui.core.GlfwContext;
import sibarum.dasum.gui.core.command.CommandRegistry;
import sibarum.dasum.gui.core.command.EverythingMenu;
import sibarum.dasum.gui.core.dialog.FileDialog;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Components;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.DynamicChildren;
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
import sibarum.dasum.gui.core.graph.NodeInstances;
import sibarum.dasum.gui.core.graph.PortType;
import sibarum.dasum.gui.core.graph.Ports;
import sibarum.dasum.gui.core.graph.ConnectionSelection;
import sibarum.dasum.gui.core.graph.GraphSurfaceChildren;
import sibarum.dasum.gui.core.graph.SubgraphNodeBuilder;
import sibarum.dasum.gui.core.graph.SubgraphNodes;
import sibarum.dasum.gui.core.data.DataTableClipboard;
import sibarum.dasum.gui.core.data.DataTableSource;
import sibarum.dasum.gui.core.data.DataTableStates;
import sibarum.dasum.gui.core.data.DataTables;
import sibarum.dasum.gui.core.data.TableContextMenu;
import sibarum.dasum.gui.core.data.TableSelection;
import sibarum.dasum.gui.core.input.ConnectionDragController;
import sibarum.dasum.gui.core.input.ConnectionSelectionController;
import sibarum.dasum.gui.core.input.DataTableSelectionController;
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
import sibarum.dasum.gui.core.nav.NavId;
import sibarum.dasum.gui.core.nav.NavRegistry;
import sibarum.dasum.gui.core.input.TabsController;
import sibarum.dasum.gui.core.input.TextInputController;
import sibarum.dasum.gui.core.input.TextState;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.input.TextStyle;
import sibarum.dasum.gui.core.input.TextStyleStates;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.Layout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.layout.Render;
import sibarum.dasum.gui.core.overlay.Anchor;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.overlay.TooltipController;
import sibarum.dasum.gui.core.overlay.TooltipRenderer;
import sibarum.dasum.gui.core.overlay.TooltipTrigger;
import sibarum.dasum.gui.core.overlay.Tooltips;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.RenderStats;
import sibarum.dasum.gui.core.render.Texture;
import sibarum.dasum.gui.core.text.AtlasData;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.Icon;
import sibarum.dasum.gui.demo.generated.Icons;
import sibarum.dasum.gui.core.theme.Themed;
import sibarum.dasum.gui.core.theme.Variant;
import sibarum.dasum.gui.core.window.Window;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.natives.glfw.Glfw;
import sibarum.dasum.gui.natives.glfw.GlfwCallbacks;
import sibarum.dasum.gui.vis.DasumVis;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.pointcloud.SceneViewController;
import sibarum.dasum.gui.vis.pointcloud.PointCloudSnapshot;
import sibarum.dasum.gui.vis.pointcloud.PointCloudStates;
import sibarum.dasum.gui.vis.scene.BlendMode;
import sibarum.dasum.gui.vis.scene.CsgBox;
import sibarum.dasum.gui.vis.scene.MandelbulbField;
import sibarum.dasum.gui.vis.scene.SurfaceSampler;
import sibarum.dasum.gui.vis.scene.VexelRayView;
import sibarum.dasum.gui.vis.scene.ImageLayer;
import sibarum.dasum.gui.vis.scene.InteractionSpec;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.PointLayer;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.SceneStates;
import sibarum.dasum.gui.vis.scene.TextLayer;
import sibarum.dasum.gui.vis.scene.TriangleLayer;
import sibarum.dasum.gui.vis.scene.VexelRayLayer;
import sibarum.dasum.gui.vis.plot.Axis;
import sibarum.dasum.gui.vis.plot.ComplexColorMaps;
import sibarum.dasum.gui.vis.plot.ComplexField2D;
import sibarum.dasum.gui.vis.plot.ComplexField3D;
import sibarum.dasum.gui.vis.plot.FieldSlicePlot;
import sibarum.dasum.gui.vis.plot.LinePlot;
import sibarum.dasum.gui.vis.plot.PlotFrame;
import sibarum.dasum.gui.vis.plot.PlotStyle;
import sibarum.dasum.gui.vis.plot.PlotView;
import sibarum.dasum.gui.vis.plot.Series;
import sibarum.dasum.gui.vis.math.Vec3;
import sibarum.dasum.gui.vis.pointcloud.PointHandlers;

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
    // Subgraph (group) node — runtime-creatable type whose contents are an
    // inner GraphSurface. Click the outer node to expand into a modal
    // overlay that hosts the inner surface; close to collapse. Inner
    // sub-graphs are queryable via NodeInstances + SubgraphNodes for
    // app-side serialization.
    private static final java.util.function.Supplier<Component> SUBGRAPH_FACTORY = () -> {
        Component outer = SubgraphNodeBuilder.titled("Group")
            .input(NUMBER, "x")
            .input(NUMBER, "y")
            .output(NUMBER, "out")
            .outerBackground(new Color(0.24f, 0.28f, 0.40f, 1f))
            .innerSurfaceBg(new Color(0.04f, 0.06f, 0.10f, 1f))
            .stubBackground(new Color(0.18f, 0.22f, 0.30f, 1f))
            .build();
        // Wire single-click to expand the inner surface in a modal overlay,
        // mirroring the PointCloud thumbnail-to-modal pattern.
        Handlers.onClick(outer, () -> openSubgraphOverlay(outer));
        return outer;
    };

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
            DataTables.init();
            EmContext.setDpiScale(window.contentScaleX());

            try (Texture primaryTexture = Texture.fromPngResource("/dasum/atlas/primary.png");
                 Texture iconsTexture   = Texture.fromPngResource("/dasum/atlas/icons.png")) {
                AtlasData primaryAtlas = AtlasData.loadFromResource("/dasum/atlas/primary.json");
                AtlasData iconsAtlas   = AtlasData.loadFromResource("/dasum/atlas/icons.json");
                FontGroups.register(FontGroup.of(FontGroups.DEFAULT, primaryAtlas, primaryTexture));
                FontGroups.register(FontGroup.of(Icon.DEFAULT_FONT_GROUP, iconsAtlas, iconsTexture));

                Component root = buildUi();
                wireInput(window, cursors);
                registerCommands(window);

                RenderStats stats = new RenderStats();
                System.out.println("Demo: top-level tabs — Node Editor / Widgets / Text / Stress / Tables. Ctrl+Space opens the command palette; Ctrl+=/- zoom.");

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
                    // Tooltip rides above every previous z-layer. The
                    // controller re-resolves hover against the freshly-
                    // computed layout each frame so a UI rebuild that
                    // moves components under a stationary cursor still
                    // produces the correct anchor/text. Resolve scope is
                    // the active overlay tree when an overlay is up so
                    // tooltips don't leak through modals.
                    {
                        Component ttRoot = OverlayStack.activeInputRoot(root);
                        TooltipController.resolveBeforeRender(
                            layout, ttRoot, InputState.mouseX(), InputState.mouseY());
                        // Hard flush before drawing — the batcher's two-pass
                        // solids/text flush would otherwise sandwich the
                        // tooltip's text underneath any later z-layer's
                        // solid quads.
                        batcher.flush(projection);
                        TooltipRenderer.render(batcher, projection, viewport);
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
        Component file    = Themed.iconButton(Icons.FOLDER,      "File", Em.of(5.5f), Variant.ERROR,   0);
        Component edit    = Themed.iconButton(Icons.EDIT,        "Edit", Em.of(5.5f), Variant.WARNING, 0);
        Component view    = Themed.iconButton(Icons.EYE,         "View", Em.of(5.5f), Variant.SUCCESS, 0);
        Component help    = Themed.iconButton(Icons.HELP_CIRCLE, "Help", Em.of(5.5f), Variant.PRIMARY, 0);
        Component account = button("Account", Em.of(0f),   BTN_PURPLE, 1);

        Handlers.onClick(file,    () -> CommandRegistry.invoke("file.open"));
        Handlers.onClick(edit,    () -> System.out.println("Edit clicked"));
        Handlers.onClick(view,    () -> System.out.println("View clicked"));
        Handlers.onClick(help,    () -> openHelpDialog());
        Handlers.onClick(account, () -> System.out.println("Account clicked"));

        Tooltips.set(file,    "Open / save / create files (Ctrl+O / Ctrl+S)");
        Tooltips.set(edit,    "Undo, redo, cut, copy, paste — Ctrl+Z to undo");
        Tooltips.set(view,    "Toggle view options");
        Tooltips.set(help,    "Open the Help dialog");
        Tooltips.set(account, "Account & profile");

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
                new Component.Tabs.TabPanel("Stress",      buildStressPane()),
                new Component.Tabs.TabPanel("VexelRay",    buildVexelRayPane()),
                new Component.Tabs.TabPanel("Plots",       buildPlotsPane()),
                new Component.Tabs.TabPanel("Tables",      buildTablesPane())
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
        Component pointCloudNode = buildPointCloudNode();

        // null width/height = fill the tab pane (Layout.childExtentOrFill
        // for GraphSurface returns max(explicit, fillExtent)). interactive=true
        // so right-clicks on empty surface area land on the surface itself.
        Component.GraphSurface surface = new Component.GraphSurface(
            null, null, surfaceBg,
            List.of(constantNode, multiplyNode, tagNode, pointCloudNode), true, 0
        );
        DEMO_SURFACE = surface;
        GraphSurfacePositions.set(surface, constantNode,  2f, 2f);
        GraphSurfacePositions.set(surface, multiplyNode, 18f, 2f);
        GraphSurfacePositions.set(surface, tagNode,      36f, 2f);
        GraphSurfacePositions.set(surface, pointCloudNode, 2f, 12f);

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
                new ContextMenuItem("Add Subgraph Here", () -> spawnNode(SUBGRAPH_FACTORY, emX, emY)),
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
            new ContextMenuItem("Delete Node", () -> deleteNode(node)),
            new ContextMenuItem("Duplicate Node", false, () -> {})
        ));
    }

    /**
     * Remove {@code node} from the demo surface. Detach cascades through every
     * sidecar — including {@link SubgraphNodes}, which recursively detaches a
     * subgraph node's inner surface so it doesn't linger as zombie state.
     */
    private static void deleteNode(Component node) {
        Component.GraphSurface surface = DEMO_SURFACE;
        if (surface == null) return;
        GraphSurfaceChildren.remove(surface, node);
        Components.detach(node);
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

        // Hook a few tooltips onto the widgets above so the user can hover
        // a checkbox/radio/slider and see help text.
        Tooltips.set(darkCb,  "Toggle the dark color scheme");
        Tooltips.set(autoCb,  "Auto-save every few seconds");
        Tooltips.set(statsCb, "Show frame statistics overlay");
        Tooltips.set(rbLight, "Use light theme");
        Tooltips.set(rbDark,  "Use dark theme");
        Tooltips.set(rbAuto,  "Follow system theme preference");
        Tooltips.set(volumeSlider, "Drag to set volume (0 — 100)");

        // Navigation destination: reachable via the Everything Menu ("Go to:
        // Volume slider") or Navigator.navigate("widgets.volume").
        NavId.tag(volumeSlider, "widgets.volume");
        NavRegistry.register("widgets.volume", "Volume slider");

        // ---- Tooltip showcase section: trigger mode + edge-overflow + long-text ----
        Component tooltipSection = buildTooltipShowcase();

        // ---- Assemble sectioned column ----
        Component.Flex column = new Component.Flex(
            null, Em.AUTO, Em.of(1.2f), new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(1.4f),
            List.of(
                section("Variant buttons",    variantButtonRow),
                section("Variant checkboxes", variantCheckboxRow),
                section("Checkboxes",         checkboxRow),
                section("Radio group",        radioRow),
                section("Slider",             sliderRow),
                section("Tooltips",           tooltipSection)
            ),
            false, 0
        );

        return new Component.Scroll(
            null, null, Em.ZERO, CONTENT_BG,
            column, false, 1
        );
    }

    /**
     * Build a graph node containing a small point-cloud thumbnail. Clicking
     * the node (without dragging) opens the same viewport in a full-screen
     * modal overlay; dragging on the node moves it around the surface as
     * any other node.
     *
     * <p>The thumbnail and the expanded view share the same
     * {@link Component.SceneView} instance — snapshot, camera, click
     * handler, and GPU buffer all follow the component because every
     * piece of state is identity-keyed off it. The node's body is a
     * {@code DynamicChildren}-backed slot so we can swap the viewport
     * out for a placeholder while it's in the overlay.
     */
    private static Component buildPointCloudNode() {
        Color cloudBg = new Color(0.04f, 0.05f, 0.08f, 1f);
        Color nodeBg  = new Color(0.18f, 0.22f, 0.28f, 1f);

        // Explicit em dimensions on the viewport give it a real intrinsic
        // size for the thumbnail context (12em x 8em). Inside the overlay's
        // STRETCH-aligned flex the explicit cross size is overridden by
        // crossAvail and the flexGrow=1 takes all remaining main-axis
        // space, so the same record renders at thumbnail size in the node
        // and full-overlay size in the popup.
        Component.SceneView viewport = new Component.SceneView(
            Em.of(12f), Em.of(8f), Em.ZERO, cloudBg, true, 1
        );

        // 1000-point unit cube; per-axis colour bias makes rotation +
        // depth ordering easy to read (red~x, green~y, blue~z).
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
        // Line-segment layer alongside the points:
        //   3 axes through the origin (X red↔white, Y green↔white, Z blue↔white)
        //   + a wireframe unit cube outline (12 edges) for context.
        // Demonstrates per-endpoint gradient: each axis fades from its dim's
        // saturated colour at one end to white at the other; cube edges use
        // a single colour at both endpoints.
        float[] segEndpoints = new float[(3 + 12) * 2 * 3];
        float[] segColors    = new float[(3 + 12) * 2 * 3];
        int si = 0;
        // X axis: red → white
        segEndpoints[si*6    ] = -1.4f; segEndpoints[si*6 + 1] =  0f;   segEndpoints[si*6 + 2] = 0f;
        segEndpoints[si*6 + 3] =  1.4f; segEndpoints[si*6 + 4] =  0f;   segEndpoints[si*6 + 5] = 0f;
        segColors   [si*6    ] = 1f;    segColors   [si*6 + 1] = 0.2f;  segColors   [si*6 + 2] = 0.2f;
        segColors   [si*6 + 3] = 1f;    segColors   [si*6 + 4] = 1f;    segColors   [si*6 + 5] = 1f;
        si++;
        // Y axis: green → white
        segEndpoints[si*6    ] =  0f;   segEndpoints[si*6 + 1] = -1.4f; segEndpoints[si*6 + 2] = 0f;
        segEndpoints[si*6 + 3] =  0f;   segEndpoints[si*6 + 4] =  1.4f; segEndpoints[si*6 + 5] = 0f;
        segColors   [si*6    ] = 0.2f;  segColors   [si*6 + 1] = 1f;    segColors   [si*6 + 2] = 0.2f;
        segColors   [si*6 + 3] = 1f;    segColors   [si*6 + 4] = 1f;    segColors   [si*6 + 5] = 1f;
        si++;
        // Z axis: blue → white
        segEndpoints[si*6    ] =  0f;   segEndpoints[si*6 + 1] =  0f;   segEndpoints[si*6 + 2] = -1.4f;
        segEndpoints[si*6 + 3] =  0f;   segEndpoints[si*6 + 4] =  0f;   segEndpoints[si*6 + 5] =  1.4f;
        segColors   [si*6    ] = 0.3f;  segColors   [si*6 + 1] = 0.5f;  segColors   [si*6 + 2] = 1f;
        segColors   [si*6 + 3] = 1f;    segColors   [si*6 + 4] = 1f;    segColors   [si*6 + 5] = 1f;
        si++;
        // Unit-cube edges — 12 edges, all the same dim colour at both endpoints
        // (cyan-grey). Vertices at ±1 in each axis.
        float[][] verts = {
            {-1,-1,-1}, { 1,-1,-1}, { 1, 1,-1}, {-1, 1,-1},
            {-1,-1, 1}, { 1,-1, 1}, { 1, 1, 1}, {-1, 1, 1}
        };
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},   // bottom
            {4,5},{5,6},{6,7},{7,4},   // top
            {0,4},{1,5},{2,6},{3,7}    // verticals
        };
        for (int[] e : edges) {
            float[] a = verts[e[0]];
            float[] b = verts[e[1]];
            segEndpoints[si*6    ] = a[0]; segEndpoints[si*6 + 1] = a[1]; segEndpoints[si*6 + 2] = a[2];
            segEndpoints[si*6 + 3] = b[0]; segEndpoints[si*6 + 4] = b[1]; segEndpoints[si*6 + 5] = b[2];
            segColors   [si*6    ] = 0.45f; segColors   [si*6 + 1] = 0.70f; segColors   [si*6 + 2] = 0.80f;
            segColors   [si*6 + 3] = 0.45f; segColors   [si*6 + 4] = 0.70f; segColors   [si*6 + 5] = 0.80f;
            si++;
        }
        PointCloudSnapshot snap = new PointCloudSnapshot(3, N, positions, colors, null, null)
            .withSegments(3 + 12, segEndpoints, segColors);
        PointCloudStates.publish(viewport, snap);
        PointCloudStates.setCamera(viewport, CameraSpec.defaultPerspective());

        PointHandlers.onPointClick(viewport, hit ->
            System.out.printf("Point %d clicked at world (%.3f, %.3f, %.3f)%n",
                hit.pointIndex(),
                hit.worldPosition().x(), hit.worldPosition().y(), hit.worldPosition().z()));

        // Slot for swap; viewport added via DynamicChildren so it can be
        // moved out into the overlay without rebuilding the slot record.
        Component.Flex slot = new Component.Flex(
            Em.of(12f), Em.of(8f), Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(), false, 0
        );
        DynamicChildren.add(slot, viewport);

        Component titleIcon = Icon.of(Icons.BOX, Em.of(1.1f), LABEL_FG);
        Component titleText = new Component.Text("Point Cloud", Em.of(1.05f), LABEL_FG);
        Component title = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.of(0.4f),
            List.of(titleIcon, titleText), false, 0
        );
        Component subtitle = new Component.Text("click to expand · drag to move",
            Em.of(0.75f), new Color(0.65f, 0.70f, 0.85f, 0.75f));

        Component.Flex node = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.6f), nodeBg,
            Direction.COLUMN, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            List.of(title, slot, subtitle), true, 0
        );

        Handlers.onClick(node, () -> openPointCloudOverlay(viewport, slot));
        return node;
    }

    /**
     * Move the viewport into a modal overlay, leaving a placeholder behind
     * in the {@code slot} so the node doesn't collapse to title-only height
     * while expanded. Dismissal (Close button, ESC, click-outside) restores
     * the viewport to its slot and detaches the overlay's transient widgets.
     */
    private static void openPointCloudOverlay(Component.SceneView viewport,
                                              Component slot) {
        Component placeholder = new Component.Box(
            Em.of(12f), Em.of(8f), Em.ZERO,
            new Color(0.04f, 0.05f, 0.08f, 0.6f),
            List.of(new Component.Text("Viewing in popup", Em.of(0.85f),
                new Color(0.65f, 0.70f, 0.85f, 0.85f)))
        );
        DynamicChildren.remove(slot, viewport);
        DynamicChildren.add(slot, placeholder);

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

        Component closeBtn = Themed.button("Close", Em.of(6f), Variant.PRIMARY, 0);
        Component title    = new Component.Text("Point Cloud — Expanded", Em.of(1.1f), LABEL_FG);
        Component spacer   = new Component.Box(Em.of(1f), Em.of(0f), Em.ZERO, new Color(0f, 0f, 0f, 0f))
                                  .withFlexGrow(1);

        Component header = new Component.Flex(
            null, Em.of(2.6f), Em.of(0.5f), TOOLBAR_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(title, perspectiveBtn, orthoBtn, spacer, closeBtn),
            false, 0
        );

        // 1000em width/height is clamped to the viewport by
        // OverlayStack.computeOverlayRect, giving us a full-screen modal.
        // Replace with Em.of(60f), Em.of(40f) for a smaller dialog.
        Component.Flex overlayRoot = new Component.Flex(
            Em.of(1000f), Em.of(1000f), Em.of(0.4f), CONTENT_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(header), false, 0
        );
        DynamicChildren.add(overlayRoot, viewport);

        Runnable restore = () -> {
            DynamicChildren.remove(overlayRoot, viewport);
            DynamicChildren.remove(slot, placeholder);
            DynamicChildren.add(slot, viewport);
            Components.detach(overlayRoot);
            Components.detach(placeholder);
        };
        Handlers.onClick(closeBtn, OverlayStack::pop);
        OverlayStack.push(new OverlayStack.Overlay(overlayRoot, Anchor.CENTER, true, restore));
    }

    /**
     * Mount the inner GraphSurface of a subgraph node into a full-screen
     * modal overlay so the user can inspect / edit the sub-graph. Inner
     * stub ports are already declared on the inner surface (with the
     * inverse direction to the outer port they proxy) so connection-drag
     * inside the modal "just works."
     *
     * <p>Mirrors {@link #openPointCloudOverlay} structurally but skips the
     * placeholder swap — the outer node visually stays put behind the
     * modal backdrop because we don't move it into the overlay.
     */
    private static void openSubgraphOverlay(Component outerNode) {
        SubgraphNodes.Subgraph sg = SubgraphNodes.of(outerNode);
        if (sg == null) return;
        Component.GraphSurface innerSurface = sg.innerSurface();
        NodeInstances.NodeInstance ni = NodeInstances.of(outerNode);
        String title = (ni != null && ni.params().get("title") instanceof String t) ? t : "Subgraph";

        Component closeBtn = Themed.button("Close", Em.of(6f), Variant.PRIMARY, 0);
        Component titleText = new Component.Text(title + " — Subgraph", Em.of(1.1f), LABEL_FG);
        Component spacer = new Component.Box(Em.of(1f), Em.of(0f), Em.ZERO, new Color(0f, 0f, 0f, 0f))
            .withFlexGrow(1);

        Component header = new Component.Flex(
            null, Em.of(2.6f), Em.of(0.5f), TOOLBAR_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(titleText, spacer, closeBtn),
            false, 0
        );

        // 1000em w/h → OverlayStack clamps to viewport → full-screen modal.
        // The inner surface fills the area below the header via a wrapper
        // Flex with flexGrow=1. We can't call innerSurface.withFlexGrow(1)
        // directly because that would create a NEW Component identity and
        // every inner sidecar (positions, children, ports) is keyed by the
        // original identity — the layout walk of a clone'd identity would
        // see an empty surface.
        Component.Flex innerFrame = new Component.Flex(
            null, null, Em.ZERO, CONTENT_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(innerSurface), false, 1
        );
        Component.Flex overlayRoot = new Component.Flex(
            Em.of(1000f), Em.of(1000f), Em.of(0.4f), CONTENT_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(header, innerFrame), false, 0
        );

        // Only detach the header (close button's onClick handler + title
        // text). Do NOT detach overlayRoot or innerFrame because
        // Components.detach walks the subtree pre-order and would clear
        // innerSurface's sidecars (port declarations, GraphSurfaceChildren,
        // Connections, etc.) — exactly the state we need to survive until
        // outerNode itself is deleted. overlayRoot and innerFrame are
        // plain Flexes with no sidecar entries; letting them GC silently
        // is fine.
        Runnable restore = () -> Components.detach(header);
        Handlers.onClick(closeBtn, OverlayStack::pop);
        OverlayStack.push(new OverlayStack.Overlay(overlayRoot, Anchor.CENTER, true, restore));
    }

    /**
     * Stress pane — deliberately exercises configurations that have
     * caught bugs in the past. Every feature in the framework that
     * could plausibly fail when used in an "unexpected" layout shows
     * up here in at least one such layout. Catch what the showcase
     * tabs miss.
     *
     * <p>Today's coverage:
     * <ul>
     *   <li>Three small point-cloud viewports side-by-side in a flex
     *       row, none of them centred on the framebuffer — catches
     *       NDC-mapping bugs that only surface for off-centre rects.</li>
     *   <li>A point-cloud viewport inside a {@link Component.Scroll} —
     *       catches scissor-stack interactions with the renderer's own
     *       scissor push.</li>
     *   <li>An inline mix of icons and text inside a single flex row,
     *       at varying sizes — catches MSDF atlas-swap bugs that only
     *       manifest when two font groups interleave within a frame.</li>
     * </ul>
     *
     * <p>Run with {@code -Ddasum.debug.gl=true} to surface GL-state
     * leaks from any custom renderer across this tab (the framework
     * wraps every {@code CustomRenderers.Renderer} call with a
     * before/after snapshot when the flag is set).
     */
    private static Component buildStressPane() {
        // Three small viewports side by side. Each gets its own data so
        // they look visually distinct — if any of them renders empty
        // or the wrong content, the bug is layout-position-dependent.
        Component triple = new Component.Flex(
            null, Em.AUTO, Em.of(0.5f), CONTENT_BG,
            Direction.ROW, JustifyContent.START, AlignItems.START, Em.of(0.5f),
            List.of(
                buildSmallPointCloud(7,  CameraSpec.defaultPerspective(),                            "Cube"),
                buildSmallPointCloud(13, CameraSpec.defaultPerspective().withYaw((float) Math.toRadians(60)),  "Rotated"),
                buildSmallPointCloud(21, CameraSpec.defaultOrtho(),                                  "Ortho")
            ),
            false, 0
        );

        // Mixed icon + text in one row, varied sizes. Atlas-swap is hit
        // multiple times per frame here.
        Component iconRowSmall = new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f,0f,0f,0f),
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            List.of(
                Icon.of(Icons.SEARCH,   Em.of(1.0f), LABEL_FG),
                new Component.Text("Search",   Em.of(0.95f), LABEL_FG),
                Icon.of(Icons.SETTINGS, Em.of(1.0f), LABEL_FG),
                new Component.Text("Settings", Em.of(0.95f), LABEL_FG),
                Icon.of(Icons.HOME,     Em.of(1.0f), LABEL_FG),
                new Component.Text("Home",     Em.of(0.95f), LABEL_FG),
                Icon.of(Icons.FOLDER,   Em.of(1.0f), LABEL_FG),
                new Component.Text("Folder",   Em.of(0.95f), LABEL_FG)
            ),
            false, 0
        );

        Component iconRowLarge = new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f,0f,0f,0f),
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(
                Icon.of(Icons.PLAY,    Em.of(1.6f), LABEL_FG),
                new Component.Text("Play",    Em.of(1.3f), LABEL_FG),
                Icon.of(Icons.PAUSE,   Em.of(1.6f), LABEL_FG),
                new Component.Text("Pause",   Em.of(1.3f), LABEL_FG),
                Icon.of(Icons.SAVE,    Em.of(1.6f), LABEL_FG),
                new Component.Text("Save",    Em.of(1.3f), LABEL_FG)
            ),
            false, 0
        );

        // Point cloud inside a scroll. Surrounding text content forces
        // the cloud's rect to be somewhere mid-viewport, and the scroll's
        // own scissor wraps the renderer's scissor push.
        Component scrollContent = new Component.Flex(
            null, Em.AUTO, Em.of(1f), new Color(0f,0f,0f,0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.8f),
            List.of(
                new Component.Text("Pre-cloud text — fills space above the embedded viewport so its rect isn't framebuffer-aligned.",
                    Em.of(0.95f), LABEL_FG).withWrapWidth(Em.of(40f)),
                buildSmallPointCloud(99, CameraSpec.defaultPerspective(), "In a Scroll"),
                new Component.Text("Post-cloud text — if the viewport leaked scissor or viewport state, this paragraph would render wrong (clipped, in the wrong place, or invisible).",
                    Em.of(0.95f), LABEL_FG).withWrapWidth(Em.of(40f)),
                new Component.Text("Extra line A.", Em.of(0.95f), LABEL_FG),
                new Component.Text("Extra line B.", Em.of(0.95f), LABEL_FG),
                new Component.Text("Extra line C.", Em.of(0.95f), LABEL_FG),
                new Component.Text("Extra line D.", Em.of(0.95f), LABEL_FG)
            ),
            false, 0
        );
        Component scrollBlock = new Component.Scroll(
            null, Em.of(18f), Em.of(0.5f), new Color(0.06f, 0.08f, 0.11f, 1f),
            scrollContent, false, 0
        );

        Component column = new Component.Flex(
            null, Em.AUTO, Em.of(1.2f), CONTENT_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(1.2f),
            List.of(
                section("Three viewports, off-centre rects", triple),
                section("Layered scene — direct SceneSnapshot, mixed blend modes", buildBlendScene()),
                section("Surface probes — uniform vs iteration-weighted (Mandelbulb, same budget)", buildBulbProbeScene()),
                section("Image + text layers", new Component.Flex(
                    null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
                    Direction.ROW, JustifyContent.START, AlignItems.START, Em.of(0.5f),
                    List.of(buildImageTextScene(), buildBillboardScene()),
                    false, 0, true  // wrap: reflow cards to the viewport width
                )),
                section("Icon + text inline (small)",        iconRowSmall),
                section("Icon + text inline (large)",        iconRowLarge),
                section("Viewport inside Scroll",            scrollBlock)
            ),
            false, 0
        );

        return new Component.Scroll(
            null, null, Em.ZERO, CONTENT_BG, column, false, 1
        );
    }

    /**
     * Plotting toolkit showcase ({@code dasum-vis.plot}): a multi-series
     * line+curve chart, a 2D complex domain-colouring map, and a 3D complex
     * volume sliced live by a Slider. All three are ordinary SceneViews fed
     * through {@link PlotView}/{@link FieldSlicePlot}; PAN_ZOOM_2D is enabled
     * so dragging pans and scrolling zooms (and the chart re-ticks on zoom).
     */
    private static Component buildPlotsPane() {
        PlotStyle style = PlotStyle.defaults();
        Color plotBg = new Color(0.04f, 0.05f, 0.08f, 1f);

        // ---- 1. Line + curve chart -------------------------------------
        Component.SceneView chartView = new Component.SceneView(
            Em.of(22f), Em.of(12f), Em.ZERO, plotBg, true, 0);
        int n = 40;
        double[] xs = new double[n], sine = new double[n], noisy = new double[n];
        java.util.Random rng = new java.util.Random(7);
        for (int i = 0; i < n; i++) {
            xs[i] = i * (2 * Math.PI / (n - 1));
            sine[i] = Math.sin(xs[i]);
            noisy[i] = 0.6 * Math.cos(xs[i]) + (rng.nextDouble() - 0.5) * 0.3;
        }
        List<Series> series = List.of(
            Series.curve(xs, sine, new Color(0.40f, 0.80f, 1.0f, 1f)).withThickness(0.06f),
            Series.line(xs, noisy, new Color(1.0f, 0.65f, 0.30f, 1f))
        );
        PlotView chart = new PlotView(chartView);
        chart.showLinePlot(LinePlot.autoFrame(0f, 0f, 10f, 5.5f, series), series, style);
        SceneStates.setInteraction(chartView, InteractionSpec.panZoom2d());

        // ---- 2. 2D complex domain-colouring map ------------------------
        Component.SceneView mapView = new Component.SceneView(
            Em.of(13f), Em.of(13f), Em.ZERO, plotBg, true, 0);
        int res = 256;
        ComplexField2D fz = ComplexField2D.of(res, res, (px, py, out) -> {
            // z over [-1.6, 1.6]^2 (top row = +imag), f(z) = z^2 - 1
            double zr = -1.6 + 3.2 * px / (res - 1);
            double zi =  1.6 - 3.2 * py / (res - 1);
            double fr = zr * zr - zi * zi - 1.0;
            double fi = 2 * zr * zi;
            out[0] = (float) fr; out[1] = (float) fi;
        });
        PlotFrame mapFrame = new PlotFrame(0f, 0f, 6f, 6f, Axis.linear(-1.6, 1.6), Axis.linear(-1.6, 1.6));
        PlotView fieldMap = new PlotView(mapView);
        fieldMap.showFieldMap(mapFrame, fz, ComplexColorMaps.domainColoring(), style);
        SceneStates.setInteraction(mapView, InteractionSpec.panZoom2d());

        // ---- 3. 3D complex volume + slice slider -----------------------
        Component.SceneView sliceView = new Component.SceneView(
            Em.of(13f), Em.of(13f), Em.ZERO, plotBg, true, 0);
        int vr = 96, vd = 32;
        ComplexField3D volume = ComplexField3D.Array.from(vr, vr, vd, (x, y, z, out) -> {
            double cx = -1.5 + 3.0 * x / (vr - 1);
            double cy =  1.5 - 3.0 * y / (vr - 1);
            double phase = z * 0.35;                 // slice scrubs the wave phase
            double env = Math.exp(-(cx * cx + cy * cy) * 0.7);
            double k = 3.0;
            out[0] = (float) (env * Math.cos(k * cx + phase));
            out[1] = (float) (env * Math.sin(k * cy + phase));
        });
        PlotFrame sliceFrame = new PlotFrame(0f, 0f, 6f, 6f, Axis.linear(-1.5, 1.5), Axis.linear(-1.5, 1.5));
        Property<Float> sliceIdx = new Property<>(0f);
        Component.Slider sliceSlider = new Component.Slider(
            Direction.ROW, Em.of(13f), Em.of(1.0f), Em.of(0.6f),
            SL_TRACK, SL_FILL, SL_THUMB, sliceIdx, 0f, vd - 1);
        Tooltips.set(sliceSlider, "Scrub the Z slice through the 3D complex volume");
        FieldSlicePlot slicePlot = new FieldSlicePlot(
            sliceView, sliceFrame, volume, ComplexField3D.Axis3.Z,
            ComplexColorMaps.domainColoring(), style, sliceSlider, null);
        slicePlot.bind();

        Component sliceCard = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.3f), new Color(0.12f, 0.14f, 0.18f, 1f),
            Direction.COLUMN, JustifyContent.START, AlignItems.CENTER, Em.of(0.4f),
            List.of(sliceView, inlineRow(List.of(
                new Component.Text("Slice:", Em.of(0.95f), LABEL_FG), sliceSlider))),
            false, 0);

        Component maps = new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.ROW, JustifyContent.START, AlignItems.START, Em.of(0.8f),
            List.of(
                section("2D complex map — domain colouring of f(z) = z² − 1", mapView),
                section("3D complex volume — Z-slice driven by the slider", sliceCard)),
            false, 0, true);

        Component column = new Component.Flex(
            null, Em.AUTO, Em.of(1.2f), CONTENT_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(1.2f),
            List.of(
                section("Line + curve chart — auto-ranged axes, nice-number ticks, smooth curve", chartView),
                maps),
            false, 0);

        return new Component.Scroll(null, null, Em.ZERO, CONTENT_BG, column, false, 1);
    }

    /**
     * One small point-cloud viewport, with a seeded random point cloud
     * so each call produces visually distinct content (helps spot
     * cross-renders / wrong-data bugs at a glance). Used by the Stress
     * tab to build several configurations in one shot.
     */
    private static Component buildSmallPointCloud(int seed, CameraSpec cam, String labelText) {
        Color cloudBg = new Color(0.04f, 0.05f, 0.08f, 1f);
        Component.SceneView viewport = new Component.SceneView(
            Em.of(10f), Em.of(7f), Em.ZERO, cloudBg, true, 0
        );

        final int N = 800;
        float[] positions = new float[N * 3];
        float[] colors    = new float[N * 3];
        java.util.Random rng = new java.util.Random(seed);
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
        PointCloudStates.publish(viewport, new PointCloudSnapshot(3, N, positions, colors, null, null));
        PointCloudStates.setCamera(viewport, cam);

        Component label = new Component.Text(labelText, Em.of(0.85f), LABEL_FG);
        return new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.3f), new Color(0.12f, 0.14f, 0.18f, 1f),
            Direction.COLUMN, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            List.of(label, viewport), false, 0
        );
    }

    /**
     * Direct {@link SceneSnapshot} demo — exercises the layered scene API
     * without the {@code PointCloudStates} compat path: two translucent
     * ALPHA quads (overlap blends), a ring of ADDITIVE points over them
     * (overlaps brighten), and a line frame on top. The stack is
     * order-sensitive — swapping the triangle and point layers visibly
     * changes the overlap colors. 2D ortho camera with PAN_ZOOM_2D
     * interaction, so scroll-zoom anchors at the cursor.
     */
    private static Component buildBlendScene() {
        Component.SceneView viewport = new Component.SceneView(
            Em.of(14f), Em.of(8f), Em.ZERO, new Color(0.04f, 0.05f, 0.08f, 1f), true, 0
        );

        // Two overlapping quads, 2 triangles each: red on the left, blue
        // on the right, overlapping in the middle.
        float[] tris = new float[2 * 2 * 9];
        float[] triCols = new float[tris.length];
        fillQuad(tris, triCols, 0,  -1.6f, -1.0f, 0.4f, 1.0f, 0.95f, 0.25f, 0.25f);
        fillQuad(tris, triCols, 18, -0.4f, -1.0f, 1.6f, 1.0f, 0.25f, 0.45f, 0.95f);

        // Ring of additive points crossing both quads.
        int ringN = 28;
        float[] pts = new float[ringN * 3];
        float[] ptCols = new float[ringN * 3];
        for (int i = 0; i < ringN; i++) {
            double a = (Math.PI * 2 * i) / ringN;
            pts[i*3    ] = (float) (Math.cos(a) * 1.25);
            pts[i*3 + 1] = (float) (Math.sin(a) * 0.85);
            pts[i*3 + 2] = 0f;
            ptCols[i*3    ] = 0.9f;
            ptCols[i*3 + 1] = 0.8f;
            ptCols[i*3 + 2] = 0.3f;
        }

        // Line frame on top.
        float fx0 = -1.8f, fy0 = -1.2f, fx1 = 1.8f, fy1 = 1.2f;
        float[] frame = {
            fx0, fy0, 0f,  fx1, fy0, 0f,
            fx1, fy0, 0f,  fx1, fy1, 0f,
            fx1, fy1, 0f,  fx0, fy1, 0f,
            fx0, fy1, 0f,  fx0, fy0, 0f,
        };

        SceneStates.publish(viewport, SceneSnapshot.of(
            new TriangleLayer(tris, triCols).withOpacity(0.6f),
            new PointLayer(pts, ptCols).withBlend(BlendMode.ADDITIVE).withDefaultSize(8f),
            new LineLayer(frame, null)
        ));
        SceneStates.setCamera(viewport, CameraSpec.defaultOrtho());
        SceneStates.setInteraction(viewport, InteractionSpec.panZoom2d());

        Component label = new Component.Text(
            "ALPHA quads under ADDITIVE points under lines — drag pans, scroll zooms at cursor",
            Em.of(0.85f), LABEL_FG);
        return new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.3f), new Color(0.12f, 0.14f, 0.18f, 1f),
            Direction.COLUMN, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            List.of(label, viewport), false, 0
        );
    }

    /**
     * Image + text layer demo (2D): a CPU-iterated Mandelbrot as a smooth
     * ImageLayer (asymmetric content — an upside-down or mirrored render
     * is immediately obvious), a small NEAREST-filtered heatmap whose
     * texel edges must stay crisp, and non-billboard TextLayer captions.
     * PAN_ZOOM_2D, so scroll-zoom anchors at the cursor — a preview of
     * the FractalView interaction.
     */
    private static Component buildImageTextScene() {
        Component.SceneView viewport = new Component.SceneView(
            Em.of(14f), Em.of(8f), Em.ZERO, new Color(0.04f, 0.05f, 0.08f, 1f), true, 0
        );

        // Mandelbrot, 256x192, escape-time coloring, top row first.
        int mw = 256, mh = 192;
        byte[] mandel = new byte[mw * mh * 4];
        double reMin = -2.4, reMax = 0.8, imMax = 1.2, imMin = -1.2;
        int maxIter = 64;
        for (int py = 0; py < mh; py++) {
            double ci = imMax - (imMax - imMin) * py / (mh - 1); // top row = imMax
            for (int px = 0; px < mw; px++) {
                double cr = reMin + (reMax - reMin) * px / (mw - 1);
                double zr = 0, zi = 0;
                int it = 0;
                while (it < maxIter && zr * zr + zi * zi <= 4.0) {
                    double t = zr * zr - zi * zi + cr;
                    zi = 2 * zr * zi + ci;
                    zr = t;
                    it++;
                }
                int off = (py * mw + px) * 4;
                if (it >= maxIter) {
                    mandel[off] = 0; mandel[off + 1] = 0; mandel[off + 2] = 0;
                } else {
                    float t = (float) it / maxIter;
                    mandel[off    ] = (byte) (Math.min(1f, t * 2.5f) * 255);        // ramps fast
                    mandel[off + 1] = (byte) (Math.min(1f, t * 1.2f) * 200);
                    mandel[off + 2] = (byte) ((1f - t) * 180 + 60);
                }
                mandel[off + 3] = (byte) 255;
            }
        }

        // Heatmap, 8x6, smooth scalar field -> 3-stop colormap, NEAREST.
        int hw = 8, hh = 6;
        byte[] heat = new byte[hw * hh * 4];
        for (int py = 0; py < hh; py++) {
            for (int px = 0; px < hw; px++) {
                float v = (float) (0.5 + 0.5 * Math.sin(px * 0.9) * Math.cos(py * 1.1));
                int off = (py * hw + px) * 4;
                // dark blue -> magenta -> yellow
                float r = Math.min(1f, v * 2f);
                float g = Math.max(0f, v * 2f - 1f);
                float b = 1f - v;
                heat[off    ] = (byte) (r * 255);
                heat[off + 1] = (byte) (g * 255);
                heat[off + 2] = (byte) (b * 255);
                heat[off + 3] = (byte) 255;
            }
        }

        Color captionFg = new Color(0.85f, 0.88f, 0.95f, 1f);
        SceneStates.publish(viewport, SceneSnapshot.of(
            ImageLayer.rect(-3.2f, -1.0f, -0.2f, 1.0f, 0f, mw, mh, mandel),
            ImageLayer.rect(0.2f, -1.0f, 3.2f, 1.0f, 0f, hw, hh, heat).withSmooth(false),
            new TextLayer("Mandelbrot (smooth)", new Vec3(-1.7f, -1.5f, 0f), 0.24f, captionFg),
            new TextLayer("Heatmap (nearest)",   new Vec3(1.7f, -1.5f, 0f), 0.24f, captionFg)
        ));
        SceneStates.setCamera(viewport, CameraSpec.defaultOrtho());
        SceneStates.setInteraction(viewport, InteractionSpec.panZoom2d());

        Component label = new Component.Text(
            "ImageLayer + TextLayer (2D) — scroll zooms at cursor", Em.of(0.85f), LABEL_FG);
        return new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.3f), new Color(0.12f, 0.14f, 0.18f, 1f),
            Direction.COLUMN, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            List.of(label, viewport), false, 0
        );
    }

    /**
     * Billboard text demo (3D): a line-frame cube with TextLayer labels
     * on three corners. The labels are uploaded once and oriented in the
     * vertex shader — orbiting must keep them facing the camera without
     * any re-upload.
     */
    private static Component buildBillboardScene() {
        Component.SceneView viewport = new Component.SceneView(
            Em.of(12f), Em.of(8f), Em.ZERO, new Color(0.04f, 0.05f, 0.08f, 1f), true, 0
        );

        // 12 cube edges, +-1.
        float[] e = new float[12 * 6];
        int k = 0;
        float[][] c = {
            {-1,-1,-1}, {1,-1,-1}, {1,1,-1}, {-1,1,-1},
            {-1,-1, 1}, {1,-1, 1}, {1,1, 1}, {-1,1, 1},
        };
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},  // back face
            {4,5},{5,6},{6,7},{7,4},  // front face
            {0,4},{1,5},{2,6},{3,7},  // connectors
        };
        for (int[] ed : edges) {
            for (int v : ed) {
                e[k++] = c[v][0]; e[k++] = c[v][1]; e[k++] = c[v][2];
            }
        }

        Color labelFg = new Color(1.00f, 0.85f, 0.40f, 1f);
        SceneStates.publish(viewport, SceneSnapshot.of(
            new LineLayer(e, null),
            new TextLayer("(+1,+1,+1)", FontGroups.DEFAULT, new Vec3(1f, 1f, 1f), 0.22f,
                labelFg, TextLayer.HAlign.CENTER, true, BlendMode.ALPHA, 1f),
            new TextLayer("(-1,-1,-1)", FontGroups.DEFAULT, new Vec3(-1f, -1f, -1f), 0.22f,
                labelFg, TextLayer.HAlign.CENTER, true, BlendMode.ALPHA, 1f),
            new TextLayer("(+1,-1,+1)", FontGroups.DEFAULT, new Vec3(1f, -1f, 1f), 0.22f,
                labelFg, TextLayer.HAlign.CENTER, true, BlendMode.ALPHA, 1f)
        ));
        SceneStates.setCamera(viewport, CameraSpec.defaultPerspective());

        Component label = new Component.Text(
            "Billboard TextLayer (3D) — labels face the camera while orbiting", Em.of(0.85f), LABEL_FG);
        return new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.3f), new Color(0.12f, 0.14f, 0.18f, 1f),
            Direction.COLUMN, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            List.of(label, viewport), false, 0
        );
    }

    /**
     * VexelRay inspector tab ("Houdini-lite"): one orbit viewport plus a
     * control panel that swaps the model, the inspection view mode, and the
     * march step budget — so any render-pipeline experiment applies to any
     * model. Geometry/quality is per-layer (republished on change); the view
     * mode is the global {@link VexelRayView} read live by the renderer.
     */
    private enum VexelModel {
        SPHERE("Sphere", 3.0f), BOX("Box", 3.0f), TORUS("Torus", 3.2f),
        BLOBS("Blobs", 4.5f), MANDELBULB("Mandelbulb", 3.5f),
        CSG_MONUMENT("CSG Monument", 4.0f), ALIEN_TREE("Alien Tree", 3.4f);
        final String label; final float camDist;
        VexelModel(String label, float camDist) { this.label = label; this.camDist = camDist; }
    }

    private static VexelRayLayer vexelModel(VexelModel m, int maxSteps, int bulbIters, float escapeR, float iterLod) {
        return switch (m) {
            case SPHERE     -> VexelRayLayer.of(VexelRayLayer.Field.SPHERE).withMaxSteps(maxSteps);
            case BOX        -> VexelRayLayer.of(VexelRayLayer.Field.BOX).withMaxSteps(maxSteps);
            case TORUS      -> VexelRayLayer.of(VexelRayLayer.Field.TORUS).withMaxSteps(maxSteps);
            case BLOBS      -> VexelRayLayer.of(VexelRayLayer.Field.BLOBS)
                                   .withColor(new Color(0.45f, 0.9f, 0.6f, 1f)).withMaxSteps(maxSteps);
            // params: x=power, y=iterations, z=escape radius, w=iteration-LOD strength.
            case MANDELBULB -> VexelRayLayer.of(VexelRayLayer.Field.MANDELBULB)
                                   .withParams(new float[]{8f, bulbIters, escapeR, iterLod})
                                   .withColor(new Color(0.92f, 0.74f, 1.00f, 1f)).withMaxSteps(maxSteps);
            case CSG_MONUMENT -> VexelRayLayer.csgBoxes(monumentOps(), MONUMENT_ROUNDING)
                                   .withColor(MONUMENT_COLOR).withMaxSteps(maxSteps);
            case ALIEN_TREE -> VexelRayLayer.of(VexelRayLayer.Field.ALIEN_PLANT).withMaxSteps(maxSteps);
        };
    }

    private static Component buildVexelRayPane() {
        Component.SceneView viewport = new Component.SceneView(
            null, null, Em.ZERO, new Color(0.03f, 0.04f, 0.07f, 1f), true, 1
        );

        Property<VexelModel> model = new Property<>(VexelModel.CSG_MONUMENT);
        Property<Float> maxSteps = new Property<>(96f);
        Property<Float> bulbIters = new Property<>(10f);  // Mandelbulb only
        Property<Float> escapeR = new Property<>(2f);     // Mandelbulb only
        Property<Float> iterLod = new Property<>(0f);     // Mandelbulb only (0 = off)
        Property<VexelRayView> view = new Property<>(VexelRayView.LIT);

        Runnable republish = () -> {
            VexelModel m = model.get();
            SceneStates.publish(viewport, SceneSnapshot.of(
                vexelModel(m, Math.round(maxSteps.get()), Math.round(bulbIters.get()),
                           escapeR.get(), iterLod.get())));
            SceneStates.setCamera(viewport, CameraSpec.defaultPerspective().withDistance(m.camDist));
        };
        model.subscribe(v -> republish.run());
        maxSteps.subscribe(v -> republish.run());
        bulbIters.subscribe(v -> republish.run());
        escapeR.subscribe(v -> republish.run());
        iterLod.subscribe(v -> republish.run());
        view.subscribe(VexelRayView::set);
        republish.run(); // initial publish

        List<Component> modelRows = new java.util.ArrayList<>();
        for (VexelModel m : VexelModel.values()) modelRows.add(vexelRadioRow(model, m, m.label));

        List<Component> viewRows = new java.util.ArrayList<>();
        String[] viewLabels = {"Lit", "Normals", "AO", "Steps (cost)", "Escape iters", "Cost - escape", "Work (iters)"};
        VexelRayView[] views = VexelRayView.values();
        for (int i = 0; i < views.length; i++) viewRows.add(vexelRadioRow(view, views[i], viewLabels[i]));

        Component stepsSlider = new Component.Slider(
            Direction.ROW, Em.of(10f), Em.of(0.9f), Em.of(0.5f),
            SL_TRACK, SL_FILL, SL_THUMB, maxSteps, 16f, 256f);
        Component itersSlider = new Component.Slider(
            Direction.ROW, Em.of(10f), Em.of(0.9f), Em.of(0.5f),
            SL_TRACK, SL_FILL, SL_THUMB, bulbIters, 2f, 20f);
        Component escapeSlider = new Component.Slider(
            Direction.ROW, Em.of(10f), Em.of(0.9f), Em.of(0.5f),
            SL_TRACK, SL_FILL, SL_THUMB, escapeR, 1.2f, 12f);
        Component lodSlider = new Component.Slider(
            Direction.ROW, Em.of(10f), Em.of(0.9f), Em.of(0.5f),
            SL_TRACK, SL_FILL, SL_THUMB, iterLod, 0f, 1f);

        Component panelCol = new Component.Flex(
            null, Em.AUTO, Em.of(0.6f), new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.8f),
            List.of(
                new Component.Text("VexelRay Inspector", Em.of(1.1f), LABEL_FG),
                vexelGroup("Model", modelRows),
                vexelGroup("View", viewRows),
                vexelGroup("Max steps (16–256)", List.of(stepsSlider)),
                vexelGroup("Mandelbulb iters (2–20)", List.of(itersSlider)),
                vexelGroup("Mandelbulb escape R (1.2–12)", List.of(escapeSlider)),
                vexelGroup("Mandelbulb iter-LOD (0=off)", List.of(lodSlider))
            ), false, 0);

        Component panel = new Component.Scroll(
            Em.of(15f), null, Em.of(0.4f), new Color(0.10f, 0.12f, 0.16f, 1f), panelCol, false, 0);

        return new Component.Flex(
            null, null, Em.ZERO, CONTENT_BG,
            Direction.ROW, JustifyContent.START, AlignItems.STRETCH, Em.of(0.5f),
            List.of(panel, viewport), false, 1);
    }

    private static <T> Component vexelRadioRow(Property<T> prop, T value, String label) {
        return inlineRow(List.of(
            new Component.Radio<>(Em.of(1.1f), RB_BOX, RB_DOT, prop, value),
            new Component.Text(label, Em.of(0.9f), LABEL_FG)));
    }

    private static Component vexelGroup(String heading, List<Component> rows) {
        List<Component> kids = new java.util.ArrayList<>();
        kids.add(new Component.Text(heading, Em.of(0.95f), new Color(0.65f, 0.70f, 0.85f, 0.9f)));
        kids.addAll(rows);
        return new Component.Flex(
            null, Em.AUTO, Em.of(0.2f), new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.25f),
            kids, false, 0);
    }

    /** Arch-monument CSG parameters — the inspector's CSG_MONUMENT model. */
    private static final float MONUMENT_ROUNDING = 0.10f;
    private static final Color MONUMENT_COLOR = new Color(0.88f, 0.80f, 0.66f, 1f);

    /** The arch-monument op list — shared by the raymarched and splat cards. */
    private static List<CsgBox> monumentOps() {
        return List.of(
            // Base slab; pillars join it with small-k smooth unions —
            // hard min() keeps concave junction creases SHARP (outward
            // rounding only rounds convex edges), and sharp concave
            // creases produce AO piping. A small fillet is the
            // modeling-correct fix, and real monuments have them anyway.
            new CsgBox(CsgBox.Op.UNION, new Vec3(0f, -0.90f, 0f), new Vec3(0.95f, 0.12f, 0.50f)),
            new CsgBox(CsgBox.Op.SMOOTH_UNION, new Vec3(-0.55f, -0.25f, 0f), new Vec3(0.16f, 0.60f, 0.16f), 0.07f),
            new CsgBox(CsgBox.Op.SMOOTH_UNION, new Vec3(0.55f, -0.25f, 0f), new Vec3(0.16f, 0.60f, 0.16f), 0.07f),
            // Lintel melts into the pillars (bigger blend radius).
            new CsgBox(CsgBox.Op.SMOOTH_UNION, new Vec3(0f, 0.42f, 0f), new Vec3(0.85f, 0.14f, 0.20f), 0.16f),
            // Window slot through the lintel (slightly softened subtract).
            new CsgBox(CsgBox.Op.SMOOTH_SUBTRACT, new Vec3(0f, 0.42f, 0f), new Vec3(0.30f, 0.06f, 0.60f), 0.05f),
            // Soft scoop carved out of the base front.
            new CsgBox(CsgBox.Op.SMOOTH_SUBTRACT, new Vec3(0f, -0.80f, 0.55f), new Vec3(0.45f, 0.16f, 0.25f), 0.12f),
            // Capstone, filleted onto the lintel.
            new CsgBox(CsgBox.Op.SMOOTH_UNION, new Vec3(0f, 0.70f, 0f), new Vec3(0.13f, 0.13f, 0.13f), 0.06f)
        );
    }

    /**
     * Experiment A — iteration-weighted probe placement. Sample the
     * Mandelbulb surface once (CPU MandelbulbField), then draw the SAME
     * probe budget two ways: uniform random placement vs. placement
     * weighted by the field's intrinsic escape-iteration count. The
     * weighted cloud concentrates probes on the high-iteration filigree
     * and thins them on the smooth lobes — and because the iteration count
     * is view-INDEPENDENT, the placement stays optimal as you orbit (unlike
     * a step-count/cost basis, which would thrash on camera motion). Splats
     * are coloured by complexity so the allocation is visible. Both clouds
     * are baked once at startup.
     */
    private static Component buildBulbProbeScene() {
        MandelbulbField bulb = new MandelbulbField(8f, 14);
        float s = 1.3f;
        SurfaceSampler.Result surf = SurfaceSampler.sample(
            bulb, new Vec3(-s, -s, -s), new Vec3(s, s, s), 110, 0.6f, 6, 0.18f);
        int n = surf.count();
        float[] pos = surf.positions();
        float[] comp = new float[n];
        double sumW = 0;
        final float EXP = 2.0f;          // sharpen the density bias toward detail
        for (int i = 0; i < n; i++) {
            comp[i] = bulb.complexityAt(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
            sumW += Math.pow(comp[i], EXP);
        }
        float keep = 0.30f;              // shared probe budget (fraction of base samples)
        float scale = (float) ((keep * n) / Math.max(sumW, 1e-6));

        float[] uniProb = new float[n];
        float[] wgtProb = new float[n];
        for (int i = 0; i < n; i++) {
            uniProb[i] = keep;
            wgtProb[i] = Math.min(1f, (float) Math.pow(comp[i], EXP) * scale);
        }

        Component uni = bulbProbeCard("Uniform placement", pos, comp, n, uniProb);
        Component wgt = bulbProbeCard("Iteration-weighted (same budget)", pos, comp, n, wgtProb);
        return new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.ROW, JustifyContent.START, AlignItems.START, Em.of(0.5f),
            List.of(uni, wgt), false, 0, true);
    }

    /** Keep points by per-point probability, colour by complexity, splat them in a labelled card. */
    private static Component bulbProbeCard(String caption, float[] pos, float[] comp, int n, float[] prob) {
        int kept = 0;
        for (int i = 0; i < n; i++) if (hash01(i) < prob[i]) kept++;
        float[] kpos = new float[kept * 3];
        float[] kcol = new float[kept * 3];
        int w = 0;
        for (int i = 0; i < n; i++) {
            if (hash01(i) >= prob[i]) continue;
            kpos[w * 3] = pos[i * 3]; kpos[w * 3 + 1] = pos[i * 3 + 1]; kpos[w * 3 + 2] = pos[i * 3 + 2];
            heat(comp[i], kcol, w * 3);
            w++;
        }
        System.out.println("Bulb probes — " + caption + ": " + kept + " / " + n);

        Component.SceneView viewport = new Component.SceneView(
            Em.of(13f), Em.of(9f), Em.ZERO, new Color(0.03f, 0.04f, 0.07f, 1f), true, 0);
        SceneStates.publish(viewport, SceneSnapshot.of(
            new PointLayer(kpos, kcol, null, 5f, BlendMode.OPAQUE, 1f)));
        SceneStates.setCamera(viewport, CameraSpec.defaultPerspective().withDistance(3.6f));

        return new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.3f), new Color(0.12f, 0.14f, 0.18f, 1f),
            Direction.COLUMN, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            List.of(new Component.Text(caption, Em.of(0.85f), LABEL_FG), viewport), false, 0);
    }

    /** Blue→red complexity ramp (matches the shader's heat()). */
    private static void heat(float t, float[] out, int o) {
        t = Math.max(0f, Math.min(1f, t));
        out[o]     = Math.max(0f, Math.min(1f, t * 2f));
        out[o + 1] = Math.max(0f, Math.min(1f, 1f - Math.abs(t - 0.5f) * 2f));
        out[o + 2] = Math.max(0f, Math.min(1f, 1f - t * 2f));
    }

    /** Deterministic per-index [0,1) hash, so the bake is reproducible. */
    private static float hash01(int i) {
        int x = i * 374761393 + 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x >>> 8) & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Append an axis-aligned quad as two triangles at {@code off} in {@code verts}/{@code cols}. */
    private static void fillQuad(float[] verts, float[] cols, int off,
                                 float x0, float y0, float x1, float y1,
                                 float r, float g, float b) {
        float[] quad = {
            x0, y0, 0f,  x1, y0, 0f,  x1, y1, 0f,
            x0, y0, 0f,  x1, y1, 0f,  x0, y1, 0f,
        };
        System.arraycopy(quad, 0, verts, off, quad.length);
        for (int v = 0; v < 6; v++) {
            cols[off + v*3    ] = r;
            cols[off + v*3 + 1] = g;
            cols[off + v*3 + 2] = b;
        }
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

        // Navigation destination in a different tab — exercises cross-tab
        // switching plus scroll-into-view on arrival.
        NavId.tag(paragraphText, "text.editor");
        NavRegistry.register("text.editor", "Editable text");

        return new Component.Scroll(
            null, null, Em.of(1f), CONTENT_BG,
            buildStyledColumn(paragraphText), false, 1
        );
    }

    /**
     * Column wrapping the existing paragraph (kept first) and a "Styled text"
     * subsection that exercises {@link TextStyleStates}: a read-only label
     * with static foreground + background ranges, a multi-line block showing
     * {@code wrapLineEndings} extend-to-edge fills, and an editable Text with
     * a content-change listener that re-highlights {@code TODO} / {@code FIXME}
     * tokens live as the user types. Demonstrates both the static-publisher
     * and incremental-publisher API patterns; wrap-aware bg rendering is
     * visible on the editable input when the user types past the wrap point.
     */
    private static Component buildStyledColumn(Component.Text paragraphText) {
        // Static-styled label — one Text, both fg + bg ranges set once.
        String diag = "error: undefined symbol 'foo' in main.c:42";
        Color red    = new Color(1.00f, 0.45f, 0.45f, 1f);
        Color yellow = new Color(1.00f, 0.85f, 0.35f, 1f);
        Color cyan   = new Color(0.45f, 0.85f, 1.00f, 1f);
        Color errBg  = new Color(0.50f, 0.10f, 0.10f, 0.55f);
        Color fooBg  = new Color(0.45f, 0.45f, 0.10f, 0.55f);

        Component.Text staticStyled = new Component.Text(
            diag, FontGroups.DEFAULT, Em.of(1.1f), BODY_TEXT,
            null, null, Em.of(0.6f),
            null, false,
            true, true, false, false, 0  // interactive + selectable, NOT editable
        );
        int errStart  = 0;
        int errEnd    = "error:".length();
        int fooStart  = diag.indexOf("'foo'");
        int fooEnd    = fooStart + "'foo'".length();
        int fileStart = diag.indexOf("main.c:42");
        int fileEnd   = fileStart + "main.c:42".length();
        TextStyleStates.setForeground(staticStyled, List.of(
            new TextStyle(errStart,  errEnd,  red),
            new TextStyle(fooStart,  fooEnd,  yellow),
            new TextStyle(fileStart, fileEnd, cyan)
        ));
        TextStyleStates.setBackground(staticStyled, List.of(
            new TextStyle(errStart, errEnd, errBg),
            new TextStyle(fooStart, fooEnd, fooBg)
        ));

        // Glyph effects — outline (dilated underlay) and weight (SDF edge
        // shift) ride on foreground ranges. A null range color keeps the
        // Text's default tint, so weight-only spans don't recolor.
        String fx = "Outlined words and bold words and lighter words coexist.";
        Component.Text fxStyled = new Component.Text(
            fx, FontGroups.DEFAULT, Em.of(1.1f), BODY_TEXT,
            null, null, Em.of(0.6f),
            null, false,
            true, true, false, false, 0  // interactive + selectable, NOT editable
        );
        int outStart   = fx.indexOf("Outlined words");
        int outEnd     = outStart + "Outlined words".length();
        int boldStart  = fx.indexOf("bold words");
        int boldEnd    = boldStart + "bold words".length();
        int lightStart = fx.indexOf("lighter words");
        int lightEnd   = lightStart + "lighter words".length();
        TextStyleStates.setForeground(fxStyled, List.of(
            new TextStyle(outStart, outEnd, yellow)
                .withOutline(new Color(0.15f, 0.35f, 0.70f, 1f), Em.of(0.06f)),
            new TextStyle(boldStart, boldEnd, null).withWeight(Em.of(0.04f)),
            new TextStyle(lightStart, lightEnd, null).withWeight(Em.of(-0.03f))
        ));

        // Wrapped-ending spans — a multi-line bg range with wrapLineEndings
        // fills each continued line to the text-area right edge (diff-view
        // look), including the empty middle line; the default range on the
        // last line stops at the glyph for contrast.
        String block =
            "Removed block — wrapLineEndings=true fills to the edge:\n" +
            "    int unused = compute();\n" +
            "\n" +
            "    cleanup(unused);\n" +
            "A default span on this line stops at the last glyph.";
        Component.Text blockStyled = new Component.Text(
            block, FontGroups.DEFAULT, Em.of(1.1f), BODY_TEXT,
            null, null, Em.of(0.6f),
            null, false,
            true, true, false, false, 0  // interactive + selectable, NOT editable
        );
        Color removedBg = new Color(0.45f, 0.12f, 0.12f, 0.55f);
        int blockStart = block.indexOf("    int unused");
        int blockEnd   = block.indexOf("A default");  // ends just past cleanup line's '\n'
        int defStart   = block.indexOf("default span");
        int defEnd     = defStart + "default span".length();
        TextStyleStates.setBackground(blockStyled, List.of(
            new TextStyle(blockStart, blockEnd, removedBg, true),
            new TextStyle(defStart, defEnd, fooBg)
        ));

        // Live-highlight editable Text — content-change listener re-publishes
        // ranges as the user types. Demonstrates the canonical
        // "recompute-from-scratch-per-content-change" pattern; no RMW races
        // possible because there's exactly one publisher (this listener).
        String initial =
            "Type here. Highlights update as you type.\n" +
            "Try inserting TODO or FIXME — they get tagged.\n" +
            "Long lines wrap, and background fills follow the wrap correctly.";
        Component.Text liveStyled = new Component.Text(
            initial, FontGroups.DEFAULT, Em.of(1.1f), BODY_TEXT,
            null, null, Em.of(0.6f),
            Em.of(36f), false,
            true, true, true, false, 0  // editable
        ).withLineNumbers(true);  // logical-line gutter; wrapped continuations unnumbered
        Color todoBg  = new Color(0.55f, 0.45f, 0.05f, 0.55f);
        Color fixmeFg = new Color(1.00f, 0.40f, 0.40f, 1f);
        TextStates.onContentChange(liveStyled, content -> {
            List<TextStyle> bg = new java.util.ArrayList<>();
            for (int i = content.indexOf("TODO"); i >= 0; i = content.indexOf("TODO", i + 4)) {
                bg.add(new TextStyle(i, i + 4, todoBg));
            }
            List<TextStyle> fg = new java.util.ArrayList<>();
            for (int i = content.indexOf("FIXME"); i >= 0; i = content.indexOf("FIXME", i + 5)) {
                fg.add(new TextStyle(i, i + 5, fixmeFg));
            }
            TextStyleStates.setBackground(liveStyled, bg);
            TextStyleStates.setForeground(liveStyled, fg);
        });
        // Initial publish so the demo opens with the marks already painted.
        {
            List<TextStyle> bg = new java.util.ArrayList<>();
            for (int i = initial.indexOf("TODO"); i >= 0; i = initial.indexOf("TODO", i + 4)) {
                bg.add(new TextStyle(i, i + 4, todoBg));
            }
            List<TextStyle> fg = new java.util.ArrayList<>();
            for (int i = initial.indexOf("FIXME"); i >= 0; i = initial.indexOf("FIXME", i + 5)) {
                fg.add(new TextStyle(i, i + 5, fixmeFg));
            }
            TextStyleStates.setBackground(liveStyled, bg);
            TextStyleStates.setForeground(liveStyled, fg);
        }

        Component header = new Component.Text(
            "Styled text — programmatic foreground + background ranges",
            Em.of(1.0f), LABEL_FG);

        Component liveHint = new Component.Text(
            "Editable — TODO gets a background, FIXME gets a foreground:",
            Em.of(0.95f), LABEL_FG);

        return new Component.Flex(
            null, null, Em.of(1f), new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(1.0f),
            List.of(paragraphText, header, staticStyled, fxStyled, blockStyled, liveHint, liveStyled),
            false, 0
        );
    }

    /**
     * Tables pane — two DataTables side by side. Left is a 1,000,000-row
     * synthetic read-only source (proves virtualized rendering); right is a
     * tiny in-memory editable source (proves selection / edit / copy-paste
     * / row+col insert+delete and the "multiple tables at once" requirement).
     */
    private static Component buildTablesPane() {
        Property<TableSelection> leftSel  = new Property<>(null);
        Property<TableSelection> rightSel = new Property<>(null);

        Component.DataTable leftTable = new Component.DataTable(
            null, null,
            new SyntheticDataTableSource(1_000_000L, 20),
            leftSel
        ).withFlexGrow(1);
        Component.DataTable rightTable = new Component.DataTable(
            null, null,
            new EditableDataTableSource(new String[][] {
                {"Alice", "30", "Engineer"},
                {"Bob",   "27", "Designer"},
                {"Cara",  "41", "PM"},
                {"Dan",   "35", "Engineer"},
                {"Eli",   "29", "QA"},
            }, new String[] {"Name", "Age", "Role"}),
            rightSel
        ).withFlexGrow(1);

        TableContextMenu.registerDefaults(leftTable);
        TableContextMenu.registerDefaults(rightTable);

        // Navigation destination in the Tables tab.
        NavId.tag(rightTable, "tables.editable");
        NavRegistry.register("tables.editable", "Editable table");

        Component leftLabel  = new Component.Text("Synthetic 1M × 20 (read-only)", Em.of(0.95f), LABEL_FG);
        Component rightLabel = new Component.Text("Editable 5 × 3 — try F2, Enter, Tab", Em.of(0.95f), LABEL_FG);

        Component leftCol = new Component.Flex(
            null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.4f),
            List.of(leftLabel, leftTable), false, 1
        );
        Component rightCol = new Component.Flex(
            null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.4f),
            List.of(rightLabel, rightTable), false, 1
        );

        return new Component.Flex(
            null, null, Em.of(0.8f), CONTENT_BG,
            Direction.ROW, JustifyContent.START, AlignItems.STRETCH, Em.of(0.8f),
            List.of(leftCol, rightCol),
            false, 1
        );
    }

    /**
     * Simple synthetic source — formula-generated cell values, no storage.
     * Cheap {@code get} returns {@code "R<row>C<col>"} so 1M rows costs
     * O(visible window) per frame.
     */
    private static final class SyntheticDataTableSource implements DataTableSource {
        private final long rows;
        private final int  cols;
        SyntheticDataTableSource(long rows, int cols) { this.rows = rows; this.cols = cols; }
        @Override public int columnCount() { return cols; }
        @Override public String columnHeader(int col) { return "Col " + col; }
        @Override public Em columnWidth(int col) { return Em.of(7f); }
        @Override public java.util.OptionalLong rowCount() { return java.util.OptionalLong.of(rows); }
        @Override public String get(int row, int col) { return "R" + row + "C" + col; }
    }

    /**
     * Tiny mutable source backed by an {@code ArrayList<String[]>}. Validates
     * the "Age" column (col=1) as a non-negative integer; rejects other
     * inputs by returning {@code false} from {@link #trySet}.
     */
    private static final class EditableDataTableSource implements DataTableSource {
        private final java.util.List<String[]> data;
        private final String[] headers;
        EditableDataTableSource(String[][] initial, String[] headers) {
            this.headers = headers;
            this.data = new java.util.ArrayList<>(initial.length);
            for (String[] row : initial) this.data.add(row.clone());
        }
        @Override public int columnCount() { return headers.length; }
        @Override public String columnHeader(int col) { return headers[col]; }
        @Override public Em columnWidth(int col) { return Em.of(col == 0 ? 8f : col == 1 ? 5f : 10f); }
        @Override public java.util.OptionalLong rowCount() { return java.util.OptionalLong.of(data.size()); }
        @Override public String get(int row, int col) {
            if (row < 0 || row >= data.size()) return null;
            String[] r = data.get(row);
            return col < r.length ? r[col] : "";
        }
        @Override public boolean trySet(int row, int col, String value) {
            if (row < 0 || row >= data.size()) return false;
            if (col == 1 && !value.isEmpty()) {
                try { int n = Integer.parseInt(value); if (n < 0) return false; }
                catch (NumberFormatException ex) { return false; }
            }
            String[] r = data.get(row);
            if (col >= r.length) {
                String[] grown = new String[col + 1];
                System.arraycopy(r, 0, grown, 0, r.length);
                java.util.Arrays.fill(grown, r.length, col + 1, "");
                data.set(row, grown);
                r = grown;
            }
            r[col] = value;
            Invalidator.invalidate();
            return true;
        }
        @Override public void insertRowAbove(int row) {
            int idx = Math.max(0, Math.min(row, data.size()));
            String[] empty = new String[headers.length];
            java.util.Arrays.fill(empty, "");
            data.add(idx, empty);
            Invalidator.invalidate();
        }
        @Override public void deleteRows(int from, int toExclusive) {
            int lo = Math.max(0, from), hi = Math.min(data.size(), toExclusive);
            for (int i = hi - 1; i >= lo; i--) data.remove(i);
            Invalidator.invalidate();
        }
        @Override public boolean canInsertRows() { return true; }
        @Override public boolean canDeleteRows() { return true; }
    }

    // ---------- pane composition helpers ----------

    /**
     * Adversarial tooltip showcase. Exercises every corner the controller
     * is supposed to handle:
     * <ul>
     *   <li>Trigger toggle (Always / Alt / Ctrl / Shift) via a radio
     *       group — also covers "no key held" → tooltips never show under
     *       the mod variants.</li>
     *   <li>Edge-overflow flip: four buttons positioned at the corners of
     *       a wide row so the cursor lands near each viewport edge and
     *       the popup has to flip to the opposite side.</li>
     *   <li>Multiline / very-long text wrapping.</li>
     *   <li>Tooltip on a non-interactive {@link Component.Text} (the
     *       label has no {@code interactive()}; the controller's
     *       {@link sibarum.dasum.gui.core.layout.HitTest#testAny} surfaces
     *       it anyway).</li>
     *   <li>Self-detach button: clicking it removes itself via
     *       {@link Components#detach}, exercising the
     *       {@code Tooltips.clear} hook that calls into
     *       {@link TooltipController#onComponentDetached}.</li>
     *   <li>Mutate-tooltip button: clicking it changes the tooltip's
     *       registered text so the controller's "text changed → restart
     *       dwell" path runs even though the cursor is stationary.</li>
     * </ul>
     */
    private static Component buildTooltipShowcase() {
        // Trigger toggle — Property<TooltipTrigger> driving radios.
        Property<TooltipTrigger> triggerProp = new Property<>(TooltipController.getTrigger());
        triggerProp.subscribe(t -> {
            TooltipController.setTrigger(t);
            System.out.println("Tooltip trigger: " + t);
        });
        Component.Radio<TooltipTrigger> rAlways = new Component.Radio<>(
            Em.of(1.1f), RB_BOX, RB_DOT, triggerProp, TooltipTrigger.ALWAYS);
        Component.Radio<TooltipTrigger> rAlt = new Component.Radio<>(
            Em.of(1.1f), RB_BOX, RB_DOT, triggerProp, TooltipTrigger.MOD_ALT);
        Component.Radio<TooltipTrigger> rCtrl = new Component.Radio<>(
            Em.of(1.1f), RB_BOX, RB_DOT, triggerProp, TooltipTrigger.MOD_CTRL);
        Component.Radio<TooltipTrigger> rShift = new Component.Radio<>(
            Em.of(1.1f), RB_BOX, RB_DOT, triggerProp, TooltipTrigger.MOD_SHIFT);
        Tooltips.set(rAlways, "Tooltips show on hover (default)");
        Tooltips.set(rAlt,    "Tooltips show only while Alt is held");
        Tooltips.set(rCtrl,   "Tooltips show only while Ctrl is held");
        Tooltips.set(rShift,  "Tooltips show only while Shift is held");

        Component triggerRow = inlineRow(List.of(
            new Component.Text("Trigger:", Em.of(0.95f), LABEL_FG),
            rAlways, new Component.Text("Always", Em.of(0.95f), LABEL_FG),
            rAlt,    new Component.Text("Alt",    Em.of(0.95f), LABEL_FG),
            rCtrl,   new Component.Text("Ctrl",   Em.of(0.95f), LABEL_FG),
            rShift,  new Component.Text("Shift",  Em.of(0.95f), LABEL_FG)
        ));

        // Four buttons on a wide row to provoke viewport-edge flips.
        Component edgeTL = Themed.button("Edge ↖",  Em.of(7f), Variant.PRIMARY, 0);
        Component edgeTR = Themed.button("Edge ↗",  Em.of(7f), Variant.PRIMARY, 0);
        Component edgeBL = Themed.button("Edge ↙",  Em.of(7f), Variant.PRIMARY, 0);
        Component edgeBR = Themed.button("Edge ↘",  Em.of(7f), Variant.PRIMARY, 0);
        Tooltips.set(edgeTL, "Top-left corner button — pointer placement should flip to the bottom-right of the cursor automatically.");
        Tooltips.set(edgeTR, "Top-right corner button — popup should flip to the LEFT of the cursor when there's no room on the right.");
        Tooltips.set(edgeBL, "Bottom-left button — popup should flip ABOVE when there's no room below.");
        Tooltips.set(edgeBR, "Bottom-right button — popup should flip up-and-left when both edges are tight.");

        Component edgeRow = new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.ROW, JustifyContent.SPACE_BETWEEN, AlignItems.CENTER, Em.of(0.5f),
            List.of(edgeTL, edgeTR, edgeBL, edgeBR), false, 0
        );

        // Long / multi-line tooltip — exercise wrap + multi-line rendering.
        Component longBtn = Themed.button("Long tooltip", Em.of(9f), Variant.INFO, 0);
        Tooltips.set(longBtn,
            "This tooltip text is intentionally long to make sure the wrap-width " +
            "limit keeps the popup readable on narrow viewports.\n\n" +
            "It also has a hard newline in the middle to verify '\\n' is honoured " +
            "(not collapsed to a space) by the renderer.");

        // Tooltip on a non-interactive Text label — verifies HitTest.testAny
        // surfaces it even though Text.interactive() defaults to false.
        Component.Text label = new Component.Text("Hover this non-interactive label",
            Em.of(0.95f), BODY_TEXT);
        Tooltips.set(label, "Static labels can carry tooltips too — interactive() is irrelevant.");

        // Self-detaching button — exercises Tooltips.clear → TooltipController.onComponentDetached.
        // Wraps in a one-child Box that we DynamicChildren.add into, so a click can detach
        // the inner component without rebuilding the parent.
        Property<Boolean> showSelfDetach = new Property<>(true);
        Component selfDetachBtn = Themed.button("Detach me (clears tooltip)", Em.of(14f), Variant.WARNING, 0);
        Tooltips.set(selfDetachBtn, "Clicking removes this button via Components.detach — the tooltip should disappear instantly.");
        Component.Flex selfDetachSlot = new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(selfDetachBtn), false, 0
        );
        Handlers.onClick(selfDetachBtn, () -> {
            if (Boolean.TRUE.equals(showSelfDetach.get())) {
                DynamicChildren.remove(selfDetachSlot, selfDetachBtn);
                Components.detach(selfDetachBtn);
                showSelfDetach.set(false);
                System.out.println("Self-detach button removed; tooltip should be gone.");
            }
        });

        // Mutate-tooltip button — text changes on each click. Verifies the
        // controller's "text changed under stationary cursor → restart dwell"
        // path.
        Component mutateBtn = Themed.button("Click to mutate my tooltip", Em.of(14f), Variant.SUCCESS, 0);
        int[] mutateCounter = { 0 };
        Tooltips.set(mutateBtn, "Initial tooltip text — click to mutate (count=0)");
        Handlers.onClick(mutateBtn, () -> {
            mutateCounter[0]++;
            Tooltips.set(mutateBtn, "Tooltip mutated " + mutateCounter[0] + " times — keep hovering to see dwell restart");
        });

        return new Component.Flex(
            null, Em.AUTO, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.7f),
            List.of(triggerRow, edgeRow, longBtn, label, selfDetachSlot, mutateBtn),
            false, 0
        );
    }

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
            // Track modifier state for InputState consumers (e.g. the
            // TextInputController's char-input filter uses ctrlHeld()
            // to drop spurious space chars from Ctrl+Space chords).
            // Set on every callback — including key release — so the
            // state is accurate for char events firing right after.
            InputState.setMods(mods);
            // Notify the tooltip controller so a mod-key transition that
            // crosses the trigger threshold causes a re-resolve next
            // frame. No-op when trigger is ALWAYS.
            TooltipController.onModsChanged(mods);
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
                if (DataTableSelectionController.onCopy(window.handle())) return;
            }
            if (ctrl && key == 'X') {
                if (TextInputController.onCut(window.handle())) return;
                if (DataTableSelectionController.onCut(window.handle())) return;
            }
            if (ctrl && key == 'V') {
                if (TextInputController.onPaste(window.handle())) return;
                if (DataTableSelectionController.onPaste(window.handle())) return;
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
            // DataTable owns arrows / Tab / F2 / Enter / Esc / Delete when
            // a table is the active receiver. Runs AFTER TextInputController
            // so a focused editable Text still wins.
            if (DataTableSelectionController.onKey(key, mods, window.handle())) return;
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
            // Tooltip's lightweight per-move bookkeeping. The actual
            // hit-test + show/hide decision happens in
            // TooltipController.resolveBeforeRender at the top of the
            // render lambda, where we have the live LayoutResult.
            TooltipController.onCursorMove(x, y);
            ScrollbarController.onCursorMove(x, y);
            if (ScrollbarController.isDragging()) return;
            ConnectionDragController.onCursorMove(x, y);
            if (ConnectionDragController.isDragging()) return;
            GraphSurfaceController.onCursorMove(x, y);
            if (GraphSurfaceController.isDragging()) return;
            SceneViewController.onCursorMove(x, y);
            if (SceneViewController.isDragging()) return;
            DataTableSelectionController.onCursorMove(x, y);
            if (DataTableSelectionController.isDragging()) return;
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
            InputState.setMods(mods);
            TooltipController.onModsChanged(mods);
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
                if (SceneViewController.onMouseDown(HoverState.hovered(), InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    FocusState.set(HoverState.hovered());
                    return;
                }
                // DataTable cells: claim the press for selection / drag-extend.
                if (DataTableSelectionController.onMouseDown(InputState.mouseX(), InputState.mouseY(), shift)) {
                    pressTarget = null;
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
                boolean pointCloudDrag = SceneViewController.isDragging();
                boolean tableDrag = DataTableSelectionController.isDragging();
                ScrollbarController.onMouseUp();
                SliderController.onMouseUp();
                GraphSurfaceController.onMouseUp();
                ConnectionDragController.onMouseUp();
                SceneViewController.onMouseUp();
                DataTableSelectionController.onMouseUp();

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
                if (!scrollDrag && !sliderDrag && !canvasDrag && !connectionDrag && !pointCloudDrag && !tableDrag && pressTarget != null && released == pressTarget) {
                    Handlers.activate(pressTarget, dispatchRoot);
                }
                pressTarget = null;
            }
        });

        GlfwCallbacks.setCharListener((win, codepoint) -> {
            // Text input owns the focused editable Text. If nothing took the
            // codepoint, route to the active DataTable (so typing starts /
            // continues a cell edit).
            if (TextInputController.onCharInput(codepoint)) return;
            DataTableSelectionController.onCharInput(codepoint);
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
                TooltipController.hideAll();
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
                SceneViewController.cancelDrag();
                // Window lost OS focus. Clear hover and pending drag — the
                // mouseup likely happened in another window and we'll never
                // see it. Selection PERSISTS so users can alt-tab away to
                // a reference window and back without losing their selection.
                HoverState.clear();
                TextStates.clearAllHoverCarets();
                TooltipController.hideAll();
                InputState.setLeftButtonHeld(false);
            }
            Invalidator.invalidate();
        });

        GlfwCallbacks.setScrollListener((win, xOff, yOff) -> {
            LayoutResult lr = LatestLayout.result();
            Component layoutRoot = OverlayStack.activeInputRoot(LatestLayout.root());
            if (lr == null || layoutRoot == null) return;
            // A scene viewport eats the wheel (camera zoom) ONLY when it
            // holds focus — click it first. Otherwise a page-scroll whose
            // cursor merely passes over a viewport would hijack into a
            // zoom; requiring focus keeps casual scrolling on the page.
            Component pcHit = HitTest.test(layoutRoot, lr, (float) InputState.mouseX(), (float) InputState.mouseY());
            if (pcHit instanceof Component.SceneView && FocusState.focused() == pcHit
                    && SceneViewController.onScroll(pcHit, yOff)) return;

            boolean shift = Glfw.glfwGetKey(window.handle(), Glfw.GLFW_KEY_LEFT_SHIFT)  == Glfw.GLFW_PRESS
                        || Glfw.glfwGetKey(window.handle(), Glfw.GLFW_KEY_RIGHT_SHIFT) == Glfw.GLFW_PRESS;
            // DataTable gets second refusal — scroll over a table moves its
            // virtualized viewport rather than nested-container scroll.
            if (DataTableSelectionController.onScroll(
                    InputState.mouseX(), InputState.mouseY(), xOff, yOff, shift)) return;

            double dx, dy;
            if (shift) { dx = -yOff * WHEEL_PIXELS_PER_STEP; dy = 0; }
            else        { dx = -xOff * WHEEL_PIXELS_PER_STEP; dy = -yOff * WHEEL_PIXELS_PER_STEP; }
            // Walk the scroll chain innermost→outermost; the first
            // container that actually moves consumes the wheel. A nested
            // scroll bottomed-out at its limit returns false from
            // scrollByPx, so the event bubbles to its parent.
            java.util.List<Component.Scroll> chain =
                HitTest.findScrollChain(layoutRoot, lr, (float) InputState.mouseX(), (float) InputState.mouseY());
            for (Component.Scroll s : chain) {
                if (ScrollStates.of(s).scrollByPx((float) dx, (float) dy)) break;
            }
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
        CommandRegistry.register("node.add.subgraph", "Add Subgraph Node", () -> spawnNode(SUBGRAPH_FACTORY, 3f, 3f));
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
        // DataTable: spreadsheets traditionally show ARROW over cells, not
        // HAND. The "click to select" affordance reads as cell-grid, not
        // button-click.
        if (hit instanceof Component.DataTable) return CursorManager.CursorShape.ARROW;
        if (hit != null) return CursorManager.CursorShape.HAND;
        return CursorManager.CursorShape.ARROW;
    }
}
