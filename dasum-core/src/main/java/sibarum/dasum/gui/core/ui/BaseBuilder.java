package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;

/**
 * Shared fluent state for the container/leaf builders — an optional debug
 * {@code label}, a {@code flexGrow} weight, and the {@code interactive}
 * flag. Uses a self-type parameter so the fluent setters return the concrete
 * builder type for chaining.
 */
abstract class BaseBuilder<B extends BaseBuilder<B>> implements UiBuilder {

    protected String label;
    protected int grow = 0;
    protected boolean interactive = false;

    @SuppressWarnings("unchecked")
    private B self() { return (B) this; }

    /** Attach a debug label used in {@link LayoutLint} diagnostics ({@code card 'home'}). */
    public B named(String label) { this.label = label; return self(); }

    /** Per-child flex weight when this node is a child of a Flex parent (must be >= 0). */
    public B grow(int weight) { this.grow = weight; return self(); }

    /** Whether this node participates in hover / focus / hit-testing. */
    public B interactive(boolean v) { this.interactive = v; return self(); }

    /** Register the label against the freshly-built record and return it. */
    protected Component tagged(Component built) {
        UiLabels.set(built, label);
        return built;
    }

    /** Resolve a child argument that may be a raw record or another builder. */
    static Component resolve(Object childOrBuilder) {
        if (childOrBuilder instanceof UiBuilder b) return b.build();
        return (Component) childOrBuilder;
    }
}
