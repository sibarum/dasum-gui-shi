package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;

import java.util.ArrayList;
import java.util.List;

/**
 * The "training wheels": a structural lint pass over a built {@link Component}
 * tree that catches the legal-but-wrong layouts the compiler and the record
 * constructors can't - the ones that render as collapsed/overlapping/clipped
 * pixels with no error. It walks the declared tree (so hidden tab panels are
 * checked too) with parent context, and reports every finding at once with a
 * readable path, rather than failing one-at-a-time.
 * <p>
 * It works on any tree - builder-made or hand-written - because it inspects the
 * records, not the builders. Rules are derived from the layout algorithm's
 * documented behavior (see {@code layout/Layout.java}).
 */
public final class LayoutLint {

    private LayoutLint() {}

    /** Collect all diagnostics for the tree rooted at {@code root}. */
    public static List<Diagnostic> check(Component root) {
        List<Diagnostic> out = new ArrayList<>();
        if (root != null) walk(root, null, "", 0, out);
        return out;
    }

    private static void walk(Component node, Component parent, String parentPath,
                             int indexInParent, List<Diagnostic> out) {
        String path = parentPath.isEmpty()
            ? descriptor(node, indexInParent)
            : parentPath + " > " + descriptor(node, indexInParent);

        nodeRules(node, path, out);
        childRules(node, parent, path, out);

        List<Component> kids = declaredChildren(node);
        for (int i = 0; i < kids.size(); i++) {
            walk(kids.get(i), node, path, i, out);
        }
    }

    // ---- rules that only need the node itself ----

    private static void nodeRules(Component node, String path, List<Diagnostic> out) {
        if (node instanceof Component.Box box && box.children().size() > 1) {
            out.add(new Diagnostic(Diagnostic.Severity.ERROR, path, "box-multi-child",
                "a Box centers every child on top of the others, but this one has "
                    + box.children().size() + " children - they will overlap",
                "use Ui.row()/Ui.column() to lay multiple children out in a flow"));
        }

        if (node instanceof Component.Flex flex) {
            boolean anyGrow = false;
            for (Component c : flex.children()) if (c.flexGrow() > 0) { anyGrow = true; break; }

            if (flex.wrap() && flex.direction() == Direction.COLUMN) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, path, "wrap-on-column",
                    "wrap() is honored on ROW only; a COLUMN never wraps",
                    "switch to Ui.row().wrap(), or drop wrap()"));
            }
            if (flex.justify() != JustifyContent.START && anyGrow) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, path, "justify-vs-grow",
                    "justify=" + flex.justify() + " is a no-op when a child grows - grow"
                        + " consumes all leftover main-axis space first",
                    "drop the grow() weights, or use justify with fixed-size children"));
            }
            flexOverflowRule(flex, path, out);
        }

        if (node instanceof Component.Scroll scroll) {
            scrollRule(scroll, path, out);
        }
    }

    /** A fixed-main-size Flex whose fixed children (plus gaps) already exceed it will clip/overflow. */
    private static void flexOverflowRule(Component.Flex flex, String path, List<Diagnostic> out) {
        Em mainEm = flex.direction() == Direction.ROW ? flex.width() : flex.height();
        if (!isFixed(mainEm)) return;                 // fill/fit parents don't overflow this way
        List<Component> kids = flex.children();
        if (kids.isEmpty()) return;
        float sum = 0f;
        for (Component c : kids) {
            Em cm = flex.direction() == Direction.ROW ? width(c) : height(c);
            if (!isFixed(cm)) return;                 // can't reason unless all children are fixed
            sum += cm.value();
        }
        sum += flex.gap().value() * (kids.size() - 1);
        float avail = mainEm.value() - 2f * flex.padding().value();
        if (sum > avail + 1e-4f) {
            out.add(new Diagnostic(Diagnostic.Severity.WARN, path, "fixed-overflow",
                "children need " + trim(sum) + "em on the main axis but the fixed parent"
                    + " offers " + trim(avail) + "em - they overflow/clip",
                "enlarge the parent, shrink children, wrap it in Ui.scroll(), or use grow()"));
        }
    }

    private static void scrollRule(Component.Scroll scroll, String path, List<Diagnostic> out) {
        Component child = scroll.child();
        if (child == null) return;
        // Vertical is the dominant scroll direction and the common footgun: a
        // Scroll bounded in height whose child fills that height (height=null)
        // stretches to the viewport instead of overflowing, so it never scrolls.
        // (A fixed-width vertical scroller with a fill-width child is normal, so
        // we deliberately don't flag the horizontal axis and spam that case.)
        if (isFixed(scroll.height()) && isFill(height(child))) {
            out.add(new Diagnostic(Diagnostic.Severity.WARN, path, "scroll-cant-scroll",
                "Scroll has a fixed height but its child fills it (height=null), so it can"
                    + " never scroll vertically",
                "give the child .fit() (Em.AUTO) or an explicit height so its content can overflow"));
        }
    }

    // ---- rules that need the parent relationship ----

    private static void childRules(Component node, Component parent, String path, List<Diagnostic> out) {
        if (!(parent instanceof Component.Flex pflex)) return;

        // The headline bug: a Flex child with grow=0 and a null MAIN-axis size
        // measures to 0 and collapses (siblings stack at the same origin).
        if (node instanceof Component.Flex && node.flexGrow() == 0) {
            Em main = pflex.direction() == Direction.ROW ? width(node) : height(node);
            if (isFill(main)) {
                String axis = pflex.direction() == Direction.ROW ? "width" : "height";
                out.add(new Diagnostic(Diagnostic.Severity.ERROR, path, "collapse-to-zero",
                    "this Flex has " + axis + "=null (fill) with grow=0 inside a "
                        + pflex.direction() + " - as a measured child it collapses to 0 and"
                        + " overlaps its siblings",
                    "call .fit() (size to content), .grow(1) (take leftover space), or set an explicit "
                        + axis));
            }
        }

        // Under STRETCH the child's explicit cross-axis size is silently ignored.
        if (pflex.align() == AlignItems.STRETCH) {
            Em cross = pflex.direction() == Direction.ROW ? height(node) : width(node);
            if (isFixed(cross)) {
                String axis = pflex.direction() == Direction.ROW ? "height" : "width";
                out.add(new Diagnostic(Diagnostic.Severity.WARN, path, "stretch-ignores-size",
                    "explicit " + axis + " is ignored - the parent's align=STRETCH stretches this"
                        + " child across the cross axis",
                    "set the parent align to START/CENTER/END to honor the " + axis
                        + ", or drop the explicit size"));
            }
        }
    }

    // ---- tree + geometry helpers ----

    private static List<Component> declaredChildren(Component c) {
        return switch (c) {
            case Component.Box b   -> b.children();
            case Component.Flex f  -> f.children();
            case Component.Scroll s -> s.child() != null ? List.of(s.child()) : List.of();
            case Component.GraphSurface g -> g.children();
            case Component.Tabs t  -> {
                List<Component> out = new ArrayList<>();
                for (Component.Tabs.TabPanel p : t.tabs()) if (p.content() != null) out.add(p.content());
                yield out;
            }
            default -> List.of();
        };
    }

    /** Width Em for the variants that carry one; {@code null} for leaves without the field. */
    private static Em width(Component c) {
        return switch (c) {
            case Component.Box b   -> b.width();
            case Component.Flex f  -> f.width();
            case Component.Scroll s -> s.width();
            case Component.Text t  -> t.width();
            case Component.Tabs t  -> t.width();
            case Component.GraphSurface g -> g.width();
            case Component.SceneView v -> v.width();
            case Component.DataTable d -> d.width();
            default -> null;
        };
    }

    private static Em height(Component c) {
        return switch (c) {
            case Component.Box b   -> b.height();
            case Component.Flex f  -> f.height();
            case Component.Scroll s -> s.height();
            case Component.Text t  -> t.height();
            case Component.Tabs t  -> t.height();
            case Component.GraphSurface g -> g.height();
            case Component.SceneView v -> v.height();
            case Component.DataTable d -> d.height();
            default -> null;
        };
    }

    private static boolean isFill(Em e)  { return e == null; }
    private static boolean isFixed(Em e) { return e != null && !e.isAuto(); }

    private static String descriptor(Component node, int index) {
        String label = UiLabels.get(node);
        String type = typeName(node);
        if (label != null) return type + " '" + label + "'";
        return index > 0 ? type + "#" + index : type;
    }

    private static String typeName(Component c) {
        return switch (c) {
            case Component.Flex f  -> f.direction() == Direction.ROW ? "row" : "column";
            case Component.Box b   -> "box";
            case Component.Scroll s -> "scroll";
            case Component.Text t  -> "text";
            case Component.Tabs t  -> "tabs";
            case Component.GraphSurface g -> "graph";
            case Component.SceneView v -> "scene";
            case Component.DataTable d -> "table";
            case Component.Checkbox cb -> "checkbox";
            case Component.Radio<?> r -> "radio";
            case Component.Slider s -> "slider";
        };
    }

    private static String trim(float v) {
        return String.valueOf(Math.round(v * 100f) / 100f);
    }
}
