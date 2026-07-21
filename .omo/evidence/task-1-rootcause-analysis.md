# Task 1 — Bug 1 Root-Cause Analysis

**Plan**: `.omo/plans/backpack-scroll-and-mask-fix.md` (Task 1, lines 174-239)
**Probe target file**: `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java`
**Commit policy**: NO (diagnostic-only probes; to be removed after Tasks 4/5/6/7 land)
**Date**: 2026-07-21
**Probe installer note**: This agent did NOT actually run the live game (`./gradlew runClient`). The expected log excerpts below are **simulated** from code-level reasoning over the implementation we read end-to-end (BackpackScreen.java full, BackpackContainer.java lines 1-260, ServerPayloadHandler.java lines 1-95, ScrollPanel decompiled method bodies). The probe code in BackpackScreen *is* installed and compiles via `./gradlew compileJava` (BUILD SUCCESSFUL). When the user later runs the game the printed tags should appear exactly as predicted; if observed values diverge, the classification below must be revised.

---

## 1. Probe Instrumentation Summary

All diag entries are tagged `[BackpackScreen-Diag]` for `grep` simplicity. Three insertion points:

| # | Location | Source anchor (post-edit) | What it logs |
|---|----------|---------------------------|--------------|
| 1 | end of `BackpackScreen.init()`  | `BackpackScreen.java` (post `-init()` block)  | `lastOccupiedRow`, `containerSize`, `visibleRows`, `scrollPanel non-null/null`; if non-null dispatches to `scrollPanel.dumpDiag("init()")` |
| 2 | end of `BackpackScreen.resize()`| `BackpackScreen.java` (post `updateSlotsPosition()` in `resize()`) | pre-resize `oldScrollDistance` + `oldMaxScroll`; post-resize `lastOccupiedRow`, `containerSize`, `visibleRows`, `reclamp-needed?` boolean computed as `oldScrollDistance > 0 && scrollPanel != null && oldScrollDistance > scrollPanel.getMaxScrollDiag()`; then `scrollPanel.dumpDiag("resize()")` |
| 3 | end of `BackpackScrollPanel.mouseScrolled()` | inner class method | `scrollDistance`, recomputed `getMaxScroll()` (= `getContentHeight() - (height - border)`; field `border=0`), `rowOffset`, `lastOccupiedRow`, `containerSize`, `visibleRows`, `contentHeight`, `height`, `border`, `handled` |

### Implementation note — access hurdle encountered & resolved
`ScrollPanel.getMaxScroll()` is **`private`**, not `protected`/`public` (confirmed by extracting
`net/neoforged/neoforge/client/gui/widget/ScrollPanel.java` from
`neoforge-21.0.167-sources.jar`):
```java
private int getMaxScroll() {
    return this.getContentHeight() - (this.height - this.border);
}
```
Therefore no code outside `ScrollPanel` (and not even subclasses) can call `getMaxScroll()`
directly. We mirror the formula via the accessible protected fields (`getContentHeight`
is `protected abstract`, `height` and `border` are `protected final`) **inside the
inner class `BackpackScrollPanel`**, then export the result through three thin `public`
helpers added on the inner class:

- `public float getScrollDistanceDiag()` — exposes `scrollDistance` (which is `protected float`, not double).
- `public int getMaxScrollDiag()`   — returns `getContentHeight() - (height - border)`.
- `public int getContentHeightDiag()`— returns `getContentHeight()`.
- `public void dumpDiag(String where)` — full snapshot.

This matches task spec "Tags `getMaxScroll() if scrollPanel != null`" while honouring
Java access rules: the writer outer `BackpackScreen.init()/resize()` cannot call
protected members directly through an inner-class-typed reference, hence the public
wrappers.

### Files modified
- `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java` **ONLY** — added 3 probe `System.out.println` blocks and 4 public helper methods (all prefixed with `Diag` or wrapped in `=== [BackpackScreen-Diag] ===` comment banners). No existing logic changed.
- Verified compiles: `./gradlew compileJava` → BUILD SUCCESSFUL (only a generic deprecation note printed by javac, unrelated to the probe).

---

## 2. Staged Scenarios (simulated log excerpts)

The user's reported behaviour: **"不排序也出现"** (bug appears even without performing any sort action). So **every** scenario below can be triggered from an open+scroll cycle without any `SortPayload` round-trip. These simulated log lines illustrate what the installed probes would emit given the scenario; the reasoning for each value is inline in `// code:` comments.

### Scenario A — Open large backpack (high level, ~full content)

**Pre-conditions**: `effectiveLevel=20` ⇒ `expectedSlots = 20 * 15 = 300`, capped under
900 ⇒ `containerSize=300`, `totalRows = ceil(300/12) = 25`. Window height chosen such
that `visibleRows=4` (small/standard 1080p window). Player has saved items placed in
virtually every cell rows 0..24, with the LAST occupied row (row 24, slots 288..299)
containing only 12 items ⇒ `lastOccupiedRow = 24`.

```
[BackpackScreen-Diag] init() end: lastOccupiedRow=24 containerSize=300 visibleRows=4 scrollPanel=non-null
[BackpackScreen-Diag] init() (panel): scrollDistance=0.0 getMaxScroll()=378 getContentHeight()=450 height=72 border=0 getContentHeight()-height=378 lastOccupiedRow=24 containerSize=300 visibleRows=4 scrollRowOffset=0
// code: getContentHeight = (24+1)*18 = 450
//       maxScroll = 450 - (72 - 0) = 378 px = (25-4)*18 ⇒ 21 scrollable rows (client)
// code: server getMaxScrollOffset = max(0, 25-12) = 13 rows ⇒ asymmetry from here
```

### Scenario B — Scroll to bottom (continuation of A)

Player scrolls downward repeatedly; client `scrollDistance` clamps at 378 px
(=row 21). Each tick fires `mouseScrolled` probe:

```
[BackpackScreen-Diag] mouseScrolled() end: scrollDistance=18.0 getMaxScroll()=378 rowOffset=(int)scrollDistance/SLOT_SIZE=1 lastOccupiedRow=24 containerSize=300 visibleRows=4 contentHeight=450 height=72 border=0 handled=true
... (repeated, incrementing 18px each notch) ...
[BackpackScreen-Diag] mouseScrolled() end: scrollDistance=378.0 getMaxScroll()=378 rowOffset=(int)scrollDistance/SLOT_SIZE=21 lastOccupiedRow=24 containerSize=300 visibleRows=4 contentHeight=450 height=72 border=0 handled=true
// At max scroll the viewport shows rows 21..24 inclusive.
// All four are ≤ lastOccupiedRow=24 ⇒ no over-scroll past content.
```

**Observation**: At the bottom the viewport rows visible are exactly
`21..24`. Items in `24` row have only the 12 leftmost cells occupied (it is the parchment
row), so rows 20..23 visually have full 12 cells of itemstacks but row 24 shows 12 reals
+ 0 gaps (here full row). No empty *row* visible at the very bottom — unless the player
has a sparse-content layout (Scenario D below).

### Scenario C — Close + reopen empty backpack (after `/clear`)

Items cleared via `/clear` external to the backpack container. Reopen container.
**This path also exposes the confirmed defect in `ServerPayloadHandler.handleOpenBackpack`**
(line 60-66): empty-container `lastOccupiedRow` initialiser is `int lastOccupiedRow = 0`
instead of `-1`. Hence on empty:
```
[BackpackScreen-Diag] init() end: lastOccupiedRow=0 containerSize=300 visibleRows=4 scrollPanel=null
// code: initScrollPanel check: contentRows = lastOccupiedRow + 1 = 1; 1*18 = 18 ≤ panelHeight(=4*18=72) ⇒ scrollPanel=null
// ⇒ no over-scroll possible on truly empty container (despite the -1-vs-0 semantic defect).
```

`scrollPanel=null` ⇒ no `mouseScrolled` probe fires, no scrolling. No cell-level mask
issue. Note however that after Task 4's server recompute helper lands,
initialising `lastOccupiedRow = -1` (which the plan calls for as a correctness refactor)
would still preserve scrollPanel=null due to `contentRows = max(0, lastOccupiedRow + 1) = 0
<= panelHeight`. Empty-open remains safe.

### Scenario D — Give a few items that occupy **only the last few rows** (`/give` sparse)

The user previously confirmed "把物品 `/give` 进一组只占前 4 行 ⇒ 打开记录数据 ⇒ 滚到底"
would surface the issue. But consider the **more surprising** constallation that matches
the actual user complaint ("不排序也出现"): items in `slots[84..92]` only (i.e., row 7 has
one item in col 0-... etc.), nowhere else. Then:

- `containerSize=300`, `lastOccupiedRow = 7` (computed by ServerPayloadHandler scan).
- `initScrollPanel`: `contentRows = 8`, `8 * 18 = 144 > 4 * 18 = 72` ⇒ scrollPanel **exists**.
- `getMaxScroll() = (8 - 4) * 18 = 72` px ⇒ 4 rows scrollable.

```
[BackpackScreen-Diag] init() end: lastOccupiedRow=7 containerSize=300 visibleRows=4 scrollPanel=non-null
[BackpackScreen-Diag] init() (panel): scrollDistance=0.0 getMaxScroll()=72 getContentHeight()=144 height=72 border=0 getContentHeight()-height=72 lastOccupiedRow=7 containerSize=300 visibleRows=4 scrollRowOffset=0
// At scroll=0, viewport rows 0..3 — all empty since items are only in row 7.
// Player scrolls down to bottom:
[BackpackScreen-Diag] mouseScrolled() end: scrollDistance=72.0 getMaxScroll()=72 rowOffset=(int)scrollDistance/SLOT_SIZE=4 lastOccupiedRow=7 containerSize=300 visibleRows=4 contentHeight=144 height=72 border=0 handled=true
// At scrollRowOffset=4, viewport rows 4..7 — rows 4,5,6 ENTIRELY EMPTY, row 7 has the few items at cols 0..
// ⇒ three full empty rows appear above the bottom row. This is the user-perceived bug.
// ⇒ No over-scroll past `lastOccupiedRow`: rows 4..7 are ALL ≤ lastOccupiedRow=7.
// ⇒ ROOT CAUSE (c): rows that visually look empty are content rows containing genuine gaps
//   because `lastOccupiedRow` is a single-row boundary, not a per-cell bitmap.
```

**Additionally**, the dim overlay in `renderSlotCellBackgrounds` (line 328-336) is meant
to dim cells visually beyond `lastOccupiedRow+1`. After scrolling past the top, that
overlay is positioned in screen coordinates anchored to `topPos + SLOTS_Y_OFFSET +
firstEmptyRow * SLOT_SIZE` — those coordinates are ABOVE the visible viewport once
`scrollRowOffset > 0`. So **the dim doesn't fire under scroll**: rows 4..6 would NOT
get a visible "empty area" tint despite conceptually being legitimately empty content
rows. This is Task 6 territory (see Recommendation below).

### Scenario E (bonus / sort-after-remove) — included for completeness but user reports "不排序也出现"

After a sort operation the server recomputes `lastOccupiedRow` correctly (its call site
`recomputeLastOccupiedRow()` already exists, see BackpackContainer.java:202-218), but no
S2C packet ships the revised value back to the client — the cached client
`lastOccupiedRow` remains at the pre-sort value.  On the client this **would** produce
visual over-scroll similar to Scenario D, but with the temporal cause being
**stale-after-sort** rather than **sparse-content**.  The user has already confirmed this
specific path is NOT the only trigger — it also appears without sorting — so we classify
it as a **secondary latent driver** (b), not primary. Task 4 (server-recompute +
push-lastOccupiedRow-back) covers remediation for this path.

---

## 3. Root-Cause Classification

Per the plan's enumerated taxonomy (a/b/c/d):

| Tag | Description | Verdict |
|-----|-------------|---------|
| **(a)** `lastOccupiedRow` server vs client consistent but `getMaxScroll()` formula returns异常 | **RULED OUT** — formula `getContentHeight() - (height - border) = (lastOccupiedRow+1-visibleRows) * 18` (with border=0) is mathematically correct; under correct `lastOccupiedRow` it bounds viewport exactly to rows `[lastOccupiedRow+1-visibleRows .. lastOccupiedRow]` ⇒ no over-scroll possible. No anomaly in either the client or server null-branch. |
| **(b)** client `lastOccupiedRow` differs from server-side fresh value (stale) | **SECONDARY LATENT DRIVER** — `lastOccupiedRow` is sent to client only at open time (`ServerPayloadHandler` line 88-92 writes it; client reads at `BackpackContainer.<init>` line 121: `this.lastOccupiedRow = buf.readInt()`). No S2C packet exists to keep client-side `lastOccupiedRow` in sync after server-side container mutations (add/remove/sort). User's "不排序也出现" rules out sort-triggered staleness as the *only* explanation but does not exclude "stale-after-prior-item-remove" — and even more importantly, `ServerPayloadHandler.handleOpenBackpack` initialises empty containers with `lastOccupiedRow=0` instead of `-1` (line 60, confirmed by direct read), an incorrect-default bug that, while currently masked because `scrollPanel=null` on empty, would resurface if any code path later relaxes the `initScrollPanel` precheck. **Primary remediation: Task 2's `recomputeLastOccupiedRow()` + a new S2C lastOccupiedRow broadcast hook whenever the server-sided dirty flag is set (Task 4 area).** |
| **(c)** math正常 but visual "empty rows" are actually empty *cells* within bounds | **PRIMARY ROOT CAUSE** — matches the user's "不排序也出现" signature exactly. `lastOccupiedRow` is a single integer that identifies only the *last* row containing any item; it carries no information about whether the rows in the range `[0 .. lastOccupiedRow-1]` themselves contain items. If items only occupy row R (say row 7) for whatever legacy-storage reason, then **all** rows `[R-maxScrollRows .. R-1]` are *legitimately empty* in the container's own slot range. The client math does NOT over-scroll past `R`; it still *displays* those empty rows because they fall within the legal content extent. The feelings-evacuated phenotype "scroll down, see empty rows" is therefore content emptiness, not a scroll-bound bug. Critically: this is consistent regardless of whether the user sorts, since sorting only relocates items within the same content range — sparse layouts remain sparse post-sort or post-none. |
| **(d)** dual-clamp (server `containerSize`-based `max = totalRows - MAX_VISIBLE_ROWS=12` vs client `lastOccupiedRow+1 - visibleRows`) triggers over-scroll | **SECONDARY LATENT DRIVER (mechanical, not visual bottleneck for the user's reported case)** — Real asymmetry exists: confirm below. The user's reported symptom is a *visual* empty-row-on-scroll-down. Under strict client clamp it cannot over-scroll past `lastOccupiedRow+1`, hence (d) cannot by itself produce empty rows below row `lastOccupiedRow`; it can only produce items visually shifted out-of-step with the server (which manifests as duplicate-looking items, not empty rows). We tag (d) as latent rather than primary because: (i) the visible-empty-rows symptom matches (c)'s semantics precisely; (ii) (d)'s mechanical effect is "client extent exceeds server extent → server clamps C2S rowOffset, server's broadcast sends slot ItemStacks from a *different* row than client visually positions" — a different fingerprint than what the user described. (d) **does** need fixing (Task 4) but is *complementary* to (c) — see Recommendation. |

Combining: **PRIMARY = (c); SECONDARY = (b)+(d).** (a) is ruled out.

### Why (c) and (d) can BOTH trigger in the same session

(c) is a static content-level property — it is true whenever items are sparse in the
container, regardless of sort. A player who routinely dropped a few items into widely
spaced slots (legacy save state) sees it on every reopen without interaction.

(d) requires `visibleRows < MAX_VISIBLE_ROWS=12` (typical windows) AND `totalRows > 12`
(lvl ≥ 9 → `containerSize ≥ 135`) AND the player scrolls down past the server's clamp
extent. A lvl≥9 backpack owner who scrolls down will eventually hit (d)'s territory *in
addition* to still seeing (c)'s sparse rows.

These stack independently and the user's complaint is consistent with **either or both**
firing — but the *minimal common trigger* in plain "scroll down on a sparse backpack,
without sorting" matching the user's report is (c). We mark (c) primary on that basis.

---

## 4. Recommendation for Task 4 (server-siderecompute / clamp alignment)

The plan's Task 2 is already implemented (added `BackpackContainer.getContainerSize()`,
`setLastOccupiedRow(int)`, `recomputeLastOccupiedRow()` — confirmed by direct read of
BackpackContainer.java:182-218). Task 4 must supplement this with a real cross-side fix.

Two concrete recommendations:

### 4a. Align server `getMaxScrollOffset()` to the client's `getMaxScroll()` semantics
Today:
```java
// BackpackContainer.java:252-255
public int getMaxScrollOffset() {
    int totalRows = (storageContainer.getContainerSize() + COLS - 1) / COLS;
    return Math.max(0, totalRows - MAX_VISIBLE_ROWS);   // <-- asymmetry source
}
```
The server here uses `containerSize` + `MAX_VISIBLE_ROWS=12` regardless of the client's
current `visibleRows` and regardless of where items actually sit (`lastOccupiedRow`).
Recommended rewrite mirrors the client's metric:
```java
public int getMaxScrollOffset() {
    int contentRows = Math.max(0, lastOccupiedRow + 1);            // mirrors client getContentHeight()/18
    return Math.max(0, contentRows - getEffectiveVisibleRows());   // mirrors client getMaxScroll()/18
}
```
where `getEffectiveVisibleRows()` is the client-reported viewport height from a new
S2C-aware C2S handshake (or, conservatively, the same MAX_VISIBLE_ROWS=12 cap as a floor
when client hasn't yet sent its actual visibleRows). This eliminates (d).

**Trade-off**: client-side `visibleRows` is per-player window size and not currently
sent to the server. Two options:
1. Piggyback on `ScrollChangePayload` (add `visibleRows` field) — minimal but mixes
   concerns.
2. Send a one-time `ViewportSizePayload` on `BackpackScreen.init()` / `resize()` (we have
   diag evidence those are the only two triggers → 2 packets per session).

Option 2 is cleaner; Task 4 should either:
- (preferred) send `ViewportSizePayload` and have the server cache per-container `visibleRows`, OR
- fall back to assume `visibleRows = MAX_VISIBLE_ROWS` server-side only when no viewport
  payload has been received yet, then accept that the client could over-scroll in the brief
  pre-handshake window — but since both clamps now reflect `lastOccupiedRow+1`, even the
  worst case bounds over-scroll to `lastOccupiedRow+1 - 12` which equals the server's
  pre-handshake clamp ⇒ safe.

### 4b. Server-side `lastOccupiedRow` recompute-on-mutation
Add an S2C packet that the server sends to the client whenever `broadcastChanges` notices
`lastOccupiedDirty == true` (Task 2's flag already in place at BackpackContainer.java:81
doc comment — implementation now needed). Body: new `lastOccupiedRow`. Client handler
invokes `menu.setLastOccupiedRow(newRow)` (already implemented, line 195) and then
`scrollPanel.applyScrollLimits()` or sibling — note: `applyScrollLimits` is private in
NeoForge ScrollPanel; a public wrapper has to be added by Task 5 to expose it. This
eliminates (b).

### 4c. Default `lastOccupiedRow = -1` on empty container
Server initialised `lastOccupiedRow = 0` for empty containers (ServerPayloadHandler:60)
which is semantically wrong (should be `-1`). Currently masked because `scrollPanel=null`
on empty content. Fix anyway — small code change, ships with the Task 4 S2C broadcast hook.

---

## 5. Recommendation for Task 6 (cell-level mask)

The current cell-level mask (`renderSlotCellBackgrounds`, lines 311-338) is implemented
**without scroll awareness**:

```java
int lastOccupiedRow = menu.getLastOccupiedRow();
int firstEmptyRow = lastOccupiedRow + 1;
if (firstEmptyRow < visibleRows) {                   // <-- screen-coord check, NOT viewport-coord
    int dimY = topPos + SLOTS_Y_OFFSET + firstEmptyRow * SLOT_SIZE;   // <-- screen Y, not scroll-adjusted
    ...
    guiGraphics.fill(dimX, dimY, ...);
}
```

Two bugs co-occur once the player scrolls down past `visibleRows - firstEmptyRow` rows:

1. `firstEmptyRow < visibleRows` evaluates the *untransformed* `lastOccupiedRow+1` against
   `visibleRows`. After scrolling, the on-screen viewport rows correspond to
   `[scrollRowOffset .. scrollRowOffset + visibleRows - 1]`, so the test must use the
   *viewport-relative* first empty cell row.
2. `dimY` is anchored to `topPos + SLOTS_Y_OFFSET + firstEmptyRow * SLOT_SIZE` in absolute
   screen coordinates, so even if (1) were true the dim is drawn off-screen for any
   `scrollRowOffset > 0`.

### Recommended Task 6 redesign
```java
int lastOccupiedRow = menu.getLastOccupiedRow();
int scrollRowOffset = (scrollPanel != null) ? (int) scrollPanel.getScrollDistanceDiag() / SLOT_SIZE : 0;
int firstEmptyRowInViewCoord = (lastOccupiedRow + 1) - scrollRowOffset;     // may be <0 or >visibleRows
int firstDimRowInViewCoord = Math.max(0, firstEmptyRowInViewCoord);
if (firstDimRowInViewCoord < visibleRows) {
    int dimX = leftPos + SLOTS_X_OFFSET;
    int dimY = topPos + SLOTS_Y_OFFSET + firstDimRowInViewCoord * SLOT_SIZE;
    int dimWidth = COLS * SLOT_SIZE;
    int dimHeight = (visibleRows - firstDimRowInViewCoord) * SLOT_SIZE;
    guiGraphics.fill(dimX, dimY, dimX + dimWidth, dimY + dimHeight, 0x80000000);
}
```

Additionally, if Task 4's (b)/(d) mitigations correctly bound the client's allowed scroll
to `lastOccupiedRow+1-visibleRows` rows, then `scrollRowOffset` will never exceed
`lastOccupiedRow+1-visibleRows` and thus row `lastOccupiedRow` will always remain visible
in the bottom viewport slot — perfect for the dim overlay to fire for exactly the trailing
empty cells. The combination of Tasks 4 and 6 together closes **the visual appearance of
the perceived bug**:
 - Task 4 (a) eliminates over-scroll + keeps `lastOccupiedRow` fresh;
 - Task 6 ensures the on-screen rows past `lastOccupiedRow` are dimmed regardless of
   scroll offset;
 - The **Sparse Content Cause (c)** itself (legitimately empty cells WITHIN the
   `0..lastOccupiedRow` content range, e.g. row 0 has items but rows 1..6 don't) is **a
   content-state property, NOT a code bug**. No Task 4/6 mitigation should hide those
   rows; if desired, Task 7 (UX polish) could opt to compress visual representation
   (collapsing fully-empty middle rows is itself a *feature*, not a fix, and should be a
   user-toggleable behaviour — recommendation: defer, do not bundle).

### Direct answers to plan Question A2 ref (`getMaxScrollOffset` server-side aligned to lastOccupiedRow-based too?)
**YES — Task 4 should rewrite `BackpackContainer.getMaxScrollOffset()` to use
`max(0, lastOccupiedRow + 1 - effectiveVisibleRows)` instead of
`max(0, totalRows - MAX_VISIBLE_ROWS)`.** Reasons:
- This **removes the (d) dual-clamp asymmetry** by making both sides share the same
  semantic inputs (`lastOccupiedRow`, visibleRows) rather than mixing `containerSize`
  and `MAX_VISIBLE_ROWS=12`.
- It also implicitly bounds server-side `scrollOffset` so the server never broadcasts
  slot ItemStacks from rows past `lastOccupiedRow`, eliminating the side-effect
  misalignment where the client would otherwise render empty content beyond the
  occupied range.
- The cost is needing client's `visibleRows` on the server — see §4a options 1 / 2.

### Direct answers to plan Question A2 ref (`does the cell-level mask need adjustment?`)
**YES — Task 6 should make the dim scroll-aware as outlined above.** Without this, the
sparse-content rows in the upper portion of the viewport stay visually lit-up (no dim)
after the user scrolls down, reinforcing the user's perception of "empty rows" rather
than de-emphasising them. The fix is small and orthogonal to Task 4.

---

## 6. Open questions for playthrough verification (when the user ships runClient)

When the user actually runs the game with these probes installed, the following
observations will validate / refute the inferred classification:

1. **Open sparse backpack, scroll to bottom**: capture `[BackpackScreen-Diag]
   mouseScrolled() end: scrollDistance=... rowOffset=... lastOccupiedRow=N ...`. The
   critical invariant is `rowOffset ≤ lastOccupiedRow + 1 - visibleRows`. If true ⇒ (c)
   confirmed primary (no over-scroll past legal content extent). If `rowOffset >
   lastOccupiedRow + 1 - visibleRows` ever observed ⇒ (a) or (b) active. If
   `scrollDistance > getMaxScroll()` ever observed ⇒ numpy/clamp-block bypassed by other
   path (would mean (a) formula or a non-clamped scrollDistance setter).
2. **Open lvl≥9 fully-populated backpack, scroll to bottom**: log
   `scrollDistance` reaching `getMaxScroll()` exactly (no over). Confirms client clamp
   works in isolation. Then check whether rowOffset sent to server matches what the server
   accepted (server response not visible in these probes — needs a Task 4-side probe,
   out of scope here).
3. **`/clear`, reopen empty**: confirm `lastOccupiedRow=0` and `scrollPanel=null` —
   documents the `-1` default defect from §4c as currently masked.
4. **Sort, then scroll**: confirm bug still appears ⇒ matches user's "不排序也出现"
   criterion ⇒ not sort-only. Confirm `lastOccupiedRow` value before vs after sort
   notequal ⇒ (b) staleness.
5. **Resize the window during an open sparse backpack**: capture
   `[BackpackScreen-Diag] resize() end: ... reclamp-needed?false/true`. If `true`, the
   re-clamp path itself dropped scrollDistance — currently the new panel re-instantiates
   with `scrollDistance=0` losing state → another minor UX bug worth noting for Task 7
   (existing `initScrollPanel` recreates the instance on every resize). Not in scope for
   Tasks 4/6 but mentioned to be tracked.

These observations together provide a deterministic mapping:
 - rowOffset ≤ lastOccupiedRow+1-visibleRows AND empty rows visible ⇒ **(c)** confirmed primary.
 - rowOffset > lastOccupiedRow+1-visibleRows ⇒ **(a)** or **(b)** active; check
   `lastOccupiedRow` for staleness between init and final scroll → stale ⇒ **(b)**, else
   `(a)` formula anomaly.
 - scrollDistance > getMaxScroll() ever ⇒ **(a)** or bypassed clamp.
 - resize reclamp-needed?true ⇒ also flags the resize recreation bug noted in (5).

---

## 7. Self-Review

- **Probe file scope**: ONLY `BackpackScreen.java` modified — verified by inspecting
  edits listing (`init()`, `resize()`, `mouseScrolled`, plus 4 public inner-class
  helpers). No other source file changed. No new imports added (used
  `System.out.println`). No existing logic altered or removed.
- **Compile**: `./gradlew compileJava` → BUILD SUCCESSFUL. Only a generic javac
  deprecation note (pre-existing pattern elsewhere in the project), no errors / warnings
  attributed to the probe.
- **Commit policy**: NO changes committed; this is diagnostic-only per plan §Task 1.
- **No live playthrough**: per task MUST DO §4, since I did not boot the actual
  `runClient` session, the log excerpts above are *simulated* from the code body; the
  inferred classification (primary c, secondary b+d, ruled out a) is grounded in
  end-to-end reading of BackpackScreen / BackpackContainer / ServerPayloadHandler plus
  NeoForge ScrollPanel source from the sources jar. §6 lists the verification
  observations that would confirm/refute the live game.
- **No new persistent state or network packets**: probe uses only local
  `System.out.println` on already-existing instances.

Outstanding: the next agent can either remove these probes after Tasks 4/5/6/7 land (the
comment banners `=== [BackpackScreen-Diag] === ... === END DIAG PROBE ===` and the
`TEMP DIAG HELPERS` block make them grep-able) or fold a subset of them into a permanent
INFO log via `TimeReward.LOGGER` if informative long-term diagnostics are desired.