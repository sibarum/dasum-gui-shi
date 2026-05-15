package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;

/**
 * A connection between two ports on the same {@code GraphSurface}.
 * <p>
 * When one end has a defined direction (INPUT or OUTPUT), {@code from} is
 * the output side and {@code to} is the input side — that lets a future
 * curve renderer always flow from→to. When both ends are
 * {@link PortDirection#BIDIRECTIONAL}, ordering is whatever was passed to
 * {@link Connections#add}; the relationship is symmetric.
 */
public record Connection(Component from, Component to) {}
