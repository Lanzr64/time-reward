# 背包 GUI 双 Bug 修复 + lastOccupiedRow 同步

## TL;DR

> **快速摘要**: 修复 `BackpackScreen` 两个用户报告的 bug：(1) 大容器可滚动时往下滚会看到超过实际最大槽位的"空行"；(2) 黑色蒙版位置计算依赖窗口大小，应只在按 `containerSize` 决定的实际槽位位置上画 cell-级蒙版。同时一并修复根因：服务端 `lastOccupiedRow` 在 sort / 取放物品后不重算也不下发到客户端。
>
> **交付物**:
> - `BackpackScreen.renderSlotCellBackgrounds()` 重写为 cell-级蒙版（按 `containerSize` 边界）
> - `BackpackContainer` 暴露 `getContainerSize()` getter 给客户端使用
> - `BackpackContainer` 服务端在 `sort()` / 通过 `broadcastChanges()` 钩子重算 `lastOccupiedRow`
> - 新增 `BackpackStatePayload` S2C 同步包；`ClientPayloadHandler` 接收并刷新客户端 `lastOccupiedRow`
> - `BackpackScreen.resize()` / 收到新 lastOccupiedRow 后显式重 clamp `scrollDistance`
> - 新增 OpenBackpackPayload 时容器全空的 `lastOccupiedRow = -1` 语义
> - 复现场景日志 + 视觉验证证据
>
> **预计工作量**: Medium
> **并行执行**: YES - 3 waves
> **关键路径**: Task 2 (containerSize getter / Sync payload 定义) → Task 4 (服务端 recompute + 同步) → Task 5 (客户端 sync handler + re-clamp) → Task 7 (集成 + e2e)

---

## Context

### 原始请求
1. 大容器（100+ 槽位）可滚动时，往下滚会发现下面多了很多空行，这些空行超过实际的最大槽位，无显示意义。
2. 背包用来视觉掩盖的黑色蒙版位置计算有问题：窗口大小变化会影响蒙版位置；应只按最后一个槽位的位置来计算蒙版位置，而不是按窗口大小。

### 访谈总结
**关键讨论**：
- Bug 1 触发场景：用户确认"不排序也出现"——即初始状态可直接复现，所以 `stale lastOccupiedRow-after-sort` 不是唯一根因。
- Bug 2 蒙版期望：用户明确希望 **cell 级蒙版按 `containerSize` 计算**：containerSize=12 时下 3 行整行蒙版；containerSize=11 时下 3 行蒙版 + row 0 col 11 蒙版（即"最后一槽"也是蒙版）；大容器可滚动时最后一行多余槽位（超出 containerSize）也应有蒙版；蒙版要随槽位一起滚动。
- 根因修复：用户同意"一并修根因"——服务端 sort / 取放物品后重算 `lastOccupiedRow` 并通过新 S2C 包同步给客户端。

**研究发现**：
- NeoForge `ScrollPanel.applyScrollLimits()` 把 `scrollDistance` clamp 到 `[0, getMaxScroll()]`，其中 `getMaxScroll() = getContentHeight() - (height - border)`。当前 `getContentHeight = (lastOccupiedRow + 1) * SLOT_SIZE`，border = 0。数学上在正确的 `lastOccupiedRow` 下不会过滚——所以"空行"来自 `lastOccupiedRow` 错误（陈旧、未同步、或边界值错误）。
- 客户端 `SimpleContainer(containerSize)` 已经包含 `containerSize`，**不需要为蒙版新增网络字段**——只需 `BackpackContainer` 暴露一个 getter。
- 隐藏 `DynamicScrollSlot` 当 `actualIndex >= containerSize` 已正确返回 EMPTY（`isInRange()` 行 60），但 cell 背景仍画格子——这是 Bug 2 视觉缺陷来源。
- 服务端 `getMaxScrollOffset()` 用 `containerSize` clamp `scrollOffset`，客户端 `getContentHeight()` 用 `lastOccupiedRow` clamp `scrollDistance`——两者维度不一致是潜在 Bug 1 候选根因（实施代理需要用日志确认是否实际触发）。
- `lastOccupiedRow` 在 `ServerPayloadHandler.handleOpenBackpack` 计算（行 60-66）；容器空时默认 `0`（应改 `-1`）。排序、取放后**完全不重算**。
- 现有 payload 模型：每个 payload 是单一 `record`，`Type` + `StreamCodec`；在 `TimeReward.registerPayloads` 用 `registrar.playToServer(...)` 注册。需追加 `registrar.playToClient(BackpackStatePayload.TYPE, ...)`。`ClientPayloadHandler` 目前是占位符（行 1-13）。
- `lastOccupiedRow` 在客户端 `BackpackContainer` 构造（行 113）从 buffer 读一次，**之后无 setter**——实现需新增 setter 并在收到 S2C 时调用。

### Metis Review
**已确认的 gap**（已闭环解决）：
- 未列 Scope OUT → 已新增 "范围之外" 9 条。
- 未定测试策略 → 已新增 "验证策略"：无 JUnit（GUI 不可单测）+ 强制 agent /visual-qa screenshot diff + 7-class containerSize 复现矩阵 + 日志断言。
- 两个 bug 关联分析：Metis 指出双重 clamp 不对称 (服务端 containerSize-based / 客户端 lastOccupiedRow-based) 是 Bug 1 的另一候选根因，需在 Task 1 落地日志验证。
- Metis 推荐 payload 方案 Option B：新增独立 `BackpackStatePayload(int containerId, int lastOccupiedRow)` S2C-only record，干净分离，不污染既有 C2S record 契约。

---

## Work Objectives

### 核心目标
修复 `BackpackScreen` 的两个用户报 bug（过滚空行、蒙版随窗口变），并修复上一级根因——服务端 `lastOccupiedRow` 在数据变更后不同步到客户端。

### 具体交付物
- `BackpackContainer.getContainerSize()` getter
- `BackpackContainer.setLastOccupiedRow(int)` 客户端 setter
- `BackpackContainer.recomputeLastOccupiedRow()` 服务端方法
- `BackpackContainer.sort()` 末尾自动调用 recompute
- `BackpackContainer.broadcastChanges()` override（含 dirty flag，避免每 tick O(n)）
- `BackpackStatePayload` S2C record + `ClientPayloadHandler.handleBackpackState`
- `TimeReward.registerPayloads` 追加 `playToClient` 注册
- `BackpackScreen.renderSlotCellBackgrounds()` 重写为 cell-级蒙版
- `BackpackScreen.BackpackScrollPanel` 添加 `reclampScrollDistance()` 方法
- `BackpackScreen.resize()` 在 initScrollPanel 后 re-clamp
- `ServerPayloadHandler.handleOpenBackpack` 容器空时 `lastOccupiedRow = -1`
- 诊断日志（打开/sort/scroll/sync 各打印关键字段）

### 完成定义
- [ ] `containerSize=11` 下打开背包，row 0 col 11 + 全部下 3 行被蒙版像素采样验证
- [ ] 大可滚动容器滚到底，截图显示 viewport 最后一行恰好为 `lastOccupiedRow` 行，下方无空行
- [ ] sort / /give / 取放 触发后，客户端 `menu.getLastOccupiedRow()` 在 1 tick 内 == 服务端 freshly-computed 值（日志对齐）
- [ ] 三种窗口尺寸（default / 小 / 大）下截图蒙版最后一格位置相对 `leftPos+SLOTS_X_OFFSET` 不变（不随窗口飘移）
- [ ] 容器空时背包行整片蒙版，无滚动条

### Must Have
- Cell-级蒙版按 `actualIndex >= containerSize` 谓词决策
- 蒙版坐标随 `scrollRowOffset` 滚动
- 服务端 sort / item-mutation 后 `lastOccupiedRow` 重算并通过 S2C 下发
- 客户端收到 S2C 后 re-clamp `scrollDistance` 至新 `getMaxScroll`
- 容器空时 `lastOccupiedRow = -1`（语义清晰）
- 新 S2C payload 命名 `BackpackStatePayload`，沿用既有 record/Type/StreamCodec 模式

### Must NOT Have（Guardrails）
- MUST NOT 修改 NeoForge `ScrollPanel` widget 源码（只 override 已有 `getContentHeight()` + 外部重 clamp）
- MUST NOT 修改 `DynamicScrollSlot.getActualIndex()` 映射契约或 `isInRange()` 行为
- MUST NOT 改动 `PlayerRewardManager` / `LZSavedData` / save 回调逻辑
- MUST NOT 改动 `BackpackContainer.buildSortComparator()` 内部排序逻辑
- MUST NOT 改动 search-filter 行为（保持 `DISABLED_SLOT_X = -2000` 方案）
- MUST NOT 引入 JUnit 对 GUI 渲染的测试（不可单测）
- MUST NOT 改 `imageWidth` / `SLOTS_X_OFFSET` / `SLOTS_Y_OFFSET` / `SLOT_SIZE` 等视觉常量
- MUST NOT 改 gradle、引入新依赖
- MUST NOT 触碰 `PaginationContainer`（无关菜单）
- MUST NOT 同时保留旧 row-level dim 块（必须完全替换为 cell-level）
- MUST NOT 引入同步 `scrollOffset` 的 S2C 字段（客户端是 scroll row 的 source-of-truth，不接收服务器该字段）

---

## Verification Strategy (MANDATORY)

> **无人工干预** — 全部 agent 可执行的。允许的"屏幕截图采样"通过游戏内置 F2 + 后处理工具做。

### 测试决策
- **基础设施存在**: YES（gradle / runClient / 现有 C2S payload 管线）；无 JUnit for GUI
- **自动化测试**: NO JUnit（GUI 不可单测）；强制的 agent QA
- **Framework**: NeoForge runClient + /visual-qa + 手工 log 探针
- **TDD**: 不适用（GUI bug 修复）

### QA 策略
- **每任务必须含 agent-executable QA scenario**（happy + failure/edge）
- **证据保存**: `.omo/evidence/task-{N}-{slug}.{ext}`
- **Frontend/UI**: NeoForge runClient 让玩家实际打开 GUI（代理用 `look_at` 截图 + 像素采样脚本）
- **网络同步**: 日志对齐断言（同一 tick 内 server send / client receive 的 `lastOccupiedRow` 字段值）
- **蒙版像素采样**: 列出每个 cell 的中心点坐标，取 1×1 像素 RGBA，对比 expected：in-range=白底 SC 纹理 / out-of-range=`0x80000000`

---

## Execution Strategy

### 并行执行波次

```
Wave 1 (Start Immediately — 类型 / 容器基础 / payload 定义):
├── Task 1: Backing field 日志探针确认根因 [deep]
├── Task 2: BackpackContainer 新增 containerSize getter + lastOccupiedRow setter + recompute helper [quick]
└── Task 3: BackpackStatePayload 定义 + 在 TimeReward.registerPayloads 注册 S2C [quick]

Wave 2 (After Wave 1 — 服务端逻辑 + 客户端逻辑):
├── Task 4: 服务端 lastOccupiedRow recompute on sort / item mutation + 发 BackpackStatePayload [deep]
├── Task 5: ClientPayloadHandler.handleBackpackState + 客户端 re-clamp scrollDistance [quick]
└── Task 6: BackpackScreen.renderSlotCellBackgrounds 重写为 cell-级蒙版 [visual-engineering]

Wave 3 (After Wave 2 — 集成 & resize 行为):
└── Task 7: resize() re-clamp + 期望场景矩阵 e2e 复现 + 证据收集 [deep]

Wave FINAL (After ALL tasks):
├── Task F1: 计划合规审计 [oracle]
├── Task F2: 代码质量审阅 [unspecified-high]
├── Task F3: 实机真测 QA [unspecified-high]
└── Task F4: 范围保真核对 [deep]
 → 提交结果 → 必须等用户 OK

关键路径: Task 2 → Task 4 → Task 5 → Task 7 → F1-F4 → user ok
并行加速: ~60% vs 串行
最大并发: 3 (Wave 1 / Wave 2)
```

### 依赖矩阵

| Task | Depends On | Blocks |
|------|------------|--------|
| 1    | —          | 4, 6 |
| 2    | —          | 4, 5, 6 |
| 3    | —          | 4 |
| 4    | 2, 3       | 5, 7 |
| 5    | 2, 3        | 7 |
| 6    | 1, 2       | 7 |
| 7    | 4, 5, 6     | F1-F4 |
| F1-F4| 7          | user ok |

### Agent Dispatch Summary

- **Wave 1 (3)**: T1 → `deep` (复现+根因确认), T2 → `quick` (轻度 API 增改), T3 → `quick` (payload record)
- **Wave 2 (3)**: T4 → `deep` (服务端同步语义), T5 → `quick` (客户端 handler), T6 → `visual-engineering` (GUI 重绘)
- **Wave 3 (1)**: T7 → `deep` (集成 + e2e)
- **FINAL (4)**: F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [x] 1. 加日志探针确认 Bug 1 实际触发根因

  **What to do**:
  - 临时在 `BackpackScreen.BackpackScrollPanel.mouseScrolled` 末尾打印 `scrollDistance`, `getMaxScroll()`, `(int)scrollDistance/SLOT_SIZE`, `menu.getLastOccupiedRow()`, `menu.getStorageContainer().getContainerSize()`, `visibleRows`
  - 临时在 `BackpackScreen.init()` 末尾打印打开瞬间 `lastOccupiedRow`, `containerSize`, `visibleRows`, `getContentHeight()-height`
  - 临时在 `BackpackScreen.resize()` 末尾打印同上 + 是否触发 re-clamp
  - 启动游戏，按以下顺序操作并截图游戏日志：`/give` 大量物品让容器占据 row 0..15 → 打开背包记录打开瞬间数据 → 滚到底记录数据 → 关闭 `/clear` 自身 → 在容器空状态打开背包记录数据 → 把物品 `/give` 进一组只占前 4 行 → 打开记录数据 → 滚到底
  - 据日志断定根因分类：(a) `lastOccupiedRow` 服务器与客户端一致但客户端 `getMaxScroll` 返回值异常；(b) 客户端拿到的 `lastOccupiedRow` 与服务端初始值不一致（stale）；(c) 数学正常但视觉"空行"实际是 `lastOccupiedRow` 行内部分空槽 cell；(d) 双重 clamp 不对称触发越滚
  - 在 plan 的 Self-Review 报告里归类结论，告诉后续 Task 4 / 6 是否需要调整方案

  **Must NOT do**:
  - 不修复任何 bug——本轮仅诊断
  - 不引入新持久状态/网络包
  - 不删除既有功能

  **Recommended Agent Profile**:
  > Select category based on task domain.
  - **Category**: `deep`
    - Reason: 需要在游戏内真实启动 runClient 操作并做日志分析，不是 quick trivial 任务
  - **Skills**: []
    - 无 skill 适用；纯日志+复现流程

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3)
  - **Blocks**: Tasks 4, 6（依赖根因结论调整修复方案）
  - **Blocked By**: None (start immediately)

  **References**:
  **Pattern References**:
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:457-583` - `BackpackScrollPanel` 全部内部逻辑，尤其 `mouseScrolled` 行 499-509、`applyScrollLimits` 后的 `scrollDistance` 状态、`getContentHeight` 行 479-483
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:112-118,343-351` - `init()` 与 `resize()`：打开 / 窗口变化点
  **API/Type References**:
  - `src/main/java/net/lanzr/time_reward/inventory/BackpackContainer.java:160-167,200-203` - `getLastOccupiedRow`, `getMaxScrollOffset`（双重 clamp 的服务端侧）
  **External References**:
  - 无

  **WHY Each Reference Matters**:
  - BackpackScreen.java:457-583 — `BackpackScrollPanel` 是 scrollDistance 唯一持有者，所有 clamp 计算路径
  - BackpackContainer.java:200-203 — 服务端 `getMaxScrollOffset` 用 `containerSize` clamp，与客户端 `getContentHeight` 维度不同，双重 clamp 不对称验证关键

  **Acceptance Criteria**:
  - [ ] 日志探针在指定 3 处加好，代码可编译
  - [ ] 至少 4 次 `runClient` 操作（开背包-滚到底；关闭-重开-空状态；give 后开-滚到底；排序 after remove）的日志截图保存
  - [ ] 根因分类报告写在 `.omo/evidence/task-1-rootcause-analysis.md` 含：原始日志摘录 + 归类 (a/b/c/d 中哪个或哪些) + 对 Task 4/6 的方案建议

  **QA Scenarios**:
  ```
  Scenario: 复现 "大容器滚到底看到空行"
    Tool: NeoForge runClient + 手工操作 + 日志 (look_at 截图日志控制台)
    Preconditions: 项目可正常 ./gradlew runClient
    Steps:
      1. ./gradlew runClient 启动客户端，进入单机世界
      2. 聊天框输入 /give @s minecraft:dirt 64 给自己一堆物品（500 个 → 等于多个槽位）
      3. 按 B 键打开背包
      4. 游戏日志控制台截图保存到 .omo/evidence/task-1-open-large-backpack-log.png
      5. 鼠标滚轮向下滚到极限
      6. 截图保存到 .omo/evidence/task-1-scroll-to-bottom-log.png
      7. 关闭背包，输入 /clear 清空自己
      8. 再按 B 打开空背包，再截图日志 `.omo/evidence/task-1-empty-open-log.png`
    Expected Result: 3 张日志截图存在；每张含字段 `[BackpackScreen] open/scroll/resize` 的打印行
    Failure Indicators: 日志找不到上述打印；截图未产生；或打印的 `getMaxScroll` 与计算出的 `(lastOccupiedRow+1-visibleRows)*18` 不一致
    Evidence: .omo/evidence/task-1-{open-large-backpack,scroll-to-bottom,empty-open}-log.png
  ```

  **Commit**: NO (诊断阶段，不向主干 commit)

---

- [x] 2. BackpackContainer 新增 `getContainerSize()` getter + `setLastOccupiedRow(int)` setter + `recomputeLastOccupiedRow()` 服务端 helper

  **What to do**:
  - 在 `BackpackContainer` 加 public `int getContainerSize()` 委托到 `storageContainer.getContainerSize()`
  - 加 public `void setLastOccupiedRow(int row)` setter，仅更新 `lastOccupiedRow` 字段（不动 broadcast）；调用方在 ClientPayloadHandler
  - 加 public `int recomputeLastOccupiedRow()` 服务端方法：自下而上扫描 `storageContainer`，遇首个非空槽返回 `i/COLS`，全空返回 `-1`；同步更新内部 `lastOccupiedRow` 字段并返回值
  - 加 private `boolean lastOccupiedDirty = true;` 字段，供后续 `broadcastChanges()` override 控制 O(n) 扫描频率

  **Must NOT do**:
  - 不改 `getMaxScrollOffset()` 行为（保留 containerSize-based）
  - 不在 setter 内 broadcast
  - 不修改字段访问修饰符以外的现有方法

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件 API 增删，约 20 行
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3)
  - **Blocks**: Tasks 4, 5, 6
  - **Blocked By**: None

  **References**:
  **Pattern References**:
  - `src/main/java/net/lanzr/time_reward/inventory/BackpackContainer.java:69-167` - 字段定义、现有 `getLastOccupiedRow`、Ctor 中 `lastOccupiedRow = buf.readInt()`
  - `src/main/java/net/lanzr/time_reward/network/ServerPayloadHandler.java:60-66` - 已有的"自下而上扫描非空槽"算法可直接复用（注意 ServerPayloadHandler 用错了默认 `0`，本任务改为 `-1`）

  **API/Type References**:
  - 无新类型

  **WHY Each Reference Matters**:
  - BackpackContainer.java:69-167 — setter/getter 添加位置；既有 private int 字段，无公共 setter，本任务引入
  - ServerPayloadHandler.java:60-66 — 引入到 BackpackContainer 后，ServerPayloadHandler 也可调本方法替代重复代码

  **Acceptance Criteria**:
  - [ ] `./gradlew compileJava` 通过
  - [ ] `BackpackContainer` 拥有公开的 `getContainerSize()`, `setLastOccupiedRow(int)`, `recomputeLastOccupiedRow()`，签名如上
  - [ ] `lastOccupiedDirty` 字段为 private boolean，默认 true

  **QA Scenarios**:
  ```
  Scenario: 编译型验证
    Tool: interactive_bash (./gradlew compileJava)
    Preconditions: 本任务代码已 stage
    Steps:
      1. ./gradlew compileJava
      2. 确认 BUILD SUCCESSFUL
    Expected Result: 0 编译错误；新方法签名可被同模块其他类调用
    Failure Indicators: 编译错误、签名不符合 spec
    Evidence: .omo/evidence/task-2-compile-output.txt (gradle 输出全文)

  Scenario: getter/setter 行为单测（用 REPL）
    Tool: interactive_bash / jshell 或同等
    Preconditions: 单测类可实例化 BackpackContainer（构造 dummy 调 FriendlyByteBuf）
    Steps:
      1. 构造一个 BackpackContainer(containerSize=15)
      2. menu.setLastOccupiedRow(7); 断言 menu.getLastOccupiedRow() == 7
      3. menu.getContainerSize() == 15; recomputeLastOccupiedRow() == -1（容器全空）
    Expected Result: 字段读写一致；空容器返回 -1
    Failure Indicators: 返回错值 / NPE
    Evidence: .omo/evidence/task-2-repl-test.txt
  ```

  **Commit**: YES (group with Wave 1)
  - Message: `refactor(backpack): 公开 BackpackContainer.containerSize/lastOccupiedRow API`
  - Files: `src/main/java/net/lanzr/time_reward/inventory/BackpackContainer.java`
  - Pre-commit: `./gradlew compileJava`

---

- [x] 3. 定义 `BackpackStatePayload` S2C record + 在 `TimeReward.registerPayloads` 注册 playToClient

  **What to do**:
  - 新建 `src/main/java/net/lanzr/time_reward/network/BackpackStatePayload.java`：record `(int containerId, int lastOccupiedRow)` 实现 `CustomPacketPayload`，含 `TYPE` 与 `STREAM_CODEC`（仿照 `ScrollChangePayload.java`）
  - ResourceLocation 用 `time_reward:backpack_state`
  - 在 `TimeReward.registerPayloads` 末尾追加
    ```
    registrar.playToClient(
        BackpackStatePayload.TYPE,
        BackpackStatePayload.STREAM_CODEC,
        ClientPayloadHandler::handleBackpackState
    );
    ```
  - 在 `ClientPayloadHandler` 加占位 `public static void handleBackpackState(BackpackStatePayload data, IPayloadContext ctx)` 暂时仅 `ctx.enqueueWork(() -> { });` 或简单 LOGGER.info（具体由 Task 5 实现）

  **Must NOT do**:
  - 不修改既有任何 payload 的 fields 或注册顺序
  - 不实现客户端行为（Task 5 负责）
  - 不引入除 `ByteBufCodecs.VAR_INT` 之外的 codec

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件 record 复制 + 2 行注册 + 1 个 handler 占位，约 35 行
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2)
  - **Blocks**: Tasks 4, 5
  - **Blocked By**: None

  **References**:
  **Pattern References**:
  - `src/main/java/net/lanzr/time_reward/network/ScrollChangePayload.java:1-25` - 必须严格复制的 record 风格
  - `src/main/java/net/lanzr/time_reward/network/SortPayload.java:1-25` - 第二个参考模式
  - `src/main/java/net/lanzr/time_reward/TimeReward.java:51-68` - `registerPayloads` 上下文，复用 `registrar.playToClient(...)`
  - `src/main/java/net/lanzr/time_reward/network/ClientPayloadHandler.java:1-13` - 添加 handle 方法的入口（目前占位空类）

  **WHY Each Reference Matters**:
  - ScrollChangePayload 完整 record 模板；2 字段 `VAR_INT` 的 StreamCodec.composite 模式
  - TimeReward L53-67 注册顺序与名字 "1" registrar；新注册追加到现有 3 个 playToServer 之后

  **Acceptance Criteria**:
  - [ ] `BackpackStatePayload.java` 创建，编译通过
  - [ ] `TimeReward.java` 中 `payloadHandlersEvent` 末尾新增 `registrar.playToClient(...)`
  - [ ] `ClientPayloadHandler.handleBackpackState` 方法存在空体
  - [ ] ResourceLocation 形如 `time_reward:backpack_state`

  **QA Scenarios**:
  ```
  Scenario: 编译注册成功
    Tool: interactive_bash (./gradlew compileJava)
    Steps:
      1. ./gradlew compileJava
    Expected Result: BUILD SUCCESSFUL；无 deprecation warning 关于 payload 覆盖
    Failure Indicators: 注册名重复；record 字段数与 codec 不匹配
    Evidence: .omo/evidence/task-3-compile.txt
  ```

  **Commit**: YES (group with Wave 1)
  - Message: `feat(net): 定义 BackpackStatePayload S2C 并注册`
  - Files: `BackpackStatePayload.java`, `TimeReward.java`, `ClientPayloadHandler.java`
  - Pre-commit: `./gradlew compileJava`

---

- [x] 4. 服务端 sort / item mutation 后重算 `lastOccupiedRow` 并通过 `BackpackStatePayload` 下发客户端

  **What to do**:
  - 在 `BackpackContainer.sort()` 末尾（行 252 `setScrollOffset(0)` 之后）调 `int newRow = recomputeLastOccupiedRow()`，然后向玩家发 `BackpackStatePayload(getId(), newRow)`（用 `PacketDistributor.sendToPlayer(player, BackpackStatePayload)` 或同等）
  - Override `BackpackContainer.broadcastChanges()`：调用 `super.broadcastChanges()`；若 `lastOccupiedDirty == true`，调 `recomputeLastOccupiedRow()`，若值与上次不同，向所有 `playerMenu` 玩家发 BackpackStatePayload；清 `lastOccupiedDirty = false`
  - 在 `BackpackContainer` 内所有可能 mutate storageContainer 的路径（`sort`, `quickMoveStack` 末尾, slot.set, slot.remove 等通过 listener）触发 `lastOccupiedDirty = true`；最干净的做法：在 `setupSlots` 时给每个 storage-backed DynamicScrollSlot 让基类 `setChanged()` 信号回写 dirty（或在 recompute 时直接扫，不依赖 dirty，由 broadcastChanges 触发但用 boolean 检查）
  - 调用 `setScrollOffset` 时也附带 `lastOccupiedDirty = true`（scroll 本身不改物品但客户端可能此时刚好同步过来）
  - 在 `ServerPayloadHandler.handleOpenBackpack` 容器空分支默认值 由 `0` 改为 `-1`（line 60 `int lastOccupiedRow = 0;` → `-1`）
  - 改 `handleSort` 末尾：调用 `bc.recomputeLastOccupiedRow()` 直接重算并通过 BackpackStatePayload 下发（避免依赖 broadcastChanges 时机）

  **Must NOT do**:
  - MUST NOT 在 broadcastChanges 每 tick 全扫（必须用 dirty flag 守门）
  - MUST NOT 同步 scrollOffset（仅 lastOccupiedRow）
  - MUST NOT 改 sort 比较逻辑
  - MUST NOT 触发 BackpackStatePayload 给非当前打开此菜单的玩家

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 网络 + 数据 + 多条 hook 路径，需要细心处理 dirty flag 与重复 broadcast
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2)
  - **Parallel Group**: Wave 2 (with Tasks 5, 6 — 但 5 需要 4 的接口；实际上等 4 后启动更稳)
  - **Blocks**: Task 5（init step 同步）、Task 7（集成）
  - **Blocked By**: Tasks 2, 3

  **References**:
  **Pattern References**:
  - `src/main/java/net/lanzr/time_reward/inventory/BackpackContainer.java:184-203,222-253` - `setScrollOffset` (broadcastChanges 调用点), `sort` (发出 dirty)
  - `src/main/java/net/lanzr/time_reward/network/ServerPayloadHandler.java:60-66,121-130` - 服务端既有的扫描与 sort handler
  **API/Type References**:
  - Task 3 输出的 `BackpackStatePayload` 完整签名
  - Task 2 输出的 `BackpackContainer.recomputeLastOccupiedRow()` / `lastOccupiedDirty`
  **External References**:
  - 无

  **WHY Each Reference Matters**:
  - 行 184-203: `setScrollOffset` 也 broadcast；需在该处加 dirty 触发
  - 行 222-253: `sort` 行 252 调 `setScrollOffset(0)`——是添加 recompute 的天然位点
  - ServerPayloadHandler:60-66: 修复 lastOccupiedRow 默认 0 → -1

  **Acceptance Criteria**:
  - [ ] 客户端打开背包后在 sort 操作发生 1 tick 内，日志显示客户端接收 BackpackStatePayload 携带新 `lastOccupiedRow` 值
  - [ ] shift-click 移出 1 件物品触发 broadcastChanges，1 tick 内客户端 lastOccupiedRow 更新
  - [ ] 容器空时服务器与客户端 `lastOccupiedRow` 均为 -1
  - [ ] `broadcastChanges` 不是每次都扫描（日志中 `recompute-call-count` 每 N tick 不超过一次，N≥10）

  **QA Scenarios**:
  ```
  Scenario: sort 同步
    Tool: NeoForge runClient + 日志
    Preconditions: Task 2/3 已实施；客户端打开背包
    Steps:
      1. /give @s minecraft:dirt 10；按 B 打开
      2. 按 B 关包，/clear；再按 B 打开（容器现在 0 件物）
      3. 再 /give @s minecraft:dirt 100；按 B 打开；点排序按钮
      4. 截图日志，对比 [BackpackContainer] recompute result 与 [ClientPayloadHandler] received row
    Expected Result: 两值相等；客户端 1 tick 内更新
    Failure Indicators: 客户端 lastOccupiedRow 仍为旧值
    Evidence: .omo/evidence/task-4-sort-sync-log.png

  Scenario: shift-click 同步
    Tool: runClient
    Steps:
      1. 打开背包（含 100 items）
      2. shift-click 移 1 槽物到自己背包
      3. 检查 server `recompute` 输出 与 client received
    Expected Result: 客户端 lastOccupiedRow 减少 1 或不变（若该行还有物），但 value 与服务端一致
    Failure Indicators: 客户端值不变
    Evidence: .omo/evidence/task-4-shiftclick-sync-log.png

  Scenario: 容器空打开
    Tool: runClient
    Steps:
      1. /clear；按 B 打开
      2. 日志确认 lastOccupiedRow = -1 在客户端 + 服务端
    Expected Result: 打印 `lastOccupiedRow=-1` 双侧
    Evidence: .omo/evidence/task-4-empty-open-log.png
  ```

  **Commit**: YES (group with Wave 2)
  - Message: `fix(backpack): 服务端 sort/item-mutation 后同步 lastOccupiedRow 至客户端`
  - Files: `BackpackContainer.java`, `ServerPayloadHandler.java`
  - Pre-commit: `./gradlew compileJava`

---

- [x] 5. `ClientPayloadHandler.handleBackpackState` 实现 + 客户端 re-clamp `scrollDistance`

  **What to do**:
  - 在 `ClientPayloadHandler.handleBackpackState` 内 `ctx.enqueueWork()`：
    - 若 `Minecraft.getInstance().player.containerMenu instanceof BackpackContainer bc && bc.containerId == data.containerId()`：调 `bc.setLastOccupiedRow(data.lastOccupiedRow())`
    - 若当前 `screen instanceof BackpackScreen bs`：调 `bs.onLastOccupiedRowChanged()`（新方法）
  - 在 `BackpackScreen` 加 public `void onLastOccupiedRowChanged()`：若 `scrollPanel != null`，让 panel 调 `reclamp()`
  - 在 `BackpackScrollPanel` 加 `void reclamp()`：`scrollDistance = Math.max(0, Math.min(scrollDistance, getMaxScroll())); updateSlotsPosition();`；若新 max 小于旧 `scrollDistance`，记录日志 "scroll clamped from X to Y"
  - 通知服务器不应被触发，scrollOffset 保持客户端为 source

  **Must NOT do**:
  - 不发回 C2S 包（避免回环）
  - 不调整 scrollOffset

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 短小 handler + 1 个 hook 方法
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2，与 6 并行；接 4 即可)
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 7
  - **Blocked By**: Tasks 2, 3 (and 4 to actually trigger)

  **References**:
  **Pattern References**:
  - `src/main/java/net/lanzr/time_reward/network/ServerPayloadHandler.java:37-94` - 服务端 enqueueWork 语境参考
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:276-282` - `updateSlotsPosition` 入口，reclamp 完成后调用
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:498-528` - `mouseScrolled` 现有更新模式（reclamp 后应触发同样 updateSlotsPosition）

  **WHY Each Reference Matters**:
  - 现有 enqueueWork 模式：必须用 ctx.enqueueWork 跳主线程
  - BackpackScreen L276-282 repositionSlots 调用入口

  **Acceptance Criteria**:
  - [ ] 客户端日志显示 `"scroll clamped from X to Y"` 在新 lastOccupiedRow 让旧 scroll 越界时
  - [ ] 客户端 `backpack.lastOccupiedRow` 字段与新值一致（用日志探针验证）
  - [ ] 屏幕上 viewport 最后一行不超过 lastOccupiedRow

  **QA Scenarios**:
  ```
  Scenario: 接到 BackpackStatePayload 后 re-clamp
    Tool: runClient + 日志
    Steps:
      1. 容器 100 物品打开背包，滚到底 (maxScroll@old)
      2. /clear 自己全清，关包——此时容器还在服务端原状（不是清服务端容器），改用 `/tyj-reward` 命令集合 or 简单点：直接调 sort 按钮 orderName 然后 sort 把物品重排
      注：若意味 testlastOccupiedRow 减小需让真实物品数减，可让 server sort + simultaneously reduce by shift-click 1 item into trash bin
    Expected Result: trigger BackpackStatePayload with smaller lastOccupiedRow；客户端 scroll 被回拉
    Evidence: .omo/evidence/task-5-reclamp-log.png

  Scenario: 失败 / 边缘（lastOccupiedRow 上升后无需 re-clamp）
    Tool: runClient
    Steps:
      1. 容器仅 4 行物品开背包（滚距 0）
      2. /give more；触发 sort → BackpackStatePayload 携带更大 lastOccupiedRow
    Expected Result: scrollDistance 仍 = 0（不需 re-clamp）
    Evidence: .omo/evidence/task-5-no-reclamp-needed.png
  ```

  **Commit**: YES
  - Message: `feat(client): 收到 BackpackStatePayload 后刷新 lastOccupiedRow & re-clamp scrollDistance`
  - Files: `ClientPayloadHandler.java`, `BackpackScreen.java`
  - Pre-commit: `./gradlew compileJava`

---

- [x] 6. 重写 `renderSlotCellBackgrounds()` 为 cell-级蒙版，按 `actualIndex >= containerSize` 决策

  **What to do**:
  - 删除 `renderSlotCellBackgrounds()` 现有"行末整行 dim"块（行 328-337）
  - 保留前半段——画 SC 槽纹理（visibleRows 行 × COLS 列），但**对超出 containerSize 的 cell 改为只画蒙版而不画 SC 纹理**
  - 重写为如下二段：
    1. 遍历 viewport（displayRow 0..visibleRows-1, col 0..COLS-1）：
       - `scrollRowOffset = scrollPanel != null ? scrollPanel.getScrollRowOffset() : 0;`
       - `actualIndex = scrollRowOffset * COLS + displayRow * COLS + col;`
       - 计算屏幕坐标 `cellX = leftPos + SLOTS_X_OFFSET + col * SLOT_SIZE;` `cellY = topPos + SLOTS_Y_OFFSET + (displayRow - scrollRowOffset_before)*SLOT_SIZE;`（如果是滚动启用 scrollPanel.visible，但 SC tile 在 panel 外画在 screen 上而不是 panel 内）
       - WAIT：当前 renderBg 在 panel 外画整片 SC，蒙版画在前面；重写必须保证 SC tile 与 mask 同一坐标系
       - **更正方案**：仍按当前 `renderBg` 画 SC tile（已正确铺底），然后在 viewport 内对 each cell: 若 out-of-range（actualIndex ≥ containerSize），在该 cell 上画蒙版 `fill(cellX, cellY, cellX+18, cellY+18, 0x80000000)`
    2. 同时保留"行末整行蒙版"能力：当某行所有 COLS 项都 out-of-range，用单个大 fill rect（性能优化），跳过 12 次 cell fill
  - 边界处理：displayRow - scrollRowOffset < 0 的情况：这些 row 在 panel 内不显示（panel 起点是 topPos+17），但 SC texture 仍只画 visibleRows 行从 topPos+17 开始 → 当 scrollRowOffset > 0，上滚显示的 row 总数仍是 visibleRows（NeoForge 内部 scroll 把"已滚出的"用 scissor 裁掉）。**实际渲染必须用 viewport-row 索引**（从 panel 顶部开始第 0 行），不再用 absolute displayRow 减 scrollRowOffset 计算位置
  - 复核：BackpackScreen L312 `slotRows = visibleRows;` L322 blit 起点 `topPos + SLOTS_Y_OFFSET + renderedY * SLOT_SIZE` —— renderedY 是 viewport-row 索引（0..visibleRows-1），cellY = topPos + SLOTS_Y_OFFSET + renderedY * SLOT_SIZE；不会受 scrollRowOffset 影响（因为 panel scissor 已截走超出部分，SC texture 静态铺底）
  - 因此蒙版 cell 屏幕位置也用 `topPos + SLOTS_Y_OFFSET + viewportRowIndex * SLOT_SIZE`（与 SC tile 完全同位）
  - 重要：当 ScrollPanel 启用，scissor 把超出 panel top/bottom 的 blit 全部裁掉。当前 SC texture blit 起点 = topPos+17，整个区域在 panel 内 → 用户滚动时蒙版"跟随槽位滚动"的视觉效果，实际上通过 scissor 实现的"被裁掉的 row 不再可见——只有 panel 内 viewport 行可见"
  - 因此蒙版按 viewport-row 计算 OK

  **关键实现**：
  ```
  int containerSize = menu.getContainerSize();
  int scrollRowOffset = (scrollPanel != null) ? scrollPanel.getScrollRowOffset() : 0;
  for (int viewportRow = 0; viewportRow < visibleRows; viewportRow++) {
      for (int col = 0; col < COLS; col++) {
          int actualIndex = (scrollRowOffset + viewportRow) * COLS + col;
          if (actualIndex >= containerSize) {
              int cellX = leftPos + SLOTS_X_OFFSET + col * SLOT_SIZE;
              int cellY = topPos + SLOTS_Y_OFFSET + viewportRow * SLOT_SIZE;
              guiGraphics.fill(cellX, cellY, cellX + SLOT_SIZE, cellY + SLOT_SIZE, 0x80000000);
          }
      }
  }
  ```
  - 性能优化：若某行整行 out-of-range（即 `(scrollRowOffset + viewportRow) * COLS >= containerSize`），用单个 row 大小 fill rect 覆盖整行 12 个 cell

  **Must NOT do**:
  - 不修改 Blit SC 纹理段（保留行 311-326）
  - 不引入新的 widget
  - 不基于 `stackFilter` 调整蒙版（filter 只影响物品，不影响 cell 蒙版）
  - 不引入 lastOccupiedRow 至 renderSlotCellBackgrounds 的使用（只用 containerSize）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: GUI 渲染重写，对外观 + pixel 级蒙版精确度有视觉工程要求
  - **Skills**: []（可选 /visual-qa skill 在 F3 真测阶段）

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2)
  - **Parallel Group**: Wave 2 (with Tasks 4, 5)
  - **Blocks**: Task 7
  - **Blocked By**: Task 1 (根因结论可能让方案微调)、Task 2（用 getContainerSize）

  **References**:
  **Pattern References**:
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:311-338` - 现有 renderSlotCellBackgrounds，必须完全重写后半段行 328-337
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:525-528,538-564` - `repositionSlots` 中 scrollRowOffset 的正确推导，本任务沿用 `scrollPanel.getScrollRowOffset()`
  - `src/main/java/net/lanzr/time_reward/inventory/DynamicScrollSlot.java:50-62` - actualIndex 公式相同，本任务在 render 时复用同公式
  **API/Type References**:
  - Task 2 输出的 `BackpackContainer.getContainerSize()`

  **WHY Each Reference Matters**:
  - L311-338 必须完全替换为新算法；现有 firstEmptyRow / visibleRows 比较逻辑是 Bug 2 的根因
  - DynamicScrollSlot 50-62：保证 mask cell 与实际插入位置一致

  **Acceptance Criteria**:
  - [ ] containerSize=11 → 截图显示 row 0 col 11 像素 RGBA ≈ 0x80000000
  - [ ] containerSize=15 → row 1 col 3..11 全部蒙版（实际为 row 1 col 0..2 in-range, col 3..11 out-of-range）
  - [ ] containerSize=12 → 0 out-of-range cell；下 3 行整行蒙版（当 visibleRows=4）
  - [ ] containerSize=96 (12 边界) 和 144 完全 in-range 时 0 蒙版
  - [ ] 容器大可滚动滚到底 → row (containerSize-1)/COLS col (containerSize-1)%COLS 是最后一个 in-range cell，之后剩余 viewport cell 全部蒙版
  - [ ] 蒙版随 scroll 同步移动（不出现蒙版停在前一帧位置的视觉滞后）
  - [ ] 性能：每帧 fill 调用数：max(行批量，合计不超过 COLS*visibleRows=144)

  **QA Scenarios**:
  ```
  Scenario: containerSize=11 cell-级蒙版
    Tool: runClient + 截图 + look_at 像素采样
    Preconditions: 容器实际 size=11（可通过 `/tyj-reward` 调级或临时跑脚本mock）
    Steps:
      1. 按 B 开背包；截图
      2. 用 look_at / 脚本采样 cell (row=0,col=11) 中心像素 RGBA
      3. 采样 cell (row=0,col=10) 中心像素 RGBA
      4. 采样 cell (row=1,col=0) 中心像素 RGBA
    Expected Result: col=11 RGBA=0x80000000；col=10 与 row=1 col 0 均为正常 SC 纹理（但 row=1 col 0 后 row 1 之后才全蒙？等：containerSize=11 → row 0 col 0..10 in-range, col 11 out；row 1+ 全 out）
    Failure Indicators: col=11 没蒙版或全蒙版；row 1 col 0 没蒙版
    Evidence: .omo/evidence/task-6-cell-mask-11.png, .omo/evidence/task-6-mask-pixels.txt

  Scenario: 容器大可滚动蒙版随滚
    Tool: runClient
    Steps:
      1. /give 100 物品 → 按 B → 滚到底
      2. 截图记录 viewport 末尾几个 cell 的蒙版状态
      3. 再向上滚 1 行，截图
    Expected Result: 蒙版随 row 上移一致；底部不出现多余空行
    Failure Indicators: 滚动后蒙版位置滞后
    Evidence: .omo/evidence/task-6-scroll-mask.png

  Scenario: 三窗口尺寸蒙版像素位置
    Tool: runClient + 窗口 resize 通过 F11/fullscreen 切换 + 截图
    Steps:
      1. 在 default、小、大三种窗口尺寸下打开 containerSize=11 背包
      2. 计算蒙版最后一格屏幕 X = leftPos + SLOTS_X_OFFSET + 11 * SLOT_SIZE
    Expected Result: 蒙版像素位置 X 只随 leftPos 变（随窗口 origin），但 X - leftPos - SLOTS_X_OFFSET 恒等于 11*18
    Evidence: .omo/evidence/task-6-window-invariance.png
  ```

  **Commit**: YES
  - Message: `fix(gui): slot 蒙版按 containerSize 改为 cell 级，与 scroll 行为一致`
  - Files: `BackpackScreen.java`
  - Pre-commit: `./gradlew compileJava`

---

- [x] 7. `resize()` 新增 re-clamp 调用 + 执行 7-场景复现矩阵收集证据

  **What to do**:
  - 在 `BackpackScreen.resize()` line 343-351 末尾，`initScrollPanel()` 之后追加：若 `scrollPanel != null`，调 `scrollPanel.reclamp()`（Task 5 引入的同方法）；确保窗口缩放后 scroll证券不出越界
  - 同时确认 `resize()` 末尾再调 `updateSlotsPosition()`
  - 移除 Task 1 加入的临时日志探针（保留关键 console.log 不在产物上线）
  - 按照"复现矩阵"7 个 containerSize scenario（11, 15, 96, 144, 300, 165, 0）逐项 runClient 实测，截图存 `.omo/evidence/task-7-matrix/`，每场景产出一张全屏 + 一份日志对齐文件
  - 三窗口尺寸蒙版像素位置一致性测试（同 Task 6 中的窗口测试，整合到本任务的"实机真测"中以确保 resize 后仍生效）

  **Must NOT do**:
  - 不修改 `resize()` 中 `visibleRows` 计算公式
  - 不引入除日志外的产物代码

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 集成 + 多场景实机真测 + 证据收集，工作量大
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocks**: F1-F4
  - **Blocked By**: Tasks 4, 5, 6

  **References**:
  **Pattern References**:
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:343-351` - 现有 resize hook
  - `src/main/java/net/lanzr/time_reward/client/gui/BackpackScreen.java:276-282` - updateSlotsPosition 入口

  **WHY Each Reference Matters**:
  - resize 行为是 Task 6 视觉验证的一部分；reclamp 行为依赖 Task 5 输出的 BackpackScrollPanel.reclamp

  **Acceptance Criteria**:
  - [ ] resize() 中 initScrollPanel 后存在 `if (scrollPanel != null) scrollPanel.reclamp(); updateSlotsPosition();` 调用
  - [ ] 7 个 scenario 截图与日志全部存在于 `.omo/evidence/task-7-matrix/`
  - [ ] 三窗口尺寸蒙版像素位置 invariance 写在 `.omo/evidence/task-7-window-matrix.md`
  - [ ] 临时日志探针从源码中已清理

  **QA Scenarios**:
  ```
  Scenario: resize 后仍能正确滚动不越界
    Tool: runClient + 窗口缩放
    Steps:
      1. 容器有 200 物品，开包滚到底（maxScroll）
      2. 用操作系统快捷键窗口缩到最小
      3. 确认 scroll 未越界（日志显示 reclamp 将 scrollDistance 限制至新 max）
      4. 再缩至最大，确认 cell 蒙版位置 X 与 default 窗口下一致（相对 leftPos）
    Expected Result: 三尺寸下蒙版 X 位置相对 leftPos 不变；scrollDistance 始终 ∈ [0, maxScroll]
    Evidence: .omo/evidence/task-7-resize-reclamp-log.png

  Scenario: 7-场景矩阵
    Tool: runClient (7次) + 截图 + 日志
    Steps:
      - 11: 输入使容器 size=11（或最接近的 level），开包，截图+日志
      - 15: 同 11
      - 96: /give 96 物品
      - 144: /give 144 物品
      - 300: /give 300 物品
      - 165: /give 100 物品（lastOccupiedRow=8 scenario 边界）
      - 0: /clear 全清，验证空背包
    Expected Result: 共 14 个文件（7×2）于 evidence/task-7-matrix/
    Evidence: .omo/evidence/task-7-matrix/
  ```

  **Commit**: YES
  - Message: `fix(backpack): resize 后 re-clamp scrollDistance & 收集 7场景证据`
  - Files: `BackpackScreen.java`, `.omo/evidence/task-7-*`
  - Pre-commit: `./gradlew compileJava`

---

## Final Verification Wave (MANDATORY)

> 4 个 review agent 并行，全部 APPROVE 后向用户呈现结果并等用户 OK。

- [x] F1. **计划合规审计** — `oracle`
  完整读 plan。每个 "Must Have"：读文件 / 触发命令验证实施存在；每个 "Must NOT Have"：grep 搜索禁用模式，违反则 file:line 列出。检查 `.omo/evidence/` 下证据文件是否齐全。比对交付物清单与 plan 一致。
  输出: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **代码质量审阅** — `unspecified-high`
  运行 `./gradlew compileJava`、`./gradlew build`。审查变更文件含 `as any`/`@ts-ignore`（替换为 Java 等价如 `@SuppressWarnings`）、空 catch、`System.out.println`、注释掉的代码、未用 import。AI slop 检查：过度注释、过度抽象、泛名（temp/data/result）。日志：debug 级别，不能产物线在 informational。
  输出: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [~] F3. **实机真测 QA** — `unspecified-high`
  从 clean state 起，按"复现矩阵"逐个 containerSize 场景执行：连入客户端、`/give` 生成物品、按 B 开包、滚到底、采样像素、截屏、对齐日志。跨任务集成（蒙版与滚动协同工作）。存到 `.omo/evidence/final-qa/`。
  输出: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **范围保真核对** — `deep`
  对每个 Task：读 "What to do"，读实际 diff（git diff 主仓库），验证 1:1——所有 spec 都做了，没做 spec 之外的。检查 "Must NOT do"。检测跨任务污染。Flag 未授权改动。
  输出: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Wave 1**: `feat(backpack): 暴露 containerSize/lastOccupiedRow 字段 & 定义 BackpackStatePayload` - BackpackContainer.java, BackpackStatePayload.java, TimeReward.java
- **Wave 2**: `fix(backpack): 服务端重算+同步 lastOccupiedRow / 客户端重 clamp / cell-level 蒙版` - BackpackContainer.java, ServerPayloadHandler.java, ClientPayloadHandler.java, BackpackScreen.java
- **Wave 3**: `fix(backpack): resize re-clamp & 复现矩阵 e2e 证据` - BackpackScreen.java, .omo/evidence/

---

## Success Criteria

### 验证命令
```bash
./gradlew compileJava     # 预期: BUILD SUCCESSFUL
./gradlew runClient       # 代理启动客户端进行手工 QA
```

### 最终清单
- [ ] 所有 "Must Have" 存在
- [ ] 所有 "Must NOT Have" 缺失
- [ ] ./gradlew build 通过
- [ ] 7 个 containerSize scenario 全部 QA 通过
- [ ] 三窗口尺寸蒙版像素位置稳定
- [ ] sort 后客户端 lastOccupiedRow 1 tick 内 == 服务端值