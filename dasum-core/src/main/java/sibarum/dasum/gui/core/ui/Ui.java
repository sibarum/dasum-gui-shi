package sibarum.dasum.gui.core.ui;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.theme.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the defensive GUI-building API - "Dasum on training wheels".
 * <p>
 * Builders ({@link #row()}, {@link #column()}, {@link #box()}, {@link #text},
 * {@link #scroll}, {@link #button}) construct the plain {@link Component}
 * records the manual constructors produce, but with never-null defaults and an
 * intent-named sizing vocabulary that makes correct layouts the default. The
 * records themselves now fail fast on the null crash-fields (see
 * {@code Component}'s canonical constructors), and {@link #check}/{@link #lint}
 * run a structural {@link LayoutLint} pass that flags legal-but-wrong layouts
 * before they render. All three layers apply equally to hand-written trees.
 */
public final class Ui {

    private static volatile boolean strict = true;

    private Ui() {}

    // ---- builder entry points ----

    /** A horizontal container (children left-to-right). Main axis = width. */
    public static FlexBuilder row()    { return new FlexBuilder(Direction.ROW); }
    /** A vertical container (children top-to-bottom). Main axis = height. */
    public static FlexBuilder column() { return new FlexBuilder(Direction.COLUMN); }
    /** A fixed-size styled rectangle holding at most one child. */
    public static BoxBuilder box()     { return new BoxBuilder(); }
    /** A text run (auto-sized, theme-colored by default). */
    public static TextBuilder text(String content)  { return new TextBuilder(content); }
    /** Alias for {@link #text(String)} - reads better for standalone labels. */
    public static TextBuilder label(String content) { return new TextBuilder(content); }
    /** A scrollable viewport around a child. */
    public static ScrollBuilder scroll(Component child) { return new ScrollBuilder(child); }
    public static ScrollBuilder scroll(UiBuilder child) { return new ScrollBuilder(child.build()); }
    /** A themed button; terminate with {@code .onClick(...)} to wire it. */
    public static ButtonBuilder button(String label) { return new ButtonBuilder(label); }

    // ---- common pattern helpers ----

    /** A flexible transparent gap that pushes siblings apart (grow=1). */
    public static Component spacer() {
        return new Component.Box(Em.ZERO, Em.ZERO, Em.ZERO, Color.TRANSPARENT, List.of(), false, 1);
    }

    /** A thin full-extent divider (a childless Flex, which - unlike Box - fills its cross axis). */
    public static Component hairline() {
        return hairline(Em.of(0.08f), new Color(0.25f, 0.27f, 0.32f, 1f));
    }

    public static Component hairline(Em thickness, Color color) {
        return new Component.Flex(
            null, thickness, Em.ZERO, color,
            Direction.ROW, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(), false, 0, false);
    }

    /** A titled section: a padded card with a heading, a divider, and the body. */
    public static Component section(String title, Component body) {
        return column().named("section:" + title)
            .padding(Em.of(0.8f)).gap(Em.of(0.5f)).background(Theme.subtleBg())
            .add(label(title).size(Em.of(1.15f)).build())
            .add(hairline())
            .add(body)
            .build();
    }

    // ---- lift an existing record into a builder (easy round-trip refactor) ----

    public static FlexBuilder from(Component.Flex f)     { return new FlexBuilder(f); }
    public static BoxBuilder from(Component.Box b)       { return new BoxBuilder(b); }
    public static TextBuilder from(Component.Text t)     { return new TextBuilder(t); }
    public static ScrollBuilder from(Component.Scroll s) { return new ScrollBuilder(s); }

    // ---- linting ----

    /** Turn strict mode on/off. Strict (default) throws on lint ERRORs; lenient logs them. */
    public static void strict(boolean on) { strict = on; }
    /** Shorthand for {@code strict(false)} - downgrade lint errors to logged warnings. */
    public static void lenient() { strict = false; }
    public static boolean isStrict() { return strict; }

    /** Run the structural lint and return every finding (nothing is thrown or logged). */
    public static List<Diagnostic> check(Component root) {
        return LayoutLint.check(root);
    }

    /**
     * Lint {@code root} and act on the current mode: in strict mode, throw an
     * aggregated {@link IllegalStateException} listing all ERRORs (WARNs are
     * logged); in lenient mode, log everything. Returns the diagnostics either
     * way. Call it once per rebuild in development to catch layout mistakes at
     * their source rather than as pixels.
     */
    public static List<Diagnostic> lint(Component root) {
        List<Diagnostic> diags = LayoutLint.check(root);
        List<Diagnostic> errors = new ArrayList<>();
        for (Diagnostic d : diags) {
            if (d.severity() == Diagnostic.Severity.ERROR && strict) {
                errors.add(d);
            } else {
                System.err.println("[ui-lint] " + d);
            }
        }
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Ui layout lint found ")
                .append(errors.size()).append(" error(s):");
            for (Diagnostic d : errors) sb.append("\n  ").append(d);
            throw new IllegalStateException(sb.toString());
        }
        return diags;
    }
}
