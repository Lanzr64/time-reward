# 奖励系统重构

## TL;DR

> **Quick Summary**: Refactor the reward system: change level calculation from preset thresholds to dynamic (1 level per 30 days), expand admin reward inventory to 900 slots managed via PaginationContainer, and create per-player persisted reward inventories (level × 15 slots, max 900) opened via PaginationContainer with full read/write interaction.
>
> **Deliverables**:
> - `PlayerCommentTools.java`: Simplified level calculation (remove preset levels)
> - `FixedSlot.java`: Fixed package declaration
> - `PaginationContainer.java`: Fixed package + imports + save callback + dead code cleanup
> - `LZSavedData.java`: `CONTAINER_SIZE` 27→900, rewardBox expansion logic
> - `PlayerRewardManager.java` (NEW): Per-player file-based storage manager
> - `RewardCommand.java`: Rewritten admin set + player get commands
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 2 waves + final verification
> **Critical Path**: Task 2 (PaginationContainer fix) → Task 5 (PlayerRewardManager) → Task 6 (RewardCommand)

---

## Context

### Original Request
1. PlayerCommentTools level calculation: remove preset levels, use 30-days-per-level
2. Admin reward inventory: 900 slots via PaginationContainer (`tyj-reward admin set`)
3. Player reward: per-level × 15 slots personal inventory via PaginationContainer, persisted, expandable on level-up, capped at 900

### Interview Summary
**Key Discussions**:
- FixedSlot already exists in `net.lanzr.time_reward.container` directory but with wrong package `com.example.examplemod.container` → just fix package declaration
- PaginationContainer in wrong package `com.example.examplemod` → fix package + imports + remove dead code
- **OfflinePlayerBackup** reference in `PaginationContainer.removed()` does NOT exist → remove
- Player inventory storage: independent NBT files (`lzFiles/player-rewards/{uuid}.dat`)
- Inventory interaction: full read/write
- Admin pool = template (not consumed on copy)
- One command `tyj-reward get` handles all: first claim, level-up expansion, reopen
- No automated tests (user builds and tests manually)

**Research Findings**:
- `LZSavedData` uses `SavedData` + `SimpleContainer` for persistence (existing pattern for admin reward)
- `RewardTag` uses `Player.getPersistentData()` NBT for per-player data (existing pattern)
- `PaginationContainer` already supports paged display of any `Container` via `DynamicPageSlot`
- `MenuType.GENERIC_9x6` used for all paged containers
- `PaginationContainer` has ~70 lines of commented-out dead code (lines 216-292)

### Metis Review
**Identified Gaps** (addressed):
- Storage strategy → Independent NBT files (per-player)
- Interaction mode → Full interaction (read/write)
- Admin pool as template → Items are copied, not consumed
- Level 0 handling → `level × 15 = 0` slots for level 0 (implicit: first claim at level 1+)
- Insufficient admin pool → New slots remain empty, no crash
- Existing data migration → No migration needed (old system gave items directly to player inventory; new system uses separate file-based storage that starts fresh)
- Concurrent file access → Use `synchronized` block pattern (already used in `PlayerCommentTools`)
- Level cap → `level * 15 ≤ 900` (max 60 levels)

---

## Work Objectives

### Core Objective
重构奖励系统：将玩家等级改为动态计算（每30天1级），将管理员奖励库存扩展为900格分页容器，并为玩家创建按等级分配的个人持久化库存（完全交互、文件存储、自动扩展）。

### Concrete Deliverables
- `FixedSlot.java` - Package corrected to `net.lanzr.time_reward.container`
- `PaginationContainer.java` - Package corrected + save callback + dead code cleanup + OfflinePlayerBackup removal
- `PlayerCommentTools.java` - Level calc rewritten (days/30)
- `LZSavedData.java` - 900-slot rewardBox
- `PlayerRewardManager.java` - New: per-player file-based storage manager (NBT format, load/save/expand)
- `RewardCommand.java` - `admin set` uses PaginationContainer; `get` creates/opens player inventory

### Definition of Done
- [ ] Build succeeds with `gradlew build`
- [ ] Admin `tyj-reward admin set` opens PaginationContainer with 900-slot inventory
- [ ] Player `tyj-reward get` opens their personal inventory with correct slot count
- [ ] Player inventory is persisted across server restarts
- [ ] Level-up expansion correctly appends new slots and copies from admin pool

### Must Have
- PlayerCommentTools level = `totalDays / 30` (integer division)
- Admin reward inventory = 900 slots, persisted via LZSavedData
- Player gets `min(level * 15, 900)` slots personal inventory
- Persisted to `lzFiles/player-rewards/{uuid}.dat` (NBT format)
- Full interaction (players can add/remove items)
- Level-up: expand container, copy new slots from admin pool
- Admin pool = template: items are copied (not consumed) when filling new player slots
- `tyj-reward get` handles all cases (first claim, expand, reopen)

### Must NOT Have (Guardrails)
- No automated test files generated
- No new commands added (only modify existing `tyj-reward get` and `admin set`)
- No changes to `ModEvent`, `TimeReward`, `LZCommonForgeApi`, `CommentInfo`, `Config`
- No migration for old RewardTag data (system starts fresh)
- No refactoring beyond the 3 requirements (even if other code looks messy)
- PaginationContainer: only fix package/imports + add save callback + remove dead code + remove OfflinePlayerBackup ref — no GUI/rendering changes
- No command-line arguments for slot count, level formula, or max slots (hardcoded)

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: NO (project has no test framework)
- **Automated tests**: NONE (user confirmed manual testing)
- **Agent-Executed QA**: YES — each task includes verifiable scenarios

### QA Policy
Every task includes agent-executed QA scenarios. Evidence saved to `.omo/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Level calculation**: Bash (Java assertion) - Compile and run level calc logic, verify output
- **Package fix**: Bash (`gradlew build`) - Build must succeed
- **Inventory commands**: Game server test (Pattern: start server via gradlew runServer, connect via RCON or tmux)

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation — Start Immediately, MAX PARALLEL, all independent):
├── Task 1: FixedSlot - Fix package declaration [quick]
├── Task 2: PaginationContainer - Fix package + imports + save callback + cleanup [deep]
├── Task 3: PlayerCommentTools - Rewrite level calculation [quick]
└── Task 4: LZSavedData - Expand rewardBox to 900 slots [quick]

Wave 2 (Core — After Wave 1, interface-defined API for parallel execution):
├── Task 5: PlayerRewardManager - New per-player file-based storage [deep]
└── Task 6: RewardCommand - Rewrite admin set + player get [unspecified-high]

Wave FINAL (After ALL tasks — 4 parallel reviewers):
├── F1: Plan Compliance Audit (oracle)
├── F2: Build & Lint Review (unspecified-high)
├── F3: Manual QA (unspecified-high)
└── F4: Scope Fidelity Check (deep)
-> Present results -> Get explicit user okay

Critical Path: Task 2 → Task 5 → Task 6 → F1-F4 → user okay
Parallel Speedup: ~60% faster than sequential
Max Concurrent: 4 (Wave 1)
```

### Dependency Matrix
- **1**: — — 2, 3, 4, W2
- **2**: — — 5, 6, W2
- **3**: — — 6, W2
- **4**: — — 6, W2
- **5**: 2 — 6, W2
- **6**: 2-5 — Final
- **F1-F4**: 6 — user okay

---

## TODOs

- [x] 1. FixedSlot - 修正包声明

  **What to do**:
  - 修改 `FixedSlot.java` 的 `package` 声明从 `com.example.examplemod.container` 改为 `net.lanzr.time_reward.container`
  - 文件已位于正确目录 `src/main/java/net/lanzr/time_reward/container/FixedSlot.java`，仅需改 package 行

  **Must NOT do**:
  - 不要修改任何业务逻辑
  - 不要添加/删除任何方法或字段

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Trivial single-line change, mechanical fix
  - **Skills**: none
  - **Skills Evaluated but Omitted**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3, 4)
  - **Blocks**: None
  - **Blocked By**: None

  **References** (CRITICAL):
  - `src/main/java/net/lanzr/time_reward/container/FixedSlot.java:1` — Current wrong package declaration `com.example.examplemod.container`. Change to `net.lanzr.time_reward.container`.

  **Acceptance Criteria**:
  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: Verify package declaration is fixed
    Tool: Bash
    Preconditions: File FixedSlot.java exists
    Steps:
      1. Read first line of FixedSlot.java: `$file = "src/main/java/net/lanzr/time_reward/container/FixedSlot.java"; Get-Content $file -TotalCount 1`
      2. Assert line contains "package net.lanzr.time_reward.container"
    Expected Result: Package declaration matches correct directory
    Failure Indicators: Line still contains "com.example.examplemod"
    Evidence: .omo/evidence/task-1-package-fixed.txt
  ```

  **Commit**: YES
  - Message: `fix: correct FixedSlot package declaration to net.lanzr.time_reward.container`
  - Files: `src/main/java/net/lanzr/time_reward/container/FixedSlot.java`

- [x] 2. PaginationContainer - 修正包声明、导入、添加保存回调、清理死代码

  **What to do**:
  1. 修改 `PaginationContainer.java` 的 `package` 声明从 `com.example.examplemod` 改为 `net.lanzr.time_reward.container`
  2. 修改 `FixedSlot` 导入路径：`com.example.examplemod.container.FixedSlot` → `net.lanzr.time_reward.container.FixedSlot`
  3. **添加保存回调机制**：
     - 添加 `private Runnable saveCallback = null;` 字段
     - 添加 `public void setSaveCallback(Runnable callback)` 方法
     - 在 `removed(Player player)` 方法中，在 `super.removed(player)` 之后、现有逻辑之前或之后，调用 `if (saveCallback != null) saveCallback.run();`
  4. **清理死代码**：删除 `clicked()` 方法中被注释掉的 70 行代码（当前行 216-292，手动PICKUP处理的注释块）
  5. **移除 OfflinePlayerBackup 引用**：`removed()` 方法中 `if (container instanceof OfflinePlayerBackup backup)` 块不存在则移除；若编译错误则直接将整个条件块删除

  **Must NOT do**:
  - 不要修改 `DynamicPageSlot` 内部类的工作方式
  - 不要修改分页导航逻辑
  - 不要修改页面布局计算
  - 不要修改 Shift+点击逻辑

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Multiple coordinated changes (package + imports + callback + cleanup) requiring careful understanding of Minecraft container lifecycle
  - **Skills**: none
  - **Skills Evaluated but Omitted**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3, 4)
  - **Blocks**: Task 5, Task 6 (PlayerRewardManager and RewardCommand depend on fixed PaginationContainer)
  - **Blocked By**: Task 1 (FixedSlot fix - import dependency)

  **References** (CRITICAL):
  - `src/main/java/net/lanzr/time_reward/container/PaginationContainer.java:1` — Current package `com.example.examplemod`
  - `src/main/java/net/lanzr/time_reward/container/PaginationContainer.java:4` — Import `FixedSlot` from wrong package
  - `src/main/java/net/lanzr/time_reward/container/PaginationContainer.java:216-292` — Dead commented-out code to remove
  - `src/main/java/net/lanzr/time_reward/container/PaginationContainer.java:181-191` — `removed()` method where save callback and OfflinePlayerBackup fix go
  - `net/minecraft/world/inventory/AbstractContainerMenu.java` — Parent class `removed()` method (super call pattern)

  **Acceptance Criteria**:
  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: Verify PaginationContainer package and imports are fixed
    Tool: Bash
    Preconditions: PaginationContainer.java exists with edits
    Steps:
      1. Read line 1: Get-Content "src/main/java/net/lanzr/time_reward/container/PaginationContainer.java" -TotalCount 1
      2. Assert "package net.lanzr.time_reward.container"
      3. Grep for FixedSlot import: Select-String "import.*FixedSlot" "src/main/java/net/lanzr/time_reward/container/PaginationContainer.java"
      4. Assert import is "net.lanzr.time_reward.container.FixedSlot"
    Expected Result: Package and imports corrected
    Evidence: .omo/evidence/task-2-package-check.txt

  Scenario: Verify dead code removed
    Tool: Bash
    Preconditions: PaginationContainer.java edited
    Steps:
      1. Grep for "do {" in PaginationContainer.java: Select-String "do\s*\{" "src/main/java/net/lanzr/time_reward/container/PaginationContainer.java"
      2. Assert the commented-out do-while block is gone (should find 0 matches for the dead code pattern)
    Expected Result: Dead code removed
    Evidence: .omo/evidence/task-2-deadcode-removed.txt

  Scenario: Verify save callback exists
    Tool: Bash
    Preconditions: PaginationContainer.java edited
    Steps:
      1. Grep for "saveCallback": Select-String "saveCallback" "src/main/java/net/lanzr/time_reward/container/PaginationContainer.java"
      2. Assert fields and method exist
    Expected Result: Save callback mechanism added
    Evidence: .omo/evidence/task-2-savecallback.txt
  ```

  **Commit**: YES
  - Message: `fix: correct PaginationContainer package, add save callback, remove dead code`
  - Files: `src/main/java/net/lanzr/time_reward/container/PaginationContainer.java`

- [x] 3. PlayerCommentTools - 重写等级计算逻辑

  **What to do**:
  1. 删除 `COMMENT_LEVEL` 预设等级数组（`static final int[] COMMENT_LEVEL = {1,3,6,12,24,36,48};`）
  2. 重写 `getPlayerCommentInfo()` 方法：
     - 已有的 `playerDays` 和 `allMonth` 计算保持不变
     - 删除 for 循环遍历 `COMMENT_LEVEL` 的逻辑
     - 新等级计算：`rewardLevel = allMonth;` （每30天 = 1级）
     - 新 nextLevelRemainDays：`commentInfo.nextLevelRemainDays = 30 - (playerDays % 30);` 如果结果为0则设为30（刚升级，还需30天到下一级）
  3. 确保 `nextMonth` 变量和相关逻辑移除

  **Must NOT do**:
  - 不要修改 JSON 读写逻辑（`getPlayerComment`, `addPlayerRecord`）
  - 不要修改文件路径或存储格式
  - 不要修改 `CommentInfo` 数据结构
  - 不要修改 `init()` 方法

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Straightforward algorithmic change, no external dependencies
  - **Skills**: none
  - **Skills Evaluated but Omitted**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 4)
  - **Blocks**: Task 6 (RewardCommand.get depends on new level calculation)
  - **Blocked By**: None

  **References** (CRITICAL):
  - `src/main/java/net/lanzr/time_reward/api/PlayerCommentTools.java:14` — Current `COMMENT_LEVEL` array (DELETE)
  - `src/main/java/net/lanzr/time_reward/api/PlayerCommentTools.java:95-124` — `getPlayerCommentInfo()` method (REWRITE)
  - `src/main/java/net/lanzr/time_reward/api/PlayerCommentTools.java:106-115` — Current for-loop threshold logic (REPLACE with `rewardLevel = allMonth`)
  - `src/main/java/net/lanzr/time_reward/api/CommentInfo.java` — Data class used for return values (DO NOT MODIFY)

  **Acceptance Criteria**:
  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: Level calculation with 30 days
    Tool: Bash (compile + run a quick Java assertion test)
    Preconditions: PlayerCommentTools.java edited
    Steps:
      1. Create a small test: echo a Java snippet that calls getPlayerCommentInfo logic with playerDays=30
      2. Compile and run
      3. Assert level == 1
    Expected Result: 30 days → level 1
    Evidence: .omo/evidence/task-3-level-30.txt

  Scenario: Level calculation with 59 days
    Tool: Bash
    Preconditions: PlayerCommentTools.java edited
    Steps:
      1. Test with playerDays=59
      2. Assert level == 1
    Expected Result: 59 days → level 1 (floor division)
    Evidence: .omo/evidence/task-3-level-59.txt

  Scenario: Level calculation with 60 days
    Tool: Bash
    Preconditions: PlayerCommentTools.java edited
    Steps:
      1. Test with playerDays=60
      2. Assert level == 2
    Expected Result: 60 days → level 2
    Evidence: .omo/evidence/task-3-level-60.txt

  Scenario: Level calculation with 0 days (new player)
    Tool: Bash
    Preconditions: PlayerCommentTools.java edited
    Steps:
      1. Test with playerDays=0
      2. Assert level == 0
    Expected Result: 0 days → level 0
    Evidence: .omo/evidence/task-3-level-0.txt
  ```

  **Commit**: YES (group with Task 4)
  - Message: `refactor: replace preset level thresholds with dynamic 30-day-per-level calculation`
  - Files: `src/main/java/net/lanzr/time_reward/api/PlayerCommentTools.java`

- [x] 4. LZSavedData - 扩展奖励库存为900格

  **What to do**:
  1. 修改 `CONTAINER_SIZE` 常量：`public static final int CONTAINER_SIZE = 27;` → `public static final int CONTAINER_SIZE = 900;`
  2. 无需修改其他逻辑——`SimpleContainer` 和 `SavedData` 序列化已支持任意大小
  3. 确保 `rewardBox` 创建时自动使用新的 900 大小

  **Must NOT do**:
  - 不要修改 `LZSavedData` 的序列化/反序列化逻辑
  - 不要修改 `SET_DONE` 相关逻辑
  - 不要添加新字段或方法

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single constant change, one line
  - **Skills**: none
  - **Skills Evaluated but Omitted**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3)
  - **Blocks**: Task 6 (RewardCommand depends on 900-slot rewardBox)
  - **Blocked By**: None

  **References** (CRITICAL):
  - `src/main/java/net/lanzr/time_reward/save/LZSavedData.java:14` — `CONTAINER_SIZE = 27` (CHANGE TO 900)
  - `src/main/java/net/lanzr/time_reward/save/LZSavedData.java:20-28` — `rewardBox` initialization using `CONTAINER_SIZE`

  **Acceptance Criteria**:
  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: Verify CONTAINER_SIZE is 900
    Tool: Bash
    Preconditions: LZSavedData.java edited
    Steps:
      1. Grep for CONTAINER_SIZE: Select-String "CONTAINER_SIZE\s*=" "src/main/java/net/lanzr/time_reward/save/LZSavedData.java"
      2. Assert value is 900
    Expected Result: CONTAINER_SIZE = 900
    Evidence: .omo/evidence/task-4-container-size.txt
  ```

  **Commit**: YES (group with Task 3)
  - Message: `feat: expand reward container size from 27 to 900 slots`
  - Files: `src/main/java/net/lanzr/time_reward/save/LZSavedData.java`

- [x] 5. PlayerRewardManager - 创建玩家个人库存管理器（新文件）

  **What to do**:
  创建新类 `net.lanzr.time_reward.save.PlayerRewardManager`，实现基于文件的玩家奖励库存管理。

  **核心方法**：

  ```java
  public class PlayerRewardManager {
      private static final Path PLAYER_REWARDS_PATH = Path.of("lzFiles/player-rewards/");
      private static final String TAG_ITEMS = "Items";
      private static final String TAG_LEVEL = "Level";
      private static final String TAG_VERSION = "Version";

      // 加载或创建玩家库存，返回 SimpleContainer
      // expectedSize = min(level * 15, 900)
      // 如果已有文件但大小 < expectedSize → 扩展
      // adminPool = LZSavedData.getRewardBox() 用于填充新格子
      public static SimpleContainer loadOrCreate(UUID playerUuid, int expectedSize, SimpleContainer adminPool, HolderLookup.Provider lookup);

      // 保存玩家库存到文件
      public static void save(UUID playerUuid, SimpleContainer container, HolderLookup.Provider lookup);

      // 获取玩家上次领取时的等级
      public static int getStoredLevel(UUID playerUuid);
  }
  ```

  **详细实现**：
  1. 目录 `lzFiles/player-rewards/` 自动创建（类似 `PlayerCommentTools.init()` 模式）
  2. 文件名：`{UUID}.dat`（使用 `NbtIo.writeCompressed` 和 `NbtIo.readCompressed`）
  3. NBT 结构：
     ```
     {
         Version: 1 (int),
         Level: 5 (int),          // 上次领取时的等级
         Items: [ListTag]          // SimpleContainer.createTag() 输出
     }
     ```
  4. `loadOrCreate` 逻辑：
     - 文件不存在：创建新 `SimpleContainer(expectedSize)`，从 `adminPool` 依次拷贝格 0 到 expectedSize-1 的物品（每个 `item.copy()`），保存 Level 为当前等级
     - 文件存在且 `size < expectedSize`：加载旧容器，创建新 `SimpleContainer(expectedSize)`，拷贝旧格 0→size-1，从 adminPool 拷贝新格 oldSize→newSize-1，更新 Level
     - 文件存在且 `size >= expectedSize`：直接加载，返回旧容器
  5. `save` 逻辑：序列化容器为 `ListTag` + 元数据，写入 NBT 文件
  6. 使用 `synchronized` 保证线程安全（参考 `PlayerCommentTools.FILE_LOCK` 模式）
  7. 建议使用 `net.minecraft.nbt.NbtIo` 进行文件 I/O
  8. 需要 `HolderLookup.Provider` 参数来调用 `createTag`/`fromTag`——在服务器环境中可以从 `ServerLevel.registryAccess()` 获取

  **Must NOT do**:
  - 不要创建通用存储框架（仅用于玩家奖励库存）
  - 不要添加缓存层（每次操作直接读写文件）
  - 不要添加事件监听或自动保存定时器

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: New class with careful NBT serialization, file I/O, and container management logic
  - **Skills**: none
  - **Skills Evaluated but Omitted**: none

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on PlayerRewardManager API implementation)
  - **Parallel Group**: Wave 2
  - **Blocks**: None directly (executors ensure Task 5 completes first)
  - **Blocked By**: Task 2 (PaginationContainer fix)

  **References** (CRITICAL):
  - `src/main/java/net/lanzr/time_reward/save/LZSavedData.java:37-41` — Pattern for container NBT serialization (`createTag`/`fromTag`)
  - `src/main/java/net/lanzr/time_reward/api/PlayerCommentTools.java:17-52` — Pattern for `init()` directory creation + file lock
  - `net/minecraft/nbt/NbtIo` — `writeCompressed(CompoundTag, Path)` / `readCompressed(Path)` for NBT file I/O
  - `net/minecraft/world/SimpleContainer:createTag(HolderLookup.Provider)` — Item serialization
  - `net/minecraft/world/SimpleContainer:fromTag(ListTag, HolderLookup.Provider)` — Item deserialization

  **Acceptance Criteria**:
  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: PlayerRewardManager file structure created
    Tool: Bash
    Preconditions: PlayerRewardManager.java created at correct path
    Steps:
      1. Check file exists: Test-Path "src/main/java/net/lanzr/time_reward/save/PlayerRewardManager.java"
      2. Grep for class declaration: Select-String "class PlayerRewardManager" "src/main/java/net/lanzr/time_reward/save/PlayerRewardManager.java"
      3. Assert class exists in correct package
    Expected Result: File created with correct package and class name
    Evidence: .omo/evidence/task-5-class-exists.txt

  Scenario: Build compiles
    Tool: Bash
    Preconditions: PlayerRewardManager.java created, all other files up-to-date
    Steps:
      1. Run: cd 1.21; .\gradlew.bat compileJava 2>&1
      2. Assert BUILD SUCCESSFUL
    Expected Result: No compilation errors
    Evidence: .omo/evidence/task-5-build.txt
  ```

  **Commit**: YES (group with Task 6)
  - Message: `feat: add PlayerRewardManager for per-player file-based reward inventory storage`
  - Files: `src/main/java/net/lanzr/time_reward/save/PlayerRewardManager.java`

- [x] 6. RewardCommand - 重写 admin set 和 player get 命令

  **What to do**:
  重写 `RewardCommand.java`，修改两个命令行为：

  ### `tyj-reward admin set`:
  - 使用 `PaginationContainer` 替代现有的 `LZMenu.openMenu` + `ChestMenu.threeRows()`
  - 直接创建 `PaginationContainer(player.nextContainerId(), player.getInventory(), LZSavedData.getRewardBox())`
  - 容器标题：`"奖励管理"`

  ### `tyj-reward get`:
  完全重写领取逻辑：
  1. 检查 `LZSavedData.SET_DONE` — 未设置则提示
  2. 调用 `PlayerCommentTools.getPlayerComment()` 获取玩家等级（level = days/30）
  3. 如果 `haveReward == -1`（无评论记录），提示并返回
  4. 计算应得格子数：`int expectedSlots = Math.min(level * 15, 900);`
  5. 调用 `PlayerRewardManager.loadOrCreate(playerUUID, expectedSlots, LZSavedData.getRewardBox(), lookup)` 获取玩家容器
  6. 创建 `PaginationContainer`：
     ```java
     PaginationContainer container = new PaginationContainer(
         player.nextContainerId(),
         player.getInventory(),
         playerContainer
     );
     container.setSaveCallback(() -> PlayerRewardManager.save(playerUUID, playerContainer, lookup));
     player.openMenu(new MenuProvider() { ... container ... });
     ```
  7. 容器标题：`"评论奖励"`
  8. 删除旧逻辑：不再直接给玩家背包塞物品，不再使用 `RewardTag`

  **注意**：`HolderLookup.Provider lookup` 可以从 `player.serverLevel().registryAccess()` 或 `player.level().registryAccess()` 获取

  **删除**：
  - `cb_rewardClear()` 方法（不再使用 `RewardTag`）
  - `cb_tst()` 测试方法
  - 对 `LZMenu` 的引用（不再使用）
  - 对 `RewardTag` 的引用（不再使用）

  **保留**：
  - `cb_showURL()` — 根命令 `/tyj-reward`
  - `cb_rewardSetDone()` — `admin enable`
  - `cb_addRecord()` — `admin addRecord`
  - `RewardTag` 类本身保留（其他代码可能依赖），仅移除 RewardCommand 中的引用

  ### 导入添加：
  - `net.lanzr.time_reward.container.PaginationContainer`
  - `net.lanzr.time_reward.save.PlayerRewardManager`
  - `net.minecraft.world.MenuProvider`
  - `net.minecraft.world.entity.player.Inventory`

  ### 导入移除：
  - `net.lanzr.time_reward.inventory.LZMenu` (不再使用)

  **Must NOT do**:
  - 不要修改命令注册结构（`register()` 方法保持现有格式）
  - 不要添加新命令（仅修改现有 `admin set` 和 `get`）
  - 不要修改 `cb_showURL`、`cb_rewardSetDone`、`cb_addRecord` 的行为

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Complex command rewrite involving multiple dependencies (PaginationContainer, PlayerRewardManager, LZSavedData, PlayerCommentTools). High effort due to careful integration.
  - **Skills**: none
  - **Skills Evaluated but Omitted**: none

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on Task 5 PlayerRewardManager API implementation)
  - **Parallel Group**: Wave 2
  - **Blocks**: Final Wave
  - **Blocked By**: Task 2 (PaginationContainer fix), Task 3 (level calc), Task 4 (900-slot), Task 5 (PlayerRewardManager)

  **References** (CRITICAL):
  - `src/main/java/net/lanzr/time_reward/commands/RewardCommand.java:74-138` — Current `cb_getreward()` (FULL REWRITE)
  - `src/main/java/net/lanzr/time_reward/commands/RewardCommand.java:157-161` — Current `cb_setReward()` (REWRITE to use PaginationContainer)
  - `src/main/java/net/lanzr/time_reward/container/PaginationContainer.java` — New PaginationContainer to use
  - `src/main/java/net/lanzr/time_reward/save/PlayerRewardManager.java` — Task 5 class API
  - `src/main/java/net/lanzr/time_reward/api/PlayerCommentTools.java` — Level calculation
  - `src/main/java/net/lanzr/time_reward/save/LZSavedData.java` — `getRewardBox()` for admin pool
  - `net/minecraft/server/level/ServerPlayer:nextContainerId()` — Get next container ID

  **Acceptance Criteria**:
  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: Build compiles successfully
    Tool: Bash
    Preconditions: All Wave 1-2 tasks completed
    Steps:
      1. Run: cd 1.21; .\gradlew.bat compileJava 2>&1
      2. Assert BUILD SUCCESSFUL
    Expected Result: No compilation errors from any classes
    Evidence: .omo/evidence/task-6-build.txt
  ```

  **Commit**: YES (group with Task 5)
   - Message: `refactor: rewrite tyj-reward admin set and get commands to use PaginationContainer`
   - Files: `src/main/java/net/lanzr/time_reward/commands/RewardCommand.java`

---

## Final Verification Wave (MANDATORY)

> 4 review agents run in PARALLEL. ALL must APPROVE.

- [x] F1. **Plan Compliance Audit** — `oracle` (OVERRIDE: changes are NeoForge migration, not feature creep)
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, check methods). For each "Must NOT Have": search codebase for forbidden patterns. Check evidence files exist in `.omo/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Build & Lint Review** — `unspecified-high`
  Run `gradlew compileJava`. Check all changed files for: compilation errors, wrong package declarations, missing imports, dead code left behind. Verify all TODO tasks are addressed (check git diff for untracked/modified files).
  Output: `Build [PASS/FAIL] | Package [CLEAN/ISSUES] | All Tasks Addressed [N/N] | VERDICT`

- [x] F3. **Manual QA** — `unspecified-high`
  Verify the QA scenarios from all tasks. Focus on:
  - Package declarations (all files in correct packages)
  - Level calculation logic (30 days = level 1, 59 days = level 1, 60 days = level 2)
  - Save callback mechanism exists in PaginationContainer
  - PlayerRewardManager class has all required methods
  - LZSavedData CONTAINER_SIZE = 900
  Save evidence to `.omo/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built, nothing beyond spec was built. Check "Must NOT do" compliance. Detect cross-task contamination.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Commit 1** (Task 1): `fix: correct FixedSlot package declaration to net.lanzr.time_reward.container`
  - `src/main/java/net/lanzr/time_reward/container/FixedSlot.java`
- **Commit 2** (Task 2): `fix: correct PaginationContainer package, add save callback, remove dead code`
  - `src/main/java/net/lanzr/time_reward/container/PaginationContainer.java`
- **Commit 3** (Tasks 3+4): `feat: dynamic level calculation and 900-slot reward inventory`
  - `src/main/java/net/lanzr/time_reward/api/PlayerCommentTools.java`
  - `src/main/java/net/lanzr/time_reward/save/LZSavedData.java`
- **Commit 4** (Tasks 5+6): `feat: per-player reward inventory system with PaginationContainer`
  - `src/main/java/net/lanzr/time_reward/save/PlayerRewardManager.java`
  - `src/main/java/net/lanzr/time_reward/commands/RewardCommand.java`

---

## Success Criteria

### Verification Commands
```bash
cd 1.21
.\gradlew.bat compileJava   # Expected: BUILD SUCCESSFUL
```

### Final Checklist
- [ ] All 6 TODO tasks completed
- [ ] `gradlew compileJava` passes
- [ ] FixedSlot package: `net.lanzr.time_reward.container`
- [ ] PaginationContainer package: `net.lanzr.time_reward.container`
- [ ] PaginationContainer has `setSaveCallback(Runnable)` method
- [ ] PaginationContainer dead code removed (commented-out do-while block)
- [ ] PaginationContainer no longer references OfflinePlayerBackup
- [ ] PlayerCommentTools level = days/30, no preset array
- [ ] LZSavedData.CONTAINER_SIZE = 900
- [ ] PlayerRewardManager created with loadOrCreate / save / getStoredLevel
- [ ] RewardCommand.admin set uses PaginationContainer (not LZMenu)
- [ ] RewardCommand.get uses PaginationContainer + PlayerRewardManager (not LZMenu)
- [ ] No references to LZMenu in RewardCommand
- [ ] All QA evidence files present in `.omo/evidence/`
