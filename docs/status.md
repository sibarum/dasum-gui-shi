# The status bar (`sibarum.dasum.gui.core.status`)

A singleton ribbon at the bottom of the viewport, installed by wrapping the app root:

```java
Component root = Status.wrap(MainShell.build());
```

Its guiding principle: the status bar is a **ledger of what happened** — not a notifications
surface. Notification popups are ephemeral, push-based, and attention-capturing; they punish you for
not reading fast enough, then vanish. The ledger is the opposite: stable, pull-based, and lossless.
Every design choice below follows from that.

## The model

Every call **records** a `StatusEvent` in a session-long history (`events()`, capped at
`MAX_HISTORY`). Three **orthogonal** axes on the event decide how it is treated — deliberately kept
separate so the (future) log filter can slice on each independently:

| axis | values | meaning |
|---|---|---|
| **surface** (`alert`) | `true` / `false` | an *alert* briefly shows on the bar (then decays) and bumps the unseen counter; a plain entry is history-only |
| **severity** | `GOOD` / `NEUTRAL` / `BAD` | signed valence, shown as a *faint* tint |
| **channel** | `USER` / `TECHNICAL` | audience — the axis the filter will slice on |

### Logs vs alerts

"Should this be recorded?" (almost always yes) is a different question from "should this surface to
the user?" (rarely). So:

- **History-only** — `log(msg)`, `technical(msg)`: a quiet note. No surface, no counter bump. The
  event firehose lives here.
- **Alerts** — `alert(msg, severity)`, plus `good(msg)` / `bad(msg)` / `notify(msg)`: briefly surface
  on the bar (auto-reverting after `MESSAGE_DURATION_MS`) **and** increment the unseen-alert counter.

Alerts *are* logged too (they land in the same history), so there is never an alert without a
corresponding record — the alert and the log are one feature, not two that can drift out of sync.

### Idle state: a seen-counter, not a message

When no alert is active, the leading zone shows a muted **"N new"** — the count of alerts logged
since they were last **seen**. Opening the log popup (or calling `markSeen()`) acknowledges them and
zeroes the count: *the pull-gesture is the acknowledgment* — there is no "mark as read" button. The
counter never flashes or animates on increment, and it is colour-neutral regardless of what it
aggregates. There is deliberately **no app-settable idle/default message** — a persistent app string
in the idle slot is just a fake log; contextual tips belong to the Everything Menu.

### Severity tint: faint, static, theme-aware

`GOOD`/`BAD` show as a small, static nudge from the base text colour toward the theme's
success/error shade (via `Palette`) — never a saturated colour, a background fill, an icon, or
animation. The target is the perceptual band that is **pre-attentive but not attention-capturing**:
readable to someone who glances over primed for it, invisible to someone mid-thought. It rewards
attention instead of demanding it. The tint appears on the transient alert and the history rows,
never on the idle counter. (The mix factor is a tuned constant — verify it on-screen in both themes.)

## Two orthogonal slots the app owns

Beyond the ledger, the bar exposes two app-driven capabilities that are **not** logs:

- **Docked field** — `setDockedMessage(text[, severity])` / `dockedMessage()`. A persistent,
  trailing (right-anchored) indicator: an editor's `Ln 12, Col 4`, a mode badge, a clock. It survives
  every alert/idle refresh of the leading zone and never overlaps it (the leading zone
  shrinks/ellipsizes first).
- **Contextual override** — `setContextualMessage(text[, severity])` / `clearContextualMessage()`. A
  *transient, externally-driven* message that overrides the leading zone while set, outranking an
  active alert and the idle counter, and reverting the instant it is cleared. Another component
  toggles it from live user interaction — a compile error at the editor caret, a hover hint, a
  shortcut tip. It is pure display: it records no history, bumps no counter, sets no active event.
  This is the correct home for "a tooltip that renders in the bar" — distinct from the rejected idle
  default-message, because it is live and interaction-driven, not a parked string.

Leading-zone priority: **contextual override → active alert → "N new" counter / affordance**.

## Threading

Every log/setter is safe to call from any thread. The auto-revert runs on a daemon timer; display
mutations go through `DynamicChildren` under a static lock and wake the render loop via
`Invalidator`.

---

## Future directions (not yet built)

Three ideas ruled in but deferred, to keep the ledger useful at scale:

1. **Temporal grouping + disposition extraction.** A burst of near-simultaneous events (four logs
   that all say roughly the same thing, with one that matters lost in the shuffle) should collapse
   into a single summary whose **disposition** is extracted from the group — `GOOD`/`BAD` outranking
   `NEUTRAL`. The summary (and the idle counter / docked field) would then read its disposition, e.g.
   `(No failures)` for an all-good burst, `(+5 line trace)` for a bad one, `(+4 other logs)` for a
   neutral one. Grouping temporally-adjacent events and reporting their net disposition, rather than
   listing each.

2. **Repetitive-logger consolidation.** With hundreds or thousands of entries, the history gets
   unusable. Repetitive loggers (the same call site firing over and over) should consolidate into a
   single **traceable** event with a count, rather than N near-identical rows — dedup by origin while
   keeping the trail.

3. **The log filter.** The `channel` axis (and `severity`) exist so the history popup can filter —
   a user hides `TECHNICAL` noise and sees `USER` outcomes; a developer flips it. Deferred, but the
   data model already carries what it needs.
