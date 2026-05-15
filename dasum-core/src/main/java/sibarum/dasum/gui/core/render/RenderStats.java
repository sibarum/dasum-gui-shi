package sibarum.dasum.gui.core.render;

/**
 * Per-second rolling counter that prints frame / draw-call / vertex stats
 * to stderr. Intended as a development diagnostic — proves the batcher is
 * issuing the expected number of draw calls.
 */
public final class RenderStats {

    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;

    private long windowStartNanos = System.nanoTime();
    private long frames = 0;
    private long drawCalls = 0;
    private long vertices = 0;

    public void recordFrame(int drawCallsThisFrame, int verticesThisFrame) {
        frames++;
        drawCalls += drawCallsThisFrame;
        vertices += verticesThisFrame;

        long now = System.nanoTime();
        long elapsed = now - windowStartNanos;
        if (elapsed >= REPORT_INTERVAL_NANOS) {
            System.err.printf("RenderStats: %d frames, %d draw calls, %d vertices in %.2fs%n",
                frames, drawCalls, vertices, elapsed / 1e9);
            windowStartNanos = now;
            frames = 0;
            drawCalls = 0;
            vertices = 0;
        }
    }
}
