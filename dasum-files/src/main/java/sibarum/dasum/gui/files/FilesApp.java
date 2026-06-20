package sibarum.dasum.gui.files;

import sibarum.dasum.gui.core.GlfwContext;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.data.DataTables;
import sibarum.dasum.gui.core.data.TableSelection;
import sibarum.dasum.gui.core.dialog.FileDialog;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.EventLoop;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.CursorManager;
import sibarum.dasum.gui.core.input.DataTableHandlers;
import sibarum.dasum.gui.core.input.DataTableSelectionController;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.input.HoverState;
import sibarum.dasum.gui.core.input.InputState;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.input.ScrollbarController;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.Layout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.layout.Render;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.Projection;
import sibarum.dasum.gui.core.render.RenderStats;
import sibarum.dasum.gui.core.render.Texture;
import sibarum.dasum.gui.core.text.AtlasData;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.Icon;
import sibarum.dasum.gui.core.theme.Themed;
import sibarum.dasum.gui.core.theme.Variant;
import sibarum.dasum.gui.core.window.Window;
import sibarum.dasum.gui.files.generated.Icons;
import sibarum.dasum.gui.natives.gl.Gl;
import sibarum.dasum.gui.natives.glfw.Glfw;
import sibarum.dasum.gui.natives.glfw.GlfwCallbacks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static sibarum.dasum.gui.natives.gl.Gl.GL_COLOR_BUFFER_BIT;

/**
 * A technical file browser — the first of the toolkit's "utilitarian
 * programs" family. This first cut is a solid navigator: a clickable
 * breadcrumb path bar, Up / Home / Open… toolbar, a virtualized
 * {@link Component.DataTable} listing (Name / Size / Type / Modified,
 * directories first), double-click / Enter to descend, a show-hidden toggle,
 * and a status bar.
 *
 * <p>Architecture: {@link DirectoryModel} is the single source of truth.
 * Every navigation rebuilds the whole component tree from it (held in
 * {@link #ROOT}) — cheap, and it naturally resets per-directory table state
 * (scroll / selection) since each rebuild yields a fresh DataTable identity.
 * Input is wired directly here (no double-click hook exists in
 * {@code dasum-core}, by design), so Enter / double-click activation lives
 * in {@link #wireInput}.
 */
public final class FilesApp {

    // ---------- palette ----------
    private static final Color FRAME_BG   = new Color(0.05f, 0.06f, 0.09f, 1f);
    private static final Color TOOLBAR_BG = new Color(0.13f, 0.14f, 0.18f, 1f);
    private static final Color CRUMB_BG   = new Color(0.10f, 0.12f, 0.15f, 1f);
    private static final Color CHIP_BG    = new Color(0.18f, 0.21f, 0.27f, 1f);
    private static final Color STATUS_BG  = new Color(0.09f, 0.10f, 0.13f, 1f);
    private static final Color LABEL_FG   = new Color(0.92f, 0.94f, 0.97f, 1f);
    private static final Color DIM_FG     = new Color(0.62f, 0.67f, 0.78f, 1f);
    private static final Color ERROR_FG   = new Color(0.95f, 0.55f, 0.45f, 1f);
    private static final Color CRUMB_SEP  = new Color(0.45f, 0.50f, 0.62f, 1f);

    private static final long  DOUBLE_CLICK_NANOS    = 400_000_000L; // 400 ms

    // ---------- state ----------
    private static Window WINDOW;
    private static CursorManager CURSORS;
    private static DirectoryModel MODEL;
    private static final Property<TableSelection> SELECTION = new Property<>(null);
    private static final Property<Boolean> SHOW_HIDDEN = new Property<>(false);

    /** Holder so the render loop reads the current tree while navigation swaps it. */
    private static final Component[] ROOT = new Component[1];

    /** Left-click dispatch: target captured on press, fired on same-target release. */
    private static Component pressTarget = null;

    private FilesApp() {}

    public static void main(String[] args) {
        Path start = (args.length > 0)
            ? Path.of(args[0])
            : Path.of(System.getProperty("user.home", "."));
        MODEL = new DirectoryModel(start);

        try (GlfwContext ctx = GlfwContext.init();
             Window window = Window.create(1280, 800, "DasumGUIshi — Files");
             Batcher batcher = new Batcher();
             CursorManager cursors = new CursorManager(window.handle().address())) {

            WINDOW = window;
            CURSORS = cursors;

            Gl.load();
            batcher.init();
            cursors.init();
            DataTables.init();
            EmContext.setDpiScale(window.contentScaleX());

            try (Texture primaryTexture = Texture.fromPngResource("/dasum/atlas/primary.png");
                 Texture iconsTexture   = Texture.fromPngResource("/dasum/atlas/icons.png")) {
                AtlasData primaryAtlas = AtlasData.loadFromResource("/dasum/atlas/primary.json");
                AtlasData iconsAtlas   = AtlasData.loadFromResource("/dasum/atlas/icons.json");
                FontGroups.register(FontGroup.of(FontGroups.DEFAULT, primaryAtlas, primaryTexture));
                FontGroups.register(FontGroup.of(Icon.DEFAULT_FONT_GROUP, iconsAtlas, iconsTexture));

                SHOW_HIDDEN.subscribe(v -> { MODEL.setShowHidden(Boolean.TRUE.equals(v)); rebuild(); });

                ROOT[0] = buildUi();
                wireInput(window, cursors);

                RenderStats stats = new RenderStats();
                System.out.println("Files: " + MODEL.dir()
                    + " — double-click / Enter to open a folder, Backspace to go up, Ctrl+=/- zoom.");

                EventLoop loop = new EventLoop(window, () -> {
                    int fbW = window.framebufferWidth();
                    int fbH = window.framebufferHeight();
                    float[] projection = Projection.orthoTopLeft(fbW, fbH);

                    Gl.glViewport(0, 0, fbW, fbH);
                    Gl.glClearColor(0.03f, 0.03f, 0.05f, 1f);
                    Gl.glClear(GL_COLOR_BUFFER_BIT);

                    Component root = ROOT[0];
                    PixelRect viewport = new PixelRect(0f, 0f, fbW, fbH);
                    LayoutResult layout = Layout.compute(root, viewport);
                    LatestLayout.store(root, layout);

                    batcher.beginFrame(fbH);
                    Render.render(root, layout, batcher, projection);
                    batcher.endFrame(projection);

                    stats.recordFrame(batcher.drawCallsThisFrame(), batcher.verticesThisFrame());
                });
                loop.run();

                System.out.println("Exited cleanly; total frames rendered: " + loop.renderedFrameCount()
                    + "; idle ticks: " + loop.idleFrameCount());
            }
        }
    }

    // ---------- UI construction ----------

    /** Rebuild the whole tree from the model and request a redraw. */
    private static void rebuild() {
        // A fresh listing is a fresh context: drop any stale selection so the
        // next Enter / double-click can't act on a row index from the old dir.
        SELECTION.set(null);
        ROOT[0] = buildUi();
        Invalidator.invalidate();
    }

    private static Component buildUi() {
        return new Component.Flex(
            null, null, Em.of(0.5f), FRAME_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.of(0.5f),
            List.of(buildToolbar(), buildBreadcrumb(), buildTable(), buildStatusBar()),
            false, 0
        );
    }

    private static Component buildToolbar() {
        Component up      = Themed.iconButton(Icons.ARROW_UP,  "Up",     Em.of(4.5f), Variant.PRIMARY, 0, () -> { MODEL.up();   rebuild(); });
        Component home    = Themed.iconButton(Icons.HOME,      "Home",   Em.of(5.5f), Variant.DEFAULT, 0, () -> { MODEL.home(); rebuild(); });
        Component open    = Themed.iconButton(Icons.FOLDER,    "Open",   Em.of(5.5f), Variant.INFO,    0, FilesApp::pickFolder);
        Component refresh = Themed.iconButton(Icons.REFRESH_CW,"Refresh",Em.of(6f),   Variant.DEFAULT, 0, () -> { MODEL.refresh(); rebuild(); });

        Component spacer = new Component.Box(Em.of(1f), Em.of(0f), Em.ZERO, new Color(0f, 0f, 0f, 0f))
            .withFlexGrow(1);

        // Show-hidden toggle: eye / eye-off glyph + checkbox driven by SHOW_HIDDEN.
        Component eyeIcon = Icon.of(SHOW_HIDDEN.get() ? Icons.EYE : Icons.EYE_OFF, Em.of(1.0f), DIM_FG);
        Component hiddenCb = Themed.checkbox(Em.of(1.2f), SHOW_HIDDEN, Variant.INFO);
        Component hiddenLabel = new Component.Text("Hidden", Em.of(0.9f), LABEL_FG);

        return new Component.Flex(
            null, Em.of(3f), Em.of(0.4f), TOOLBAR_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.5f),
            List.of(up, home, open, refresh, spacer, eyeIcon, hiddenCb, hiddenLabel),
            false, 0
        );
    }

    /**
     * Clickable breadcrumb: one chip per path segment (root first), each
     * navigating to that ancestor when clicked, with a chevron between.
     * Wraps onto multiple rows for deep paths.
     */
    private static Component buildBreadcrumb() {
        List<Component> chips = new ArrayList<>();
        Path dir = MODEL.dir();
        Path root = dir.getRoot();

        // The root chip (e.g. "C:\" on Windows, "/" on POSIX). getRoot() can be
        // null for relative paths that didn't normalize to absolute — guard it.
        Path acc;
        if (root != null) {
            final Path target = root;
            chips.add(crumb(root.toString(), () -> { MODEL.navigateTo(target); rebuild(); }));
            acc = root;
        } else {
            acc = null;
        }

        for (Path segment : dir) {
            acc = (acc == null) ? segment : acc.resolve(segment);
            final Path target = acc;
            if (!chips.isEmpty()) chips.add(crumbSeparator());
            chips.add(crumb(segment.toString(), () -> { MODEL.navigateTo(target); rebuild(); }));
        }

        return new Component.Flex(
            null, Em.AUTO, Em.of(0.4f), CRUMB_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.3f),
            chips, false, 0, true /* wrap */
        );
    }

    /** A single clickable, auto-width path-segment chip. */
    private static Component crumb(String label, Runnable onClick) {
        Component text = new Component.Text(label, Em.of(0.92f), LABEL_FG);
        Component chip = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.3f), CHIP_BG,
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(text), true, 0
        );
        Handlers.onClick(chip, onClick);
        return chip;
    }

    private static Component crumbSeparator() {
        return Icon.of(Icons.CHEVRON_RIGHT, Em.of(0.85f), CRUMB_SEP);
    }

    private static Component buildTable() {
        Component.DataTable table = new Component.DataTable(
            null, null,
            new DirectoryTableSource(MODEL),
            SELECTION
        ).withFlexGrow(1);
        // Double-click or Enter on a row → descend. The table owns the
        // double-click gesture now (DataTableSelectionController); we just
        // react to the resolved row. BODY and GUTTER both map to a row.
        DataTableHandlers.onActivate(table, a -> {
            if (a.region() == DataTableSelectionController.Region.BODY
                || a.region() == DataTableSelectionController.Region.GUTTER) {
                activate(a.row());
            }
        });
        return table;
    }

    private static Component buildStatusBar() {
        long count = MODEL.entries().stream().filter(e -> !e.parentLink()).count();
        String text;
        if (MODEL.error() != null) {
            text = "! " + MODEL.error();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(count).append(count == 1 ? " item" : " items");
            Entry sel = selectedEntry();
            if (sel != null && !sel.parentLink()) {
                sb.append("    -    ").append(sel.name());
                if (!sel.dir()) sb.append("    ").append(DirectoryTableSource.humanSize(sel.size()));
            }
            text = sb.toString();
        }
        Component label = new Component.Text(text, Em.of(0.85f),
            MODEL.error() != null ? ERROR_FG : DIM_FG);
        return new Component.Flex(
            null, Em.of(1.7f), Em.of(0.4f), STATUS_BG,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
            List.of(label), false, 0
        );
    }

    // ---------- activation ----------

    /** Open a directory row; files are a no-op in this first cut. */
    private static void activate(int row) {
        Entry e = MODEL.entryAt(row);
        if (e == null) return;
        if (e.parentLink()) {
            MODEL.up();
            rebuild();
        } else if (e.dir()) {
            MODEL.navigateTo(e.path());
            rebuild();
        }
        // Files: selection already shows name/size in the status bar; an
        // open/preview action lands in a later iteration.
    }

    /** The single selected body row, or -1 if the selection isn't a single row. */
    private static int selectedRow() {
        TableSelection s = SELECTION.get();
        if (s == null) return -1;
        boolean single = (s.mode() == TableSelection.Mode.CELLS || s.mode() == TableSelection.Mode.ROWS)
            && s.rowStart() == s.rowEnd();
        return single ? s.rowStart() : -1;
    }

    private static Entry selectedEntry() {
        int r = selectedRow();
        return r >= 0 ? MODEL.entryAt(r) : null;
    }

    private static void pickFolder() {
        FileDialog.pickFolder(WINDOW, MODEL.dir())
            .ifPresent(p -> { MODEL.navigateTo(p); rebuild(); });
    }

    // ---------- input wiring ----------

    private static void wireInput(Window window, CursorManager cursors) {
        GlfwCallbacks.setKeyListener((win, key, scancode, action, mods) -> {
            InputState.setMods(mods);
            if (action != Glfw.GLFW_PRESS && action != Glfw.GLFW_REPEAT) return;
            boolean ctrl  = (mods & Glfw.GLFW_MOD_CONTROL) != 0;
            boolean shift = (mods & Glfw.GLFW_MOD_SHIFT)   != 0;

            // Copy the selected cells from the table (Ctrl+C).
            if (ctrl && key == 'C' && DataTableSelectionController.onCopy(window.handle())) return;

            // Backspace navigates up (classic Explorer behavior). Intercept
            // before the table controller, which would otherwise consume it.
            if (key == Glfw.GLFW_KEY_BACKSPACE) { MODEL.up(); rebuild(); return; }

            // Arrows / Home / End / Tab move the table selection; Enter routes
            // through the controller, which fires our registered row-activation
            // handler (see buildTable) so the selected folder opens.
            if (DataTableSelectionController.onKey(key, mods, window.handle())) return;

            if (key == Glfw.GLFW_KEY_ESCAPE && action == Glfw.GLFW_PRESS) {
                if (FocusState.focused() != null) {
                    FocusState.clear();
                } else {
                    Glfw.glfwSetWindowShouldClose(window.handle(), true);
                    Invalidator.invalidate();
                }
            } else if (key == Glfw.GLFW_KEY_TAB) {
                Component root = LatestLayout.root();
                if (root != null) FocusState.cycle(root, shift);
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
            DataTableSelectionController.onCursorMove(x, y);
            if (DataTableSelectionController.isDragging()) return;

            LayoutResult lr = LatestLayout.result();
            Component root = LatestLayout.root();
            if (lr == null || root == null) return;
            Component hit = HitTest.test(root, lr, (float) x, (float) y);
            HoverState.update(hit);
            cursors.setShape(cursorShapeFor(hit));
        });

        GlfwCallbacks.setMouseButtonListener((win, button, action, mods) -> {
            InputState.setMods(mods);
            if (button != Glfw.GLFW_MOUSE_BUTTON_LEFT) return; // right-click menu: later iteration
            boolean pressed = (action == Glfw.GLFW_PRESS);
            InputState.setLeftButtonHeld(pressed);
            boolean shift = (mods & Glfw.GLFW_MOD_SHIFT) != 0;

            if (pressed) {
                if (ScrollbarController.onMouseDown(InputState.mouseX(), InputState.mouseY())) {
                    pressTarget = null;
                    return;
                }
                // The controller claims the press for selection / drag-extend,
                // and fires our row-activation handler on a double-click.
                if (DataTableSelectionController.onMouseDown(InputState.mouseX(), InputState.mouseY(), shift)) {
                    pressTarget = null;
                    return;
                }
                Component hovered = HoverState.hovered();
                pressTarget = hovered;
                if (hovered != null) FocusState.set(hovered);
                else                 FocusState.clear();
            } else {
                boolean scrollDrag = ScrollbarController.isDragging();
                boolean tableDrag  = DataTableSelectionController.isDragging();
                ScrollbarController.onMouseUp();
                DataTableSelectionController.onMouseUp();

                LayoutResult lr = LatestLayout.result();
                Component root = LatestLayout.root();
                Component released = (lr != null && root != null)
                    ? HitTest.test(root, lr, (float) InputState.mouseX(), (float) InputState.mouseY())
                    : null;
                if (!scrollDrag && !tableDrag && pressTarget != null && released == pressTarget) {
                    Handlers.activate(pressTarget, root);
                }
                pressTarget = null;
            }
        });

        GlfwCallbacks.setCursorEnterListener((win, entered) -> {
            if (!entered) {
                HoverState.clear();
                ScrollbarController.clearHover();
                cursors.setShape(CursorManager.CursorShape.ARROW);
                Invalidator.invalidate();
            }
        });

        GlfwCallbacks.setWindowFocusListener((win, focused) -> {
            if (!focused) {
                ScrollbarController.cancelDrag();
                DataTableSelectionController.cancelDrag();
                HoverState.clear();
                InputState.setLeftButtonHeld(false);
            }
            Invalidator.invalidate();
        });

        // Scroll dispatch is owned by the framework WheelRouter (installed
        // by Window.create) — DataTables and scroll containers route
        // automatically, no app wiring needed.
    }

    private static CursorManager.CursorShape cursorShapeFor(Component hit) {
        if (hit instanceof Component.DataTable) return CursorManager.CursorShape.ARROW;
        if (hit != null) return CursorManager.CursorShape.HAND;
        return CursorManager.CursorShape.ARROW;
    }
}
