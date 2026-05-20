package sibarum.dasum.gui.core.data;

/**
 * One-shot module init for the data-table feature. Apps that use
 * {@code Component.DataTable} should call {@link #init()} once at startup
 * (next to {@code DasumVis.init()}) so the renderer is registered.
 * <p>
 * Calling {@code init()} more than once is harmless — the underlying
 * register is guarded.
 */
public final class DataTables {

    private DataTables() {}

    /** Register the {@link DataTableRenderer}. Idempotent. */
    public static void init() {
        DataTableRenderer.register();
    }
}
