package sibarum.dasum.gui.core.ui;

/**
 * One layout-lint finding produced by {@link LayoutLint}. A diagnostic points
 * at a node by its readable tree path (using {@code .named(...)} labels where
 * present), names the rule, describes what will go wrong, and suggests a fix.
 */
public record Diagnostic(Severity severity, String path, String rule, String message, String fixHint) {

    public enum Severity { ERROR, WARN }

    @Override
    public String toString() {
        String s = severity + "  " + path + " : " + message;
        return fixHint == null ? s : s + "  (fix: " + fixHint + ")";
    }
}
