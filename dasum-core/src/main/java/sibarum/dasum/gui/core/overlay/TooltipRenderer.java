package sibarum.dasum.gui.core.overlay;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.layout.Layout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.layout.Render;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.DrawCommand;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.TextMetrics;

import java.util.List;

/**
 * Last-pass per-frame renderer that draws the live tooltip popup (if any)
 * on top of the rest of the UI. Apps invoke {@link #render} from their
 * render lambda after {@code Render.render} of the main UI and every
 * overlay, and after a {@code batcher.flush(projection)} — the flush is
 * essential because the {@link Batcher}'s two-pass material flush (solid
 * fills then MSDF text) would otherwise float the tooltip's text above
 * underlying solid fills, breaking painter-algorithm z-order.
 * <p>
 * Geometry: a small padded {@link Component.Flex} wrapping a
 * {@link Component.Text}. The popup is positioned at
 * {@code (cursor + offset)}; if that would clip the viewport on the
 * right or bottom edge, the popup flips to the opposite side of the
 * cursor; if it still doesn't fit, it's clamped flush against the edge.
 */
public final class TooltipRenderer {

    private static final Color BG_COLOR     = new Color(0.10f, 0.12f, 0.15f, 0.92f);
    private static final Color BORDER_COLOR = new Color(0.35f, 0.40f, 0.50f, 0.75f);
    private static final Color TEXT_COLOR   = new Color(0.95f, 0.96f, 0.98f, 1.00f);
    private static final Color SHADOW_COLOR = new Color(0.00f, 0.00f, 0.00f, 0.30f);

    private static final Em PADDING_EM   = Em.of(0.35f);
    private static final Em FONT_SIZE_EM = Em.of(0.85f);
    private static final Em MAX_WRAP_EM  = Em.of(22f);
    private static final float BORDER_PX = 1f;
    private static final float SHADOW_OFFSET_PX = 2f;

    private TooltipRenderer() {}

    /**
     * Render the live tooltip into the batcher, if visible. Caller is
     * responsible for having already invoked {@code batcher.flush(projection)}
     * since the previous z-layer's vertices would otherwise be drawn
     * above the tooltip due to the batcher's two-pass solids/text flush.
     */
    public static void render(Batcher batcher, float[] projection, PixelRect viewport) {
        if (!TooltipController.isVisible()) return;
        String text = TooltipController.currentText();
        if (text == null || text.isEmpty()) return;

        // Defensive: if the text's font group isn't registered, skip
        // silently rather than throwing inside the render loop.
        if (!FontGroups.isRegistered(FontGroups.DEFAULT)) return;

        // Build the tooltip component graph fresh each frame. The records
        // are pure-data; reusing them across frames buys nothing because
        // the text or position can change every frame, and they don't
        // touch any sidecar (we render via Layout.computeWithin which
        // doesn't pull from TextStates because we never call setContent
        // on these instances).
        Component.Text body = new Component.Text(text, FONT_SIZE_EM, TEXT_COLOR)
            .withWrapWidth(MAX_WRAP_EM);
        Component.Flex frame = new Component.Flex(
            Em.AUTO, Em.AUTO, PADDING_EM, BG_COLOR,
            Direction.COLUMN, JustifyContent.CENTER, AlignItems.START, Em.ZERO,
            List.of(body), false, 0
        );

        // Compute intrinsic size from text metrics (the Flex's AUTO axes
        // resolve to children's intrinsic sizes plus padding).
        String content = TextStates.contentOf(body);   // identity-fresh; falls back to body.content()
        float padPx = PADDING_EM.toPixels();
        float textW = TextMetrics.contentWidthPixels(body, content);
        float textH = TextMetrics.contentHeightPixels(body, content);
        float w = textW + 2f * padPx;
        float h = textH + 2f * padPx;

        // Clamp to viewport. A tooltip larger than the viewport itself
        // would be clamped flush against top-left; we'd rather render
        // truncated than crash.
        float vpW = Math.max(1f, viewport.width());
        float vpH = Math.max(1f, viewport.height());
        if (w > vpW) w = vpW;
        if (h > vpH) h = vpH;

        // Cursor-anchored placement with edge-flip avoidance.
        double cx = TooltipController.cursorX();
        double cy = TooltipController.cursorY();
        float offsetX = TooltipController.cursorOffsetXPx();
        float offsetY = TooltipController.cursorOffsetYPx();

        float x = (float) cx + offsetX;
        float y = (float) cy + offsetY;
        // Flip to the opposite side of the cursor if the placement
        // would clip the right or bottom edge.
        if (x + w > viewport.x() + vpW) x = (float) cx - offsetX - w;
        if (y + h > viewport.y() + vpH) y = (float) cy - offsetY - h;
        // Final clamp — if the cursor sits in the top-left corner the
        // flip just took x or y negative; pin to the viewport.
        if (x < viewport.x()) x = viewport.x();
        if (y < viewport.y()) y = viewport.y();
        if (x + w > viewport.x() + vpW) x = viewport.x() + vpW - w;
        if (y + h > viewport.y() + vpH) y = viewport.y() + vpH - h;

        PixelRect rect = new PixelRect(x, y, w, h);

        // Drop shadow under the popup — sits beneath the background quad
        // so the body opacity reads correctly on whatever's behind it.
        batcher.submit(new DrawCommand.ColoredQuad(
            rect.x() + SHADOW_OFFSET_PX, rect.y() + SHADOW_OFFSET_PX,
            rect.width(), rect.height(), SHADOW_COLOR));

        // Border + body. We emit the border as four thin rects rather
        // than overpainting a larger rect first — that keeps the
        // semi-transparent body alpha from compositing twice and
        // double-darkening the bg.
        emitBorderRing(batcher, rect, BORDER_PX, BORDER_COLOR);

        // Body + text via Layout/Render reuse — give it the rect inset
        // by the border so the glyphs land inside the ring.
        PixelRect inner = new PixelRect(
            rect.x() + BORDER_PX, rect.y() + BORDER_PX,
            Math.max(0f, rect.width()  - 2f * BORDER_PX),
            Math.max(0f, rect.height() - 2f * BORDER_PX)
        );
        LayoutResult lr = Layout.computeWithin(frame, inner);
        Render.render(frame, lr, batcher, projection);

        // Flush so the tooltip's geometry doesn't bleed into any
        // subsequent draws after this method returns (e.g. an app
        // that paints debug overlays after the tooltip).
        batcher.flush(projection);
    }

    private static void emitBorderRing(Batcher batcher, PixelRect r, float t, Color c) {
        batcher.submit(new DrawCommand.ColoredQuad(r.x(),         r.y(),          r.width(),       t,                   c));
        batcher.submit(new DrawCommand.ColoredQuad(r.x(),         r.bottom() - t, r.width(),       t,                   c));
        batcher.submit(new DrawCommand.ColoredQuad(r.x(),         r.y() + t,      t,               r.height() - 2f * t, c));
        batcher.submit(new DrawCommand.ColoredQuad(r.right() - t, r.y() + t,      t,               r.height() - 2f * t, c));
    }
}
