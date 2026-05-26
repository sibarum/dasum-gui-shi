package sibarum.dasum.gui.core.layout;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.HoverState;
import sibarum.dasum.gui.core.input.ScrollbarController;
import sibarum.dasum.gui.core.input.TabsController;
import sibarum.dasum.gui.core.input.TextState;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.input.TextStyle;
import sibarum.dasum.gui.core.input.TextStyleStates;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.CustomRenderers;
import sibarum.dasum.gui.core.render.DrawCommand;
import sibarum.dasum.gui.core.render.GlStateGuard;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.LineBreaker;
import sibarum.dasum.gui.core.text.TextGeometry;
import sibarum.dasum.gui.core.text.TextMetrics;

import java.util.List;

/**
 * Second pass per dirty frame. Consumes the {@link LayoutResult} from
 * {@link Layout#compute} and emits {@link DrawCommand}s into the
 * {@link Batcher}. Walks parent-then-children so painter's-algorithm
 * implicit z-order works correctly.
 * <p>
 * Text rendering: when a {@link Component.Text} is encountered, the
 * renderer rebinds the batcher's text accumulator to the named font
 * group's atlas (M8 minimum supports one font group at a time;
 * multi-font-group batching needs flush-on-atlas-change in a later
 * milestone).
 */
public final class Render {

    private static final Color HOVER_TINT       = new Color(1f, 1f, 1f, 0.10f);
    private static final Color FOCUS_RING       = new Color(0.40f, 0.80f, 1.00f, 0.90f);
    private static final Color SCROLLBAR_IDLE   = new Color(0.55f, 0.58f, 0.65f, 0.40f);
    private static final Color SCROLLBAR_HOVER  = new Color(0.70f, 0.75f, 0.85f, 1.00f);
    private static final Color SELECTION_FILL   = new Color(0.40f, 0.80f, 1.00f, 0.35f);
    private static final Color CARET_COLOR      = new Color(0.95f, 0.95f, 1.00f, 1.00f);
    private static final Color PHANTOM_CARET    = new Color(0.95f, 0.95f, 1.00f, 0.35f);
    private static final float FOCUS_RING_THICKNESS_PX = 2f;

    private Render() {}

    public static void render(Component root, LayoutResult layout, Batcher batcher, float[] projection) {
        Component hovered = HoverState.hovered();
        Component focused = FocusState.focused();
        renderInOrder(root, layout, batcher, projection, hovered, focused);
    }

    /**
     * Backwards-compatible alias for callers that picked up the
     * short-lived 6-arg signature from an in-progress framework
     * version. The framebuffer dimensions are now redundant — custom
     * renderers query GL state directly via
     * {@code Gl.glGetIntegerv4(GL_VIEWPORT)} — so this overload simply
     * ignores them and delegates to the 4-arg version. Prefer the
     * 4-arg form in new code; this exists so existing apps don't break
     * on upgrade.
     */
    public static void render(Component root, LayoutResult layout, Batcher batcher, float[] projection,
                              int framebufferWidthPx, int framebufferHeightPx) {
        render(root, layout, batcher, projection);
    }

    private static void renderInOrder(Component c, LayoutResult layout, Batcher batcher, float[] projection,
                                       Component hovered, Component focused) {
        PixelRect r = layout.rectOf(c);
        if (r != null && r.width() > 0f && r.height() > 0f) {
            Color color = backgroundColorOf(c);
            if (color.a() > 0f) {
                batcher.submit(new DrawCommand.ColoredQuad(r.x(), r.y(), r.width(), r.height(), color));
            }
            if (c == hovered && !(c instanceof Component.Tabs) && !(c instanceof Component.GraphSurface) && !(c instanceof Component.PointCloud)
                && !(c instanceof Component.Text txt && txt.selectable())) {
                // Tabs does its own per-cell hover inside renderTabs — skip the
                // generic whole-rect tint so it doesn't wash the header strip.
                // GraphSurface is interactive (so right-clicks land on its
                // empty area) but it isn't "clickable" in a UI-feedback sense;
                // tinting the whole canvas on hover is wrong.
                // PointCloud is interactive for orbit-drag + click-pick, but
                // the whole-viewport tint washes the 3D content and gives no
                // useful feedback — the cursor change to HAND is enough.
                // Selectable Text (editors, text inputs) already signals
                // hoverability with the IBEAM cursor and a phantom caret at
                // the cursor position; the whole-rect wash on top of that
                // reads like a selection state, not a hover.
                batcher.submit(new DrawCommand.ColoredQuad(r.x(), r.y(), r.width(), r.height(), HOVER_TINT));
            }
            if (c == focused) {
                emitFocusRing(batcher, r);
            }
        }

        if (c instanceof Component.Scroll scroll && scroll.child() != null) {
            renderScrollContents(scroll, r, layout, batcher, projection, hovered, focused);
        } else if (c instanceof Component.Text text) {
            renderText(text, r, batcher, projection);
        } else if (c instanceof Component.Checkbox cb) {
            renderCheckbox(cb, r, batcher);
        } else if (c instanceof Component.Radio<?> radio) {
            renderRadio(radio, r, batcher);
        } else if (c instanceof Component.Slider sl) {
            renderSlider(sl, r, batcher);
        } else if (c instanceof Component.Tabs tabs) {
            renderTabs(tabs, r, layout, batcher, projection, hovered, focused);
        } else if (c instanceof Component.GraphSurface surface) {
            renderGraphSurface(surface, layout, batcher, projection, hovered, focused);
        } else if (c instanceof Component.PointCloud pc) {
            CustomRenderers.Renderer ext = CustomRenderers.find(pc);
            if (ext != null && r != null) {
                // Wrap the extension call with a GL-state guard so a
                // renderer that forgets to restore viewport / depth /
                // scissor / current-program shows up as a stderr diff
                // when -Ddasum.debug.gl=true. Zero cost when the flag
                // is off — snapshot() returns null and assertUnchanged
                // is a no-op on null.
                GlStateGuard.Snapshot before = GlStateGuard.snapshot();
                ext.render(pc, r, batcher, projection);
                GlStateGuard.assertUnchanged(before, "CustomRenderer<" + pc.getClass().getSimpleName() + ">");
            }
        } else if (c instanceof Component.DataTable dt) {
            CustomRenderers.Renderer ext = CustomRenderers.find(dt);
            if (ext != null && r != null) {
                GlStateGuard.Snapshot before = GlStateGuard.snapshot();
                ext.render(dt, r, batcher, projection);
                GlStateGuard.assertUnchanged(before, "CustomRenderer<" + dt.getClass().getSimpleName() + ">");
            }
        } else {
            for (Component child : childrenOf(c)) {
                renderInOrder(child, layout, batcher, projection, hovered, focused);
            }
        }
    }

    /**
     * Canvas children can overlap. The Batcher's two-pass flush (solid-fills
     * then glyphs) would otherwise float ALL labels above ALL backgrounds,
     * so a sibling's bg would sit over a previously-rendered node's label.
     * Flushing after each child renders each node atomically, preserving
     * painter's-algorithm z-order between siblings. Children are walked in
     * {@link sibarum.dasum.gui.core.graph.GraphSurfaceZOrder} so click-to-front
     * just works.
     */
    private static void renderGraphSurface(Component.GraphSurface surface, LayoutResult layout, Batcher batcher,
                                      float[] projection, Component hovered, Component focused) {
        for (Component child : sibarum.dasum.gui.core.graph.GraphSurfaceZOrder.orderedChildren(surface)) {
            renderInOrder(child, layout, batcher, projection, hovered, focused);
        }
        // Connections sit on top of nodes so the curve visually emerges from
        // each port. The renderer is data-driven from the Connections sidecar
        // — no per-node coupling.
        sibarum.dasum.gui.core.graph.ConnectionRenderer.render(surface, layout, batcher);
        // In-flight connection-drag preview, on top of finalized connections.
        sibarum.dasum.gui.core.input.ConnectionDragController.renderInflight(surface, layout, batcher);
    }

    private static void renderTabs(Component.Tabs tabs, PixelRect rect, LayoutResult layout, Batcher batcher,
                                    float[] projection, Component hovered, Component focused) {
        if (rect == null) return;
        // The Tabs's own backgroundColorOf returned contentBg; that's already
        // painted over the whole rect. Overlay the header strip on top.
        PixelRect strip = TabsController.headerStripRect(tabs, rect);
        batcher.submit(new DrawCommand.ColoredQuad(strip.x(), strip.y(), strip.width(), strip.height(), tabs.headerBg()));

        FontGroup fg = sibarum.dasum.gui.core.text.FontGroups.getOrDefault(tabs.fontGroup());
        float fontPx   = tabs.tabFontSize().toPixels();
        float ascender = fg.atlas().metrics().ascender() * fontPx;
        int active = tabs.activeIndex().get();

        // Cells (active fill / hover tint) — emit before glyphs so labels sit on top.
        for (int i = 0; i < tabs.tabs().size(); i++) {
            PixelRect cell = TabsController.tabCellRect(tabs, i, rect);
            if (i == active) {
                batcher.submit(new DrawCommand.ColoredQuad(cell.x(), cell.y(), cell.width(), cell.height(), tabs.activeTabBg()));
            } else if (TabsController.isTabHovered(tabs, i)) {
                batcher.submit(new DrawCommand.ColoredQuad(cell.x(), cell.y(), cell.width(), cell.height(), HOVER_TINT));
            }
        }

        // Labels.
        batcher.setTextAtlas(fg.texture(), fg.distanceRange(), projection);
        for (int i = 0; i < tabs.tabs().size(); i++) {
            PixelRect cell = TabsController.tabCellRect(tabs, i, rect);
            String label = tabs.tabs().get(i).label();
            float labelW = TabsController.labelWidth(fg, label, fontPx);
            float baseX = cell.x() + (cell.width() - labelW) * 0.5f;
            float baseY = cell.y() + (cell.height() - fontPx) * 0.5f + ascender;
            float cx = baseX;
            for (int j = 0; j < label.length(); ) {
                int cp = label.codePointAt(j);
                DrawCommand.GlyphQuad q = fg.layout().build(cp, cx, baseY, fontPx, tabs.tabFg());
                if (q != null) batcher.submit(q);
                cx += fg.layout().advance(cp, fontPx);
                j += Character.charCount(cp);
            }
        }

        // Active content (recurse).
        Component activeContent = tabs.activeContent();
        if (activeContent != null) {
            renderInOrder(activeContent, layout, batcher, projection, hovered, focused);
        }
    }

    private static void renderSlider(Component.Slider sl, PixelRect rect, Batcher batcher) {
        if (rect == null) return;
        // Track was painted by the generic background pass (backgroundColorOf
        // returns trackColor). Overlay the fill portion, then the thumb.
        float fraction = sl.fraction();
        float thumbT   = sl.thumbThickness().toPixels();

        if (sl.horizontal()) {
            float fillW = rect.width() * fraction;
            if (fillW > 0f) {
                batcher.submit(new DrawCommand.ColoredQuad(rect.x(), rect.y(), fillW, rect.height(), sl.fillColor()));
            }
            float thumbCx = rect.x() + fillW;
            float thumbX  = Math.max(rect.x(), Math.min(rect.right() - thumbT, thumbCx - thumbT * 0.5f));
            batcher.submit(new DrawCommand.ColoredQuad(thumbX, rect.y(), thumbT, rect.height(), sl.thumbColor()));
        } else {
            float fillH = rect.height() * fraction;
            if (fillH > 0f) {
                batcher.submit(new DrawCommand.ColoredQuad(rect.x(), rect.y(), rect.width(), fillH, sl.fillColor()));
            }
            float thumbCy = rect.y() + fillH;
            float thumbY  = Math.max(rect.y(), Math.min(rect.bottom() - thumbT, thumbCy - thumbT * 0.5f));
            batcher.submit(new DrawCommand.ColoredQuad(rect.x(), thumbY, rect.width(), thumbT, sl.thumbColor()));
        }
    }

    private static void renderCheckbox(Component.Checkbox cb, PixelRect rect, Batcher batcher) {
        if (rect == null) return;
        // boxColor was already painted by the generic background pass; only the
        // inset indicator is conditional. (The colored-quad emit above runs for
        // any component with a non-transparent backgroundColorOf().)
        if (Boolean.TRUE.equals(cb.value().get())) {
            float inset = Math.min(rect.width(), rect.height()) * 0.25f;
            batcher.submit(new DrawCommand.ColoredQuad(
                rect.x() + inset, rect.y() + inset,
                Math.max(0f, rect.width()  - 2f * inset),
                Math.max(0f, rect.height() - 2f * inset),
                cb.checkColor()
            ));
        }
    }

    private static void renderRadio(Component.Radio<?> r, PixelRect rect, Batcher batcher) {
        if (rect == null) return;
        if (r.selected()) {
            // Tighter inset than Checkbox so the two widgets read distinct
            // until we have circle rendering.
            float inset = Math.min(rect.width(), rect.height()) * 0.32f;
            batcher.submit(new DrawCommand.ColoredQuad(
                rect.x() + inset, rect.y() + inset,
                Math.max(0f, rect.width()  - 2f * inset),
                Math.max(0f, rect.height() - 2f * inset),
                r.dotColor()
            ));
        }
    }

    private static void renderText(Component.Text text, PixelRect rect, Batcher batcher, float[] projection) {
        if (rect == null) return;
        FontGroup fg = FontGroups.getOrDefault(text.fontGroup());
        String content = TextStates.contentOf(text);

        // overflow:hidden — scissor-clip glyphs and decorations to the text rect.
        boolean clipping = text.clip();
        if (clipping) {
            batcher.flush(projection);
            batcher.scissor().push(rect);
        }

        // User background style ranges — emitted first so selection and
        // glyphs sit on top. Stale indices are silently clipped inside
        // TextGeometry.styleRects against the live content length.
        List<TextStyle> bgRanges = TextStyleStates.backgroundOf(text);
        if (!bgRanges.isEmpty()) {
            for (TextStyle r : bgRanges) {
                for (PixelRect br : TextGeometry.styleRects(text, content, rect, r.start(), r.end())) {
                    if (br.width() > 0f && br.height() > 0f) {
                        batcher.submit(new DrawCommand.ColoredQuad(br.x(), br.y(), br.width(), br.height(), r.color()));
                    }
                }
            }
        }

        // Selection background — emitted before glyphs so it sits behind them.
        // Painted AFTER user background ranges so selection translucency
        // reads cleanly over any colored underlay.
        if (text.selectable()) {
            TextState ts = TextStates.of(text);
            if (ts.hasSelection()) {
                for (PixelRect sel : TextGeometry.selectionRects(text, content, rect, ts.selectionStart(), ts.selectionEnd())) {
                    if (sel.width() > 0f && sel.height() > 0f) {
                        batcher.submit(new DrawCommand.ColoredQuad(sel.x(), sel.y(), sel.width(), sel.height(), SELECTION_FILL));
                    }
                }
            }
        }

        // Foreground style ranges — bake into a per-char color array so
        // the inner glyph loop is O(1) per char. Cost: one int → ref array
        // of length content.length() per frame, only when ranges exist.
        // Last-in-list wins on overlap (matches the documented API).
        List<TextStyle> fgRanges = TextStyleStates.foregroundOf(text);
        Color[] perCharColor = null;
        if (!fgRanges.isEmpty() && !content.isEmpty()) {
            perCharColor = new Color[content.length()];
            int len = content.length();
            for (TextStyle r : fgRanges) {
                int s = Math.max(0, r.start());
                int e = Math.min(len, r.end());
                Color rc = r.color();
                for (int i = s; i < e; i++) perCharColor[i] = rc;
            }
        }

        // Glyphs — iterate visual lines (LineBreaker handles both wrap and '\n').
        batcher.setTextAtlas(fg.texture(), fg.distanceRange(), projection);
        float fontPx     = text.fontSize().toPixels();
        float padPx      = text.padding().toPixels();
        float lineHeight = fg.atlas().metrics().lineHeight() * fontPx;
        float ascender   = fg.atlas().metrics().ascender()   * fontPx;

        float startX = rect.x() + padPx;
        float baseY  = rect.y() + padPx + ascender;
        Color defaultColor = text.color();

        for (LineBreaker.LineSpan line : TextMetrics.lines(text, content)) {
            float cx = startX;
            for (int j = line.start(); j < line.end(); ) {
                int cp = content.codePointAt(j);
                Color glyphColor = (perCharColor != null && perCharColor[j] != null)
                    ? perCharColor[j] : defaultColor;
                DrawCommand.GlyphQuad q = fg.layout().build(cp, cx, baseY, fontPx, glyphColor);
                if (q != null) batcher.submit(q);
                cx += fg.layout().advance(cp, fontPx);
                j += Character.charCount(cp);
            }
            baseY += lineHeight;
        }

        // Caret + phantom — emitted last, on top of glyphs.
        if (text.selectable()) {
            TextState ts = TextStates.of(text);
            if (FocusState.focused() == text) {
                PixelRect caret = TextGeometry.caretBounds(text, content, rect, ts.caretIndex);
                batcher.submit(new DrawCommand.ColoredQuad(caret.x(), caret.y(), caret.width(), caret.height(), CARET_COLOR));
            } else if (ts.hoverCaretIndex >= 0) {
                PixelRect phantom = TextGeometry.caretBounds(text, content, rect, ts.hoverCaretIndex);
                batcher.submit(new DrawCommand.ColoredQuad(phantom.x(), phantom.y(), phantom.width(), phantom.height(), PHANTOM_CARET));
            }
        }

        if (clipping) {
            batcher.flush(projection);
            batcher.scissor().pop();
        }
    }

    private static void renderScrollContents(Component.Scroll scroll, PixelRect outer,
                                              LayoutResult layout, Batcher batcher, float[] projection,
                                              Component hovered, Component focused) {
        PixelRect interior = padInset(outer, scroll.padding());

        batcher.flush(projection);
        batcher.scissor().push(interior);

        renderInOrder(scroll.child(), layout, batcher, projection, hovered, focused);

        batcher.flush(projection);
        batcher.scissor().pop();

        emitScrollbars(batcher, scroll, interior);
    }

    private static void emitScrollbars(Batcher batcher, Component.Scroll scroll, PixelRect interior) {
        PixelRect vt = ScrollbarController.verticalThumbRect(scroll, interior);
        if (vt != null) {
            Color tint = ScrollbarController.isThumbHovered(scroll, ScrollbarController.Axis.VERTICAL)
                ? SCROLLBAR_HOVER : SCROLLBAR_IDLE;
            batcher.submit(new DrawCommand.ColoredQuad(vt.x(), vt.y(), vt.width(), vt.height(), tint));
        }
        PixelRect ht = ScrollbarController.horizontalThumbRect(scroll, interior);
        if (ht != null) {
            Color tint = ScrollbarController.isThumbHovered(scroll, ScrollbarController.Axis.HORIZONTAL)
                ? SCROLLBAR_HOVER : SCROLLBAR_IDLE;
            batcher.submit(new DrawCommand.ColoredQuad(ht.x(), ht.y(), ht.width(), ht.height(), tint));
        }
    }

    private static PixelRect padInset(PixelRect rect, Em padding) {
        float pad = padding.toPixels();
        return new PixelRect(
            rect.x() + pad, rect.y() + pad,
            Math.max(0f, rect.width()  - 2f * pad),
            Math.max(0f, rect.height() - 2f * pad)
        );
    }

    private static Color backgroundColorOf(Component c) {
        return switch (c) {
            case Component.Box  b -> b.color();
            case Component.Flex f -> f.color();
            case Component.Scroll s -> s.color();
            case Component.Text t   -> new Color(0f, 0f, 0f, 0f); // text has no background
            case Component.Checkbox cb -> cb.boxColor();
            case Component.Radio<?> r  -> r.boxColor();
            case Component.Slider sl   -> sl.trackColor();
            case Component.Tabs t      -> t.contentBg();
            case Component.GraphSurface cv   -> cv.color();
            case Component.PointCloud pc -> pc.color();
            // DataTable paints its own background (banded rows, header strip,
            // row-number gutter) inside the custom renderer — emit transparent
            // here so the generic pass doesn't draw a single color over the
            // whole table rect.
            case Component.DataTable dt -> new Color(0f, 0f, 0f, 0f);
        };
    }

    private static List<Component> childrenOf(Component c) {
        return switch (c) {
            case Component.Box  b -> sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(b);
            case Component.Flex f -> sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(f);
            case Component.Scroll s -> s.child() != null ? List.of(s.child()) : List.of();
            case Component.Text t   -> List.of();
            case Component.Checkbox cb -> List.of();
            case Component.Radio<?> r  -> List.of();
            case Component.Slider sl   -> List.of();
            case Component.Tabs t      -> t.activeContent() != null ? List.of(t.activeContent()) : List.of();
            case Component.GraphSurface cv   -> sibarum.dasum.gui.core.graph.GraphSurfaceChildren.all(cv);
            case Component.PointCloud pc -> List.of();
            case Component.DataTable dt -> List.of();
        };
    }

    private static void emitFocusRing(Batcher batcher, PixelRect r) {
        float t = FOCUS_RING_THICKNESS_PX;
        batcher.submit(new DrawCommand.ColoredQuad(r.x(),         r.y(),          r.width(),       t,                   FOCUS_RING));
        batcher.submit(new DrawCommand.ColoredQuad(r.x(),         r.bottom() - t, r.width(),       t,                   FOCUS_RING));
        batcher.submit(new DrawCommand.ColoredQuad(r.x(),         r.y() + t,      t,               r.height() - 2f * t, FOCUS_RING));
        batcher.submit(new DrawCommand.ColoredQuad(r.right() - t, r.y() + t,      t,               r.height() - 2f * t, FOCUS_RING));
    }
}
