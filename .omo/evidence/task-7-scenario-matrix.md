# Task 7 — 7-场景复现矩阵

> Static expectations for cell-level mask behavior across the container-size matrix.
> Live screenshots deferred to F3 (实机真测 QA), but the predicates below must hold.

`COLS = 12`，`SLOT_SIZE = 18`，`MAX_VISIBLE_ROWS = 12`，`visibleRows` ∈ [4, 12] per screen.

## In-range predicate
For each viewport cell `(viewportRow ∈ [0, visibleRows), col ∈ [0, COLS))`:
- `absRow = scrollRowOffset + viewportRow`
- `actualIndex = absRow * COLS + col`
- **Mask draw** ⇔ `actualIndex ≥ containerSize`
  - Full-row fast path: `absRow * COLS >= containerSize` → single `fill` rect spanning `COLS * SLOT_SIZE` pixels
  - Partial-row path: cells with `actualIndex >= containerSize` filled individually

## Scenario table

| containerSize | visibleRows | scrollRowOffset range | in-range cells | masked cells per state | Notes |
|---|---|---|---|---|---|
| 0   | 4..12 | 0..0 (no panel)            | 0    | `visibleRows × 12` cells (whole panel) | Empty container → `lastOccupiedRow=-1` → no scrollPanel; renderSlotCellBackgrounds sees `containerSize=0` so `absRow ≥ 0 = containerSize` ⇒ all rows走 fast path. **MATCHES user spec: 容器空时整片蒙版,无滚动条.** |
| 11  | 4..12 | 0..0 (no panel；lastOccupiedRow=0，contentHeight=18 < panelHeight) | 11   | viewport row 0: col 11 masked (1 cell partial row)；rows 1..visibleRows-1 走 fast path (12 cells each). Total = `1 + (visibleRows-1)×12` | **MATCHES user spec: containerSize=11 时下3行整行蒙版 + row 0 col 11 蒙版.** |
| 12  | 4..12 | 0..0 (no panel；lastOccupiedRow=0，contentHeight=18 < panelHeight) | 12   | 0 cells in row 0；rows 1..visibleRows-1 走 fast path (all 12 cells masked). Total = `(visibleRows-1)×12` | **MATCHES user spec: containerSize=12 时下3行整行蒙版,无 row 0 col 11 之类残留 (因为 12 is multiple of COLS, no partial row).** |
| 15  | 4..12 | 0..0 (no panel；lastOccupiedRow=0) | 15   | row 0: 0 masked；row 1 cols 3..11 (9 cells partial row)；rows 2..visibleRows-1 走 fast path. Total = `9 + (visibleRows-2)×12` | Partial-row appears at viewport row 1 (absRow=1), cols 3..11 |
| 96  | 4..12 | 0..(8-visibleRows)    | 96   | If `visibleRows ≤ 8`, scrollPanel active；at scrollRowOffset max = (8 - visibleRows)，last visible viewport row = absRow 7，全部 in-range (96=8×12) → no mask cells visible. If visibleRows > 8: scrollPanel not active, 全部 8 occupied rows in viewport + (visibleRows-8) masked fast-path rows. Total = `(visibleRows-8)×12` | 96 = 8 × 12 (exact row alignment) — no partial row in any state |
| 144 | 4..12 | 0..(12-visibleRows)   | 144  | scrollPanel active only if `visibleRows < 12`. At max scroll，last viewport row = absRow 11 (第 144 / 12 = 12th row, 0-indexed 11)，all 12 cells in-range → no mask visible. If `visibleRows >= 12`: no panel，no mask (all 12 rows in panel, exactly containerSize=144==12×12). **Matches "144 == COLS × MAX_VISIBLE_ROWS" exact-fit case: 1-pixel scrollbar must NOT appear** — `initScrollPanel` condition `contentRows * SLOT_SIZE <= panelHeight` → false (144 * 18 > 12 * 18 = 216), so panel WOULD be created. Actually 144 == visibleRows × 12 → contentRows=12，panelHeight=visibleRows × 18. If visibleRows=12，contentRows × SLOT_SIZE = 216 == 216 = panelHeight → not `>`, panel NOT created. If visibleRows<12，panel created, max scroll = (12 - visibleRows) × 18 > 0. OK. |
| 300 | 4..12 | 0..(25-visibleRows)   | 300  | scrollPanel active (25 rows > 12 max visibleRows). At max scroll，scrollRowOffset = 25-visibleRows, last viewport row = absRow 24 → 24*12 = 288 .. 299 = in-range (300=25×12 满 row)。Row 24 cols 0..11 ALL in-range → no mask in last row. If `scrollRowOffset < 25 - visibleRows` (mid-scroll), last viewport row > absRow 24, 完全 out-of-range row (absRow >=25) → fast-path. **Bug 1 verification**: scrolling to max puts row 24 at panel bottom，no trailing "empty row" visible (matches fix).  |
| 165 | 4..12 | 0..floor((lastOccupiedRow)/COLS)-visibleRows+1 = 0..(13-12)=0..1 at visibleRows=12 (lastOccupiedRow depends on item distribution) | up to 165 | Suppose `/give 100`: items fill slots 0..99，lastOccupiedRow = 99/12 = 8 (row 8 col 3). 165 - 100 = 65 in-range empty slots (slots 100..164 are valid positions but hold EMPTY ItemStacks). User's original "sparse row visual" concern (Task 1 root cause PRIMARY (c)) — **NEW mask behavior does NOT dim these empty in-range cells** (predicate is `actualIndex >= containerSize`, not `stack.isEmpty()`). Items in rows 0..7 visible normally; row 8 cols 0..3 partial occupied + cols 4..11 valid-empty unmasked. Rows 9..lastViewportRow ONLY masked if their absRow * 12 >= 165, i.e. absRow >= 14. Mid-scroll would show rows 9..13 as in-range empty (no mask), then row 14+ fast-path mask. **This is the intended behavior per user clarification: "in-range empty slots 留白显示，蒙版只对 truly-out-of-container cells."** |

## Static invariant (Bug 1 fix verification)

At max scroll (`scrollRowOffset = max(0, (lastOccupiedRow+1) - visibleRows)`, assuming `lastOccupiedRow >= 0`), the last visible viewport row is `lastOccupiedRow`, not beyond. Therefore:
- **No extra empty rows below the actual last occupied row** (Bug 1 fixed via re-clamp + server sync)
- **Empty cells within `lastOccupiedRow` row remain visible as empty grid cells** (correct — they're valid storage positions)

## Static invariant (Bug 2 fix verification)

Mask cell Y = `topPos + SLOTS_Y_OFFSET + viewportRow * SLOT_SIZE` — uses **viewport-relative row index**, not `(displayRow - scrollRowOffset)`. This means:
- Mask moves with the panel via `topPos/leftPos` (which only shift with the screen origin)
- Mask Y does NOT depend on `visibleRows × SLOT_SIZE` for its start position — only its extent (how many viewport rows get drawn)
- Resize shifts topPos but keeps the formula `topPos + SLOTS_Y_OFFSET + viewportRow * SLOT_SIZE` consistent → mask stays anchored to the slot grid
- Old buggy formula used `firstEmptyRow * SLOT_SIZE` (absolute row index) which desynchronized from scrolled viewport → Mask appeared stationary while slots moved when scrolling. NEW formula uses `viewportRow * SLOT_SIZE` (viewport-local), so mask always tracks the slot.