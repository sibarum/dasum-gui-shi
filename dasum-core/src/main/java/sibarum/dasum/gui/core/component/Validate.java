package sibarum.dasum.gui.core.component;

import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

/**
 * Fail-fast argument checks shared by {@link Component}'s canonical
 * constructors. The whole point is to convert the framework's classic
 * "null set here, {@code NullPointerException} five frames away in the render
 * pass" bugs into an immediate, self-describing {@link IllegalArgumentException}
 * at the exact construction site - for hand-written record code just as much as
 * for the {@code Ui.*} builders.
 * <p>
 * These guard only the <em>crash-null</em> fields (colors, non-sentinel
 * {@link Em}s, enums, child lists, reactive cells). The legitimate {@code null}
 * sentinels - {@code width}/{@code height} = "fill parent",
 * {@code Text.wrapWidth} = "no wrap", {@code Scroll.child} = "empty viewport",
 * {@code Tabs.onTabPressed}/{@code overflowGlyph}, {@code Radio.value},
 * {@code TabPanel.content} - are deliberately left unchecked.
 */
final class Validate {

    private Validate() {}

    /** A {@link Color} field that is dereferenced during rendering; null crashes late. */
    static Color color(Color c, String type, String field) {
        if (c == null) {
            throw new IllegalArgumentException(
                type + " " + field + " is null; use Color.TRANSPARENT for no fill");
        }
        return c;
    }

    /**
     * A dimension that must be a concrete length. Unlike Flex/Scroll, a
     * {@code Box} has no "fill parent" (null) or "fit content" ({@code Em.AUTO})
     * layout path - either yields a crash or a {@code NaN} rect - so its
     * width/height must be a real {@code Em.of(...)}.
     */
    static Em fixed(Em e, String type, String field) {
        if (e == null) {
            throw new IllegalArgumentException(
                type + " " + field + " is null; " + type + " needs a concrete Em size"
                    + " - use a Flex/Scroll to fill the parent or fit content");
        }
        if (e.isAuto()) {
            throw new IllegalArgumentException(
                type + " " + field + " is Em.AUTO; " + type + " needs a concrete size,"
                    + " not fit-content - use a Flex to fit content");
        }
        return e;
    }

    /** A non-sentinel {@link Em} (padding, gap, a fixed size); null crashes at {@code toPixels()}. */
    static Em em(Em e, String type, String field) {
        if (e == null) {
            throw new IllegalArgumentException(
                type + " " + field + " is null; use Em.ZERO or a fixed Em"
                    + " (null is only a valid 'fill parent' sentinel for width/height)");
        }
        return e;
    }

    /** Any required reference (enum, Property, source, String content, …). */
    static <T> T required(T v, String type, String field) {
        if (v == null) {
            throw new IllegalArgumentException(type + " " + field + " is null");
        }
        return v;
    }

    /** A children list that gets iterated by layout/render/hit-test; null (or a null element) crashes late. */
    static <T> List<T> children(List<T> list, String type, String field) {
        if (list == null) {
            throw new IllegalArgumentException(
                type + " " + field + " list is null; use List.of() for none");
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                throw new IllegalArgumentException(
                    type + " " + field + "[" + i + "] is null");
            }
        }
        return list;
    }

    /** Per-child flex weight must be non-negative (a negative skews grow distribution). */
    static void flexGrow(int g, String type) {
        if (g < 0) {
            throw new IllegalArgumentException(
                type + " flexGrow is " + g + "; must be >= 0");
        }
    }

    /** A slider's range must be non-empty and correctly ordered. */
    static void range(float min, float max, String type) {
        if (!(min < max)) {
            throw new IllegalArgumentException(
                type + " needs min < max, got min=" + min + " max=" + max);
        }
    }
}
