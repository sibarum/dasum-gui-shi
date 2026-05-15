package sibarum.dasum.gui.core.graph;

/**
 * Direction of data / relationship flow at a port.
 * <ul>
 *   <li>{@link #INPUT} — accepts incoming connections only.</li>
 *   <li>{@link #OUTPUT} — originates outgoing connections only.</li>
 *   <li>{@link #BIDIRECTIONAL} — can act as either end. Used for graphs
 *       (mind maps, dependency views, social networks) where the relation
 *       isn't a flow.</li>
 * </ul>
 * Connection rule: two ports can connect iff at least one end can output
 * AND at least one end can input ({@link #canOutput} / {@link #canInput}).
 * That means INPUT↔INPUT and OUTPUT↔OUTPUT are forbidden; everything else
 * works as long as the types are compatible.
 */
public enum PortDirection {
    INPUT, OUTPUT, BIDIRECTIONAL;

    public boolean canInput()  { return this != OUTPUT; }
    public boolean canOutput() { return this != INPUT; }
}
