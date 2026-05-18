# dasum-msdf-maven-plugin

Build-time MSDF (multi-channel signed distance field) atlas generation for `dasum-core`'s text renderer. Two atlas flavors per invocation:

- **Text atlases** — a charset preset (`ascii`, `latin-1`) or a custom charset spec, baked from a TTF/OTF font.
- **Icon atlases** — a named subset of glyphs from an icon font (Lucide, Material Symbols, …) with a generated Java `Icons` class exposing one constant per glyph.

Same Mojo, same output shape (`<name>.png` + `<name>.json` per atlas). The plugin scans the bundled `msdf-atlas-gen.exe` (Windows x64) or expects pre-generated outputs on other platforms via the `msdf-prebuilt` profile.

## Goals

- **`generate-atlas`** — the only goal. Default phase `generate-resources`.

## Operating modes

| Mode | Property | Behavior |
|---|---|---|
| `primary` | `-Dmsdf.mode=primary` (default) | Invoke `msdf-atlas-gen.exe` to regenerate outputs. Incremental: skips regeneration if every output is newer than every input (font, manifest, glyph list). |
| `prebuilt` | `-Dmsdf.mode=prebuilt` | Skip the binary entirely. Verify pre-generated PNG + JSON exist at the configured output path. Used on macOS / Linux until the binary is bundled for those platforms. |

The Windows binary lives at `/bin/windows-x64/msdf-atlas-gen.exe` inside the plugin JAR. To support a new OS, drop the matching binary into the resources tree and extend `extractBinary` in `GenerateAtlasMojo`.

## Configuration reference

```xml
<plugin>
    <groupId>sibarum.dasum.gui</groupId>
    <artifactId>dasum-msdf-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <id>generate-atlases</id>
            <goals><goal>generate-atlas</goal></goals>
        </execution>
    </executions>
    <configuration>
        <outputDir>${project.basedir}/src/main/resources/dasum/atlas</outputDir>
        <atlases>
            <atlas><!-- … one block per atlas, see below … --></atlas>
        </atlases>
    </configuration>
</plugin>
```

### Per-atlas configuration

| Field | Required | Default | Notes |
|---|---|---|---|
| `name` | yes | — | Output basename. Files written as `<outputDir>/<name>.png` and `.json`. |
| `font` | yes | — | TTF / OTF input. |
| `charset` | text mode | `ascii` | `ascii` or `latin-1` presets, or any literal `msdf-atlas-gen` charset spec (passed through). Ignored when `<icons>` is set. |
| `atlasSize` | no | `1024` | Square atlas dimensions in pixels. |
| `fontSize` | no | `32` | Glyph em size in pixels within the atlas. |
| `pxRange` | no | `4` | SDF distance range in output pixels. |
| `icons` | icon mode | — | See below. Presence switches the atlas into icon mode. |

### `<icons>` block — icon-atlas configuration

| Field | Required | Default | Notes |
|---|---|---|---|
| `manifest` | yes | — | Path to the icon library's name → codepoint manifest. |
| `manifestFormat` | yes | — | `lucide` or `material` (see [Manifest formats](#manifest-formats)). |
| `glyphList` | one of | — | Inline icon names. Multiple names per element are split on commas. |
| `glyphListFile` | one of | — | Text file, one name per line. `#` comments and blank lines ignored. |
| `generatedSourcesDir` | yes | — | Root directory under which the generated `Icons.java` is written. Auto-added to the project's compile-source roots. |
| `packageName` | yes | — | Java package for the generated class. |
| `className` | no | `Icons` | Class name. |

## Manifest formats

### `lucide`

Lucide ships `lucide-static/font/info.json`, a JSON object keyed by icon name. Each entry has an `encodedCode` field whose value is `"\\<hex>"` (an escaped backslash followed by the codepoint as hex).

```json
{
  "a-arrow-down": {
    "encodedCode": "\\e585",
    "prefix": "icon",
    "className": "icon-a-arrow-down",
    "unicode": "&#58757;"
  },
  ...
}
```

The parser reads the object keys (icon names) and extracts `encodedCode` (stripping the leading backslash). The `unicode` field's HTML-entity form is ignored — `encodedCode` is the authoritative codepoint.

### `material`

Material Symbols / Material Icons ships a plain-text `codepoints` file with one entry per line:

```
search e8b6
settings e8b8
3d_rotation e84d
```

The first whitespace-separated token is the name; the second is the codepoint as a hex string (no `0x` prefix).

Blank lines and lines starting with `#` are ignored so users can carry notes inline.

### Adding a new format

`IconManifestParser.parse(file, format)` switches on the format name and dispatches to a private parser. Each parser returns a `LinkedHashMap<String, Integer>` preserving manifest order so codegen output is deterministic. Add a new case to the switch and a parser method — Phosphor (CSS), Font Awesome (YAML), and Bootstrap Icons (JSON) would each be 30–50 lines.

## Name normalization

Source-library icon names (`arrow-up`, `check_circle`, `3d_rotation`) become Java constants (`ARROW_UP`, `CHECK_CIRCLE`, `_3D_ROTATION`) by the following rules, in order:

1. Replace `-`, `_`, and any whitespace with `_`.
2. Uppercase.
3. Replace any non-`[A-Z0-9_]` character with `_`. Collapse consecutive `_` to one.
4. Strip trailing `_` (a source name that ended in a separator).
5. If the result starts with a digit, prefix `_` (`3d-rotation` → `_3D_ROTATION`).
6. If the result is a Java reserved word (`class`, `null`, `enum`, …), suffix `_` (`class` → `CLASS_`).

Examples:

| Source name | Library convention | Java constant |
|---|---|---|
| `arrow-up` | Lucide | `ARROW_UP` |
| `arrow_upward` | Material | `ARROW_UPWARD` |
| `check-circle` | Phosphor | `CHECK_CIRCLE` |
| `3d-rotation` | Material | `_3D_ROTATION` |
| `class` | hypothetical | `CLASS_` |

The normalizer is `IconNameNormalizer.normalize(String)`. Pure function, no state.

### Collision policy

If two source names normalize to the same Java constant — `arrow-up` and `arrow_up`, say — **the build fails** with a diagnostic listing every colliding source name. There is no silent overwrite or "first-wins" rule. To proceed, drop one of the conflicting names from your `<glyphList>` or supply an alias map (the alias mechanism isn't implemented yet — open work).

## Generated `Icons` class

The codegen emits, for each selected glyph:

```java
/** Source name: {@code "arrow-up"} */
public static final int ARROW_UP = 0xE585;
```

Plus a lazy `byName(String)` lookup that accepts both forms:

```java
Icons.byName("arrow-up")  // 0xE585
Icons.byName("ARROW_UP")  // 0xE585
Icons.byName("nope")      // -1
```

The lookup map is built on first call to keep `<clinit>` lightweight for apps that never go through the by-name path.

The generated file lives under `<generatedSourcesDir>/<packageName as path>/<className>.java`. The plugin adds `generatedSourcesDir` to `project.getCompileSourceRoots()` so the standard `maven-compiler-plugin` picks it up automatically — no extra `build-helper-maven-plugin` configuration needed.

## Worked example

```xml
<configuration>
    <outputDir>${project.basedir}/src/main/resources/dasum/atlas</outputDir>
    <atlases>
        <!-- Text atlas — the framework default. -->
        <atlas>
            <name>primary</name>
            <font>${project.basedir}/fonts/Noto_Sans/NotoSans-Regular.ttf</font>
            <charset>ascii</charset>
            <atlasSize>1024</atlasSize>
            <fontSize>32</fontSize>
            <pxRange>4</pxRange>
        </atlas>

        <!-- Icon atlas — Lucide. -->
        <atlas>
            <name>icons</name>
            <font>${project.basedir}/fonts/lucide/lucide.ttf</font>
            <atlasSize>1024</atlasSize>
            <fontSize>40</fontSize>
            <pxRange>4</pxRange>
            <icons>
                <manifest>${project.basedir}/fonts/lucide/info.json</manifest>
                <manifestFormat>lucide</manifestFormat>
                <glyphList>
                    search, settings, home, trash-2,
                    chevron-down, chevron-up, chevron-left, chevron-right,
                    folder, file, save, copy, edit, eye,
                    info, alert-triangle, help-circle
                </glyphList>
                <generatedSourcesDir>${project.build.directory}/generated-sources/dasum-msdf</generatedSourcesDir>
                <packageName>com.example.icons</packageName>
                <className>Icons</className>
            </icons>
        </atlas>
    </atlases>
</configuration>
```

App code then:

```java
import com.example.icons.Icons;
import sibarum.dasum.gui.core.text.Icon;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.core.text.AtlasData;
import sibarum.dasum.gui.core.render.Texture;

// at startup, after Gl.load():
Texture iconsTex = Texture.fromPngResource("/dasum/atlas/icons.png");
AtlasData iconsAtlas = AtlasData.loadFromResource("/dasum/atlas/icons.json");
FontGroups.register(FontGroup.of(Icon.DEFAULT_FONT_GROUP, iconsAtlas, iconsTex));

// at any call site:
Component save = Icon.of(Icons.SAVE, Em.of(1.1f), labelColor);
```

## Mixed font groups in a single frame

The framework's MSDF accumulator (`MsdfTextAccumulator`) batches glyph quads per frame and the active atlas texture is shared state. When a frame mixes text from one font group and icons from another, `Render.renderText` calls `Batcher.setTextAtlas(atlas, distanceRange, projection)` — the `projection`-taking overload flushes the accumulator with the outgoing atlas before swapping to the new one. Without this flush, all buffered glyphs of the frame would render with whichever atlas was bound last.

The older `Batcher.setTextAtlas(atlas, distanceRange)` overload (no projection) throws when it would drop pending geometry — kept around for cases where the caller has already drained the accumulator manually.

The cost of mixing font groups is therefore one extra `flush()` call per atlas swap per frame. For a typical UI with a handful of icons interleaved with text labels, that's a handful of extra draw calls — negligible. If a frame ever did thousands of atlas swaps, the right move would be a merged-atlas build step (icons + text in one PNG) and a single font group covering both — see the `dasum-vis/README.md` discussion of "Approach C".

## File-of-interest map

```
dasum-msdf-maven-plugin/
├── src/main/java/sibarum/dasum/gui/msdf/
│   ├── GenerateAtlasMojo.java     — the Mojo, dispatches text/icon modes
│   ├── AtlasConfig.java           — POJO for <atlas> blocks
│   ├── IconConfig.java            — POJO for <icons> sub-block
│   ├── IconManifestParser.java    — lucide + material parsers
│   ├── IconNameNormalizer.java    — source name → Java constant
│   └── IconsCodegen.java          — generates the Icons class
└── src/main/resources/bin/
    └── windows-x64/msdf-atlas-gen.exe
```
