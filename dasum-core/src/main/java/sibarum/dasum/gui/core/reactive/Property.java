package sibarum.dasum.gui.core.reactive;

import sibarum.dasum.gui.core.event.Invalidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reactive value cell. {@link #set(Object)} updates the value, notifies
 * subscribers, and auto-invalidates the global dirty flag (via
 * {@link Invalidator}).
 * <p>
 * Listener iteration is copy-on-iterate so handlers may freely subscribe
 * or unsubscribe other listeners without {@code ConcurrentModificationException}.
 * <p>
 * Marked {@code final} to avoid premature variant explosion — see the
 * stored design decision on retained-mode + reactive auto-invalidate.
 */
public final class Property<T> {

    private T value;
    private final List<Consumer<T>> listeners = new ArrayList<>();

    public Property(T initial) {
        this.value = initial;
    }

    public T get() { return value; }

    public void set(T newValue) {
        if (Objects.equals(value, newValue)) return;
        value = newValue;
        for (Consumer<T> listener : List.copyOf(listeners)) {
            listener.accept(newValue);
        }
        Invalidator.invalidate();
    }

    /** Subscribe to value changes. Returns {@code this} for chaining. */
    public Property<T> subscribe(Consumer<T> listener) {
        listeners.add(listener);
        return this;
    }

    public void unsubscribe(Consumer<T> listener) {
        listeners.remove(listener);
    }
}
