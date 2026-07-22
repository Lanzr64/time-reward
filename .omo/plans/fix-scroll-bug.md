# Fix: 背包滚动时底部行不刷新

## TL;DR

> **Quick Summary**: 修复 `BackpackScreen.BackpackScrollPanel` 中 `mouseDragged()` 缺少 `setClientScrollOffset()` 调用导致拖拽滚动条时物品不刷新的 Bug，同时修复 `mouseScrolled()` 中 `updateSlotsPosition()` 在 `setClientScrollOffset()` 之前调用的顺序问题。
>
> **Deliverables**:
> - 修改 `BackpackScreen.java`（~5 行变动）
>
> **Estimated Effort**: Quick
> **Parallel Execution**: NO - 单文件顺序修改
> **Critical Path**: 单任务

---

## Context

### Original Request
按 B 打开背包并滚动库存时，最下面几行不刷新，仍显示滚动前的状态。

### Analysis Summary
**Root Cause**:
1. **致命**: `mouseDragged()` (L585-591) 完全缺少 `setClientScrollOffset()` 调用。拖拽滚动条时 `scrollDistance` 会更新，但 `scrollOffset` 始终是旧值，导致所有 `DynamicScrollSlot.getItem()` → `getActualIndex()` 返回错误索引。
2. **次要**: `mouseScrolled()` (L544-558) 中 `updateSlotsPosition()` 在 `setClientScrollOffset()` 之前调用，过滤检查使用旧的 scrollOffset。

---

## Work Objectives

### Core Objective
修复背包滚动时物品不刷新的 Bug，确保拖拽和滚轮滚动均能正确更新显示的物品。

### Concrete Deliverables
- `BackpackScreen.java`:
  - `mouseDragged()` 补上 `setClientScrollOffset()` + 服务端通知
  - `mouseScrolled()` 调换 `setClientScrollOffset()` 到 `updateSlotsPosition()` 之前

### Definition of Done
- [ ] 拖拽滚动条时物品正确更新，不再卡在旧位置
- [ ] 滚轮滚动时物品正确更新
- [ ] 服务端收到正确的 ScrollChangePayload

### Must Have
- `mouseDragged()` 中调用 `setClientScrollOffset()` 同步 `scrollOffset`
- `mouseDragged()` 中发送 `ScrollChangePayload` 通知服务端
- `mouseScrolled()` 中 `setClientScrollOffset()` 在 `updateSlotsPosition()` 之前

### Must NOT Have (Guardrails)
- 不改动其他文件或逻辑
- 不改变 `setClientScrollOffset()` 的 clamping 行为
- 不改变 `repositionSlots()` 的其他逻辑

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES (Minecraft mod - manual playtest)
- **Automated tests**: None (no test infra for UI)
- **Agent-Executed QA**: 启动游戏实测（见 QA 场景）

### QA Policy
执行 agent 启动 Minecraft 客户端，打开背包，验证滚动行为。

---

## TODOs

- [ ] 1. 修复 BackpackScreen 背包滚动不刷新 Bug

  **What to do**:
  1. 在 `BackpackScreen.java` 的 `BackpackScrollPanel.mouseDragged()` 中，`updateSlotsPosition()` 之后补充：
     ```java
     int rowOffset = (int) scrollDistance / SLOT_SIZE;
     menu.setClientScrollOffset(rowOffset);
     PacketDistributor.sendToServer(
             new ScrollChangePayload(menu.containerId, rowOffset));
     ```
  2. 在 `mouseScrolled()` 中，将 `menu.setClientScrollOffset(rowOffset)` 移到 `updateSlotsPosition()` 之前。

  **Must NOT do**:
  - 不改动 `repositionSlots()` 内部逻辑
  - 不改动 `setClientScrollOffset()` 的 clamping 行为

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件修改，~5 行变更，逻辑明确
  - **Skills**: [] (无需额外技能)

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: 单任务
  - **Blocks**: 无（最终验证任务）
  - **Blocked By**: 无

  **References**:
  - `BackpackScreen.java:544-558` - `mouseScrolled()` 需要在 `setClientScrollOffset()` 之前调用，需调换顺序
  - `BackpackScreen.java:585-591` - `mouseDragged()` 缺少 `setClientScrollOffset()`，需补充
  - `DynamicScrollSlot.java:49-53` - `getActualIndex()` 使用 `scrollOffsetSupplier.getAsInt()`，scrollOffset 必须同步更新
  - `BackpackContainer.java:242-244` - `setClientScrollOffset()` 方法签名和 clamping 逻辑

  **Acceptance Criteria**:

  **QA Scenarios**:

  ```
  Scenario: 鼠标拖拽滚动条后物品正确刷新
    Tool: 手动启动 Minecraft 客户端验证
    Preconditions: 背包有足够物品（至少 24 行以上），使滚动条可拖拽
    Steps:
      1. 启动客户端并进入游戏
      2. 按 B 打开背包（或打开奖励背包）
      3. 鼠标左键按住滚动条，向下拖拽
      4. 观察所有显示槽位的物品
    Expected Result: 拖拽时所有物品随滚动位置实时更新，底部不再显示滚动前的旧物品
    Failure Indicators: 拖拽后物品不变，或底部行显示空物品
    Evidence: .omo/evidence/task-1-scroll-drag.mp4（录屏）

  Scenario: 鼠标滚轮滚动后物品正确刷新
    Preconditions: 同上
    Steps:
      1. 打开背包
      2. 使用鼠标滚轮向下滚动
      3. 观察所有显示槽位的物品
    Expected Result: 物品随滚轮位置实时更新，底部行为新物品
    Failure Indicators: 底部行显示空物品或旧物品
    Evidence: .omo/evidence/task-1-scroll-wheel.mp4

  Scenario: 连续快速滚动（压力测试）
    Preconditions: 同上
    Steps:
      1. 打开背包
      2. 快速连续滚动鼠标滚轮（或快速拖拽滚动条）
      3. 观察物品更新情况
    Expected Result: 每次滚动后物品最终都更新到正确位置，无卡滞
    Failure Indicators: 物品卡在错误位置不更新
    Evidence: .omo/evidence/task-1-scroll-rapid.mp4
  ```

  **Evidence to Capture:**
  - [ ] 屏幕录制或截图对比滚动前后的物品显示

  **Commit**: YES
  - Message: `fix(backpack): update scrollOffset on mouse drag and reorder mouseScroll update`
  - Files: `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java`
  - Pre-commit: 无（Java 项目，无需额外命令）

---

## Final Verification Wave

- [ ] F1. **Code Review** — `quick`
  确认 `mouseDragged()` 补充了 `setClientScrollOffset()` 和 `PacketDistributor.sendToServer()`。
  确认 `mouseScrolled()` 中 `setClientScrollOffset()` 在 `updateSlotsPosition()` 之前。
  确认没有意外改动其他逻辑。
  Output: `Changes [N lines] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Real QA** — `unspecified-high`
  启动 Minecraft，按 F3 打开调试屏幕确认 GUI Scale，打开背包，拖拽 + 滚轮测试，连续快速滚动测试。
  Output: `Scenarios [N/N pass] | VERDICT`

---

## Commit Strategy

- **1**: `fix(backpack): update scrollOffset on mouse drag and reorder mouseScroll update` - BackpackScreen.java

---

## Success Criteria

### Final Checklist
- [ ] 拖拽滚动条时物品实时更新
- [ ] 滚轮滚动时物品实时更新
- [ ] 连续快速滚动无卡滞
- [ ] 只改了 BackpackScreen.java 一个文件
