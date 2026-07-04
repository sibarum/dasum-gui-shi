package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.Component;

/**
 * A fluent builder that produces a plain {@link Component} record. Every
 * builder terminates in {@link #build()}, which returns the exact same
 * record type the manual constructors produce — so builder-made and
 * hand-written subtrees mix freely (a builder can {@code .add(...)} a raw
 * record, and a {@code build()} result drops into any manual
 * {@code List.of(...)}).
 */
public interface UiBuilder {

    /** Construct the underlying component record (validated by its canonical constructor). */
    Component build();
}
