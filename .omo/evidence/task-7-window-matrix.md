# Task 7 — 三窗口尺寸蒙版像素位置一致性

> Per Bug 2 requirement: "蒙版位置只随窗口尺寸平移 (跟随 leftPos/topPos)，相对 leftPos+SLOTS_X_OFFSET 的偏移必须恒等。"
> Live pixel sampling deferred to F3 (实机真测 QA). Static invariant documented here.

## Constants
- `SLOTS_X_OFFSET = 7`
- `SLOTS_Y_OFFSET = 17`
- `SLOT_SIZE = 18`
- `COLS = 12`
- 蒙版颜色 ARGB: `0x80000000`

## Slot cell (col=11, viewport row N) 的蒙版左上角像素点屏幕坐标 (PYTHON 式)

```
maskX(col, viewportRow) = leftPos + SLOTS_X_OFFSET + col * SLOT_SIZE
                       = leftPos + 7 + col * 18
maskY(col, viewportRow) = topPos + SLOTS_Y_OFFSET + viewportRow * SLOT_SIZE
                       = topPos + 17 + viewportRow * 18
```

`leftPos` 和 `topPos` 由 `AbstractContainerScreen` 计算:  guiScaledWidth / 2 - imageWidth / 2,  guiScaledHeight / 2 - imageHeight / 2 (近似, 取决于 layout). Window resize 改变 guiScaledWidth/Height → leftPos/topPos 会移.

## 不变性 (相对 origin)

$$
\text{maskX}(11, r) - \text{leftPos} - \text{SLOTS\_X\_OFFSET} = 11 \times 18 = 198 \quad \forall \text{window size}
$$

即: 蒙版 cell (col=11) 距离 panel 起点的相对偏移 **恒等于 198 像素**，**与窗口尺寸无关**.

## 三窗口尺寸期望表 (containerSize = 11 as canonical test)

| 窗口尺寸 | leftPos | topPos | visibleRows | mask @ (col=11, viewportRow=0) 绝对 (X,Y) | 期望相对偏移 (X - leftPos) | mask @ (col=任意, viewportRow=3) Y |
|---|---|---|---|---|---|---|
| default (假设 windowHeight = 480, imageHeight = 114+4*18=186, topPos ≈ (480-186)/2 = 147) |  (W-236)/2 | 147 | 4 | (leftPos + 7 + 11*18, topPos+17+0*18) = (leftPos+205, 164) | **205** | topPos+17+54 |
| small (windowHeight = 240, imageHeight = 186, topPos ≈ 27) | (W-236)/2 | 27 | 4 | (leftPos+205, 44) | **205** | topPos+71 |
| large (windowHeight = 1080, visibleRows=clamp((1080-114)/18, 4, 12)=12, imageHeight = 114+12*18=330, topPos=(1080-330)/2=375) | (W-236)/2 | 375 | 12 | (leftPos+205, 392) | **205** | topPos+17+54 = 446 (row 3) |

**核心断言**：column 11 cell 的 mask 起始 X 相对 leftPos 偏移恒定 205 像素 (= 7 + 11*18)；不随 visibleRows 或 windowHeight 变动.

## 与 Bug 2 原描述一致性评估

原 bug:
- 旧代码：`dimY = topPos + SLOTS_Y_OFFSET + firstEmptyRow * SLOT_SIZE`, `firstEmptyRow = lastOccupiedRow + 1` (绝对 row 索引) → 滚动时 firstEmptyRow 不变，dimY 不随 viewport 滚动 → **mask 视觉位置不随槽位移动**.
- `dimHeight = (visibleRows - firstEmptyRow) * SLOT_SIZE` — visibleRows 是窗口派生 → **窗口高变 → dimHeight 变 → mask 扩展/收缩比例错位**.

新代码 (Task 6 fix):
- `viewportRow * SLOT_SIZE` 代替 `firstEmptyRow * SLOT_SIZE` — viewportRow 总是 `[0, visibleRows)` 局部索引 → 不论 scrollRowOffset 是几，mask Y 在 panel 内的位置恒等于 viewportRow 行的 SC texture 位置 → **mask 随 scrollRowOffset 一起滚**.
- `containerSize` 是容器容量，**不依赖 visibleRows** — mask 是否开始由 `actualIndex >= containerSize` 决定，**只看容器边界**，**与窗口尺寸无关**.
- 整行 / 部分行 mask 都按 cell 计算，无 row-level 整块 dimHeight — 高度不随 visibleRows 浮动.

## 静态验证通过

预期 mask X 相对 leftPos 偏移恒等 205(对 col 11)；Y 偏移随 viewportRow × 18 线性，与 cell SC texture 严格对齐 — Bug 2 修复在代码层面已实现. 实机像素采样留 F3 (实机真测 QA)，但本静态推导足以锁定相对位置不变性.