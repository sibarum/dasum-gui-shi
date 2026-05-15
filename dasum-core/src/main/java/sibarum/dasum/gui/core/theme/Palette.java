package sibarum.dasum.gui.core.theme;

import sibarum.dasum.gui.core.render.Color;

/**
 * Three-color slot for a {@link Variant}: the saturated brand color
 * ({@code base}), a softer text-tinted version ({@code emphasis}), and
 * the readable foreground when text sits on top of {@code base}
 * ({@code onBase}).
 * <p>
 * Mapping to widgets:
 * <ul>
 *   <li>Themed button bg = {@code base}, label = {@code onBase}</li>
 *   <li>Themed checkbox / radio / slider — accent color = {@code base}</li>
 *   <li>Themed text label color = {@code emphasis}</li>
 * </ul>
 */
public record Palette(Color base, Color emphasis, Color onBase) {}
