package sibarum.dasum.gui.core.em;

/** Em-space rectangle. (x, y) is top-left in a Y-down convention. */
public record EmRect(Em x, Em y, Em width, Em height) {

    public static EmRect xywh(float x, float y, float w, float h) {
        return new EmRect(Em.of(x), Em.of(y), Em.of(w), Em.of(h));
    }

    public EmRect inset(Em padding) {
        Em two = padding.times(2f);
        return new EmRect(
            x.plus(padding),
            y.plus(padding),
            new Em(Math.max(0f, width.value() - two.value())),
            new Em(Math.max(0f, height.value() - two.value()))
        );
    }
}
