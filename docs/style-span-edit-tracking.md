# Design note: edit-tracking for `TextStyleStates` spans

**Status:** proposed (not yet implemented)
**Scope:** `dasum-core` — `TextStyleStates`, `TextInputController`
**Motivating bug:** host highlighter spans (syntax colors, body-block tints)
detach from the text on edit, because `TextStyleStates` does not remap ranges
across `setContent`. Today it only clips out-of-range spans at render time and
tells callers to "subscribe and republish" (see the class docstring).

## Principle

Lower cognitive load: nothing should ever be visibly *out of alignment*, not
even for a frame. A highlight that lags the caret by a split second is worse
than one that updates a beat late but always sits where it belongs.

That splits the work cleanly into two layers with **different timing**:

| Layer | What | Timing |
|-------|------|--------|
| Geometric remap | shift/shrink/drop span offsets so fills stay glued to their characters | **immediate & exact** — never debounced |
| Semantic refresh | host re-runs its highlighter to recompute *what* to color | **debounced ~100 ms** |

Never debounce the geometric layer — that is the one that keeps things aligned.

## Layer 1 — geometric remap (in `dasum-core`)

`TextInputController.replaceRange` is the single choke point for every edit and
already knows the exact `(from, to, replacement)`. Emit a remap from there:

```java
TextStyleStates.onEdit(text, from, to /* old end */, replacement.length());
```

`onEdit` remaps both channels (`fg`, `bg`) against `[from, to) -> from +
insertedLen`. Per span, given the edit `delta = insertedLen - (to - from)`:

- span entirely **before** `from` → unchanged
- span entirely **after** `to` → shift both ends by `delta`
- edit strictly **inside** a span (`from >= span.start && to <= span.end`) →
  grow/shrink: `end += delta` (keeps a highlighted word highlighted as you type
  into it)
- edit **straddles** a span boundary (partial overlap) → **drop the span**.
  "No claim beats a wrong claim"; the debounced refresh restores truth shortly.

Exact-edit info beats a prefix/suffix diff, so this is both cheaper and more
precise than reconstructing the edit from two full-text snapshots.

**Payoff:** `pontif-playground`'s `AltHighlighter` can then delete its
`shiftSpans` / `changedSpan` / `CARRY_MAX_EDIT` carry-across-broken-parse
machinery — that hack exists *only* because it has to guess edits from
snapshots. With choke-point remap it is obsolete.

## Layer 2 — debounced semantic refresh (host app)

The host's `onContentChange` highlighter (e.g. `App.applyHighlight`) should run
on a ~100 ms debounce so a burst of keystrokes recomputes once. Layer 1 keeps
existing spans aligned during the debounce window, so there is no flicker while
the recompute is pending.

## Interaction with the Find bar (already shipped)

The Find bar now **closes on the first target edit** (`FindBar.attachEditGuard`),
so its match spans never need edit-tracking — they only exist while the target
is unedited. Select and copy (non-edits) stay available while open. This note
is about the *host highlighter's* spans, which live independently of Find.
