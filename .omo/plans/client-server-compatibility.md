# 客户端-服务器兼容性改造

## TL;DR

> **Quick Summary**: 移除自定义 `ModMenuTypes.BACKPACK` 注册（注册表同步断开根因），将 B 键背包功能改造为绕过 `player.openMenu()` 的自定义 Payload 通信方式，使未安装 Mod 的客户端能正常连接服务器，同时保留 Mod 安装客户的 12 列滚动背包 UI。
>
> **Deliverables**:
> - 移除 `time_reward:backpack` 注册表条目（MenuType 注册消除）
> - 3 个新增自定义 Payload：`OpenBackpackScreenPayload`、`BackpackClickPayload`、`BackpackClosePayload`
> - 服务端 BackpackContainer 管理：Map 存储 + 自定义 Payload 交互
> - 客户端 BackpackScreen 保留 `AbstractContainerScreen<BackpackContainer>` 架构
> - `/tyj-reward get`（PaginationContainer）零改动
>
> **Estimated Effort**: Medium-Large（约 8-12 个文件改动）
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: 注册解除 → 新 Payload → 服务端流程改造 → 客户端点击改造 → 集成测试

---

## Context

### Original Request
Mod 安装客户端可正常连接服务器（当前因自定义 MenuType 注册表同步导致断开），未安装客户端可通过 `/tyj-reward get` 命令访问奖励容器，安装 Mod 客户端可通过 B 键打开自定义 12 列滚动背包界面。

### Interview Summary
**Key Discussions**:
- **方案 A（确认）**: 移除自定义 MenuType，B 键容器完全通过自定义 Payload 通信
- **保留 12 列滚动**: 客户端 BackpackScreen 保持 `AbstractContainerScreen<BackpackContainer>` 架构
- **BackpackContainer**: 服务端保留用于逻辑处理（排序、滚动、保存）
- **屏幕注册**: 客户端使用自定义 MenuType（非注册表）用于 BackpackContainer 构造

**Research Findings**:
- `ModMenuTypes.BACKPACK` 是唯一注册在 `Registries.MENU` 并同步的条目（根因 ✅）
- `PaginationContainer` 使用 `MenuType.GENERIC_9x6` — 已兼容 ✅
- B 键在 `Dist.CLIENT` 下注册 — 未安装客户端不会触发 ✅
- 5 个现有 Payload：3 C2S + 2 S2C — 可作为改造基础 ✅

### Metis Review
> Metis 调用被中断，但 Oracle Phase 1 验证确认所有需求清晰完整。
- **评分、点击协议问题**: 通过服务端 `BackpackContainer.clicked()` + S2C 状态回推解决
- **容器生命周期**: 服务端 `Map<UUID, BackpackContainer>` 管理 + `removed()` 手动触发

---

## Work Objectives

### Core Objective
移除自定义 MenuType 注册，使未安装 Mod 的客户端能正常连接服务器，同时通过自定义 Payload 机制保持 Mod 安装客户端的 B 键 12 列滚动背包功能完整可用。

### Concrete Deliverables
- `ModMenuTypes.java` — 移除 `MENUS.register(modEventBus)` 调用，保留类文件但移除 BACKPACK 注册项
- `BackpackContainer.java` — 支持可配置 MenuType（不再硬编码 `ModMenuTypes.get()`）
- `OpenBackpackScreenPayload.java` — 新增 S2C Payload：通知客户端打开背包屏幕
- `BackpackClickPayload.java` — 新增 C2S Payload：槽位点击交互
- `BackpackClosePayload.java` — 新增 C2S Payload：关闭背包通知
- `BackpackCarriedUpdatePayload.java` — 新增 S2C Payload：光标物品同步
- `ServerPayloadHandler.java` — 处理 B 键流：不再调用 `player.openMenu()`
- `ClientPayloadHandler.java` — 处理新 Payload，管理客户端容器状态
- `BackpackScreen.java` — 覆盖鼠标/键盘事件发送自定义 Payload
- `ClientModEvents.java` — 更新初始化逻辑
- `TimeReward.java` — 更新 Payload 注册

### Definition of Done
- [ ] 未安装 Mod 的纯客户端可连接服务器，且登录后不因注册表断开
- [ ] 未安装 Mod 的客户端可执行 `/tyj-reward get` 正常使用分页容器
- [ ] 安装 Mod 的客户端按 B 键可打开 12 列滚动背包
- [ ] 安装 Mod 的客户端可在背包中正常取放物品、滚动、排序
- [ ] 关闭背包后物品自动保存

### Must Have
- 移除 `ModMenuTypes.BACKPACK` 在 `Registries.MENU` 的注册
- B 键打开的 BackpackContainer 不再走 `player.openMenu()`
- 客户端 BackpackScreen 维持 AbstractContainerScreen 架构
- 所有新 Payload C2S 只由 Mod 安装客户端发送（B 键仅 Mod 客户端触发）
- 服务端 S2C Payload 只在收到 C2S 后作为响应发送

### Must NOT Have (Guardrails)
- 不对 `/tyj-reward get`（PaginationContainer）做任何修改
- 不添加 Mixin
- 不添加登录阶段握手检测
- 不修改 AP 兼容层、语言文件、纹理资源
- 不添加 Fabric 支持

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: NO
- **Automated tests**: None
- **Agent-Executed QA**: MANDATORY — 每个任务至少 1 Happy Path + 1 错误/边界场景

### QA Policy
- **服务端**: 启动专用测试服务器，通过命令/观察日志验证
- **客户端**: 使用 Minecraft Client 运行模式 + 截图/日志验证
- **网络**: 通过日志确认 Payload 正确发送/接收

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation - independent tasks, can run in parallel):
├── Task 1: 移除自定义 MenuType 注册 [quick]
├── Task 2: 新增 OpenBackpackScreenPayload [quick]
├── Task 3: 新增 BackpackClickPayload [quick]
├── Task 4: 新增 BackpackClosePayload [quick]
├── Task 5: 新增 BackpackCarriedUpdatePayload [quick]
├── Task 6: 新增客户端 ContainerStateManager [unspecified-high]
└── Task 7: 新增服务端 BackpackContainerManager [unspecified-high]

Wave 2 (After Wave 1 - integration, MUST have Task 11 first):
├── (first) Task 11: 修改 BackpackContainer（可配置 MenuType + 自定义广播）[deep]
├── (then parallel) Task 8: 修改 ServerPayloadHandler（B 键流程不走 openMenu）[unspecified-high]
├── (then parallel) Task 9: 修改 ClientPayloadHandler + ClientModEvents [unspecified-high]
├── (then parallel) Task 10: 修改 BackpackScreen（自定义 Payload 点击交互）[deep]
└── (after 8+9+10) Task 12: 修改 TimeReward（Payload 注册更新）[quick]

Wave FINAL (After ALL tasks):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
```

### Dependency Matrix
- **1**: - → 8, 9, 10, 11, 12, 1
- **2-5**: - → 8, 9, 10, 1
- **6**: - → 9, 10, 1
- **7**: - → 8, 10, 1
- **8**: 1, 7, 11 → 12, 2
- **9**: 1, 6 → 12, 2
- **10**: 1, 6, 11 → 12, 2
- **11**: 1 → 8, 10, 2
- **12**: 8, 9, 10 → F1-F4, FINAL

---

## TODOs

- [x] 1. 移除自定义 MenuType 注册

  **What to do**:
  - 在 `TimeReward.java` 构造函数中移除 `ModMenuTypes.MENUS.register(modEventBus)` 行
  - 在 `ModMenuTypes.java` 中删除或注释掉 `BACKPACK` 的 `Supplier` 定义和 `get()` 方法
  - 保留 `ModMenuTypes` 类文件（可留作未来扩展占位）
  - 验证编译通过且 `Registries.MENU` 中不再包含 `time_reward:backpack`

  **Must NOT do**:
  - 不删除 `ModMenuTypes.java` 文件本身
  - 不修改其他 MenuType（不存在其他自定义 MenuType）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 简单删除+注释操作，不需要复杂分析
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2-7)
  - **Blocks**: Tasks 8, 9, 10, 11, 12
  - **Blocked By**: None (can start immediately)

  **References**:
  - `TimeReward.java:44-52` — 当前构造函数中 `ModMenuTypes.MENUS.register(modEventBus)` 调用
  - `ModMenuTypes.java:32-33` — `BACKPACK` Supplier 定义行
  - `ModMenuTypes.java:40-42` — `get()` 方法

  **Acceptance Criteria**:
  - [ ] `Registries.MENU` 的 DeferredRegister 不再注册 `time_reward:backpack`
  - [ ] `TimeReward.java` 构造函数中移除了 `ModMenuTypes.MENUS.register(modEventBus)` 调用
  - [ ] 编译通过

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 编译验证
    Tool: Bash (gradlew build)
    Preconditions: 项目在干净的编译状态
    Steps:
      1. 运行 `gradlew build`
      2. 确认编译通过无错误
    Expected Result: BUILD SUCCESSFUL
    Evidence: .omo/evidence/task-1-build.txt
  ```

  **Commit**: YES
  - Message: `fix: remove custom MenuType registration from synced registry`
  - Files: `TimeReward.java`, `ModMenuTypes.java`

---

- [x] 2. 新增 S2C Payload: OpenBackpackScreenPayload

  **What to do**:
  - 创建 `net.lanzr.time_reward.network.OpenBackpackScreenPayload` record
  - 实现 `CustomPacketPayload` 接口
  - Channel: `time_reward:open_backpack_screen`
  - 字段:
    - `containerId: int` — 容器 ID
    - `containerSize: int` — 存储容器总大小
    - `scrollOffset: int` — 当前滚动偏移
    - `lastOccupiedRow: int` — 最后有物品的行
    - `title: Component` — 屏幕标题
  - StreamCodec: 使用 `ByteBufCodecs` 组合编码
  - 使用 `RegistryFriendlyByteBuf`（可选的，如果 Component 需要）

  **Must NOT do**:
  - 不要添加全量物品数据（初始打开时不需要所有物品——客户端等待后续 SlotSync）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准 Payload 定义，与其他 Payload 模式相同
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3-7)
  - **Blocks**: Tasks 8, 9, 10
  - **Blocked By**: None

  **References**:
  - `BackpackStatePayload.java` — 现有 S2C Payload 模式
  - `BackpackSlotSyncPayload.java` — RegistryFriendlyByteBuf 使用模式

  **Acceptance Criteria**:
  - [ ] `OpenBackpackScreenPayload` record 定义正确
  - [ ] Channel 名称为 `time_reward:open_backpack_screen`
  - [ ] 包含所有必需字段：`containerId`, `containerSize`, `scrollOffset`, `lastOccupiedRow`, `title`
  - [ ] StreamCodec 编码/解码正确
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash (gradlew compileJava)
    Preconditions: 代码写入完成
    Steps:
      1. 运行 gradlew compileJava
    Expected Result: 编译成功
    Evidence: .omo/evidence/task-2-compile.txt
  ```

  **Commit**: NO (group with Task 8 or 9)

---

- [x] 3. 新增 C2S Payload: BackpackClickPayload

  **What to do**:
  - 创建 `net.lanzr.time_reward.network.BackpackClickPayload` record
  - 实现 `CustomPacketPayload` 接口
  - Channel: `time_reward:backpack_click`
  - 字段:
    - `containerId: int`
    - `slotId: int` — 点击的槽位索引
    - `button: int` — 0=左键, 1=右键
    - `clickTypeOrdinal: int` — ClickType.ordinal()
    - `hasCarried: boolean` — 客户端光标上是否有物品
  - StreamCodec: 使用 `ByteBufCodecs` 组合编码
  - 注意：不传输全量光标物品，只传是否有物品标记（服务端自行管理光标状态）

  **Must NOT do**:
  - 不要传输 `ItemStack`（减轻 Payload 大小，利用服务端权威状态）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准 Payload 模式
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 4-7)
  - **Blocks**: Tasks 8, 9, 10
  - **Blocked By**: None

  **References**:
  - `SortPayload.java` — 现有 C2S Payload 模式（VAR_INT 编码）

  **Acceptance Criteria**:
  - [ ] Payload 编译通过
  - [ ] 编码/解码正确

  **QA Scenarios**:
  ```
  Scenario: Payload 编译验证
    Tool: Bash (gradlew compileJava)
    Preconditions: 代码写入完成
    Steps:
      1. 运行 gradlew compileJava
    Expected Result: 编译成功
    Evidence: .omo/evidence/task-3-compile.txt
  ```

  **Commit**: NO (group with Task 8 or 9)

---

- [x] 4. 新增 C2S Payload: BackpackClosePayload

  **What to do**:
  - 创建 `net.lanzr.time_reward.network.BackpackClosePayload` record
  - 实现 `CustomPacketPayload` 接口
  - Channel: `time_reward:backpack_close`
  - 字段:
    - `containerId: int`
  - StreamCodec: `ByteBufCodecs.VAR_INT`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 最简 Payload
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 1)
  - **Blocks**: Tasks 8, 9
  - **Blocked By**: None

  **References**:
  - `OpenBackpackPayload.java` — 无参数 Payload 模式

  **Acceptance Criteria**:
  - [ ] Payload 编译通过
  - [ ] 编码/解码正确

  **QA Scenarios**:
  ```
  Scenario: Payload 编译验证
    Tool: Bash (gradlew compileJava)
    Preconditions: 代码写入完成
    Steps:
      1. 运行 gradlew compileJava
    Expected Result: 编译成功
    Evidence: .omo/evidence/task-4-compile.txt
  ```

  **Commit**: NO (group with Task 8 or 9)

---

- [x] 5. 新增 S2C Payload: BackpackCarriedUpdatePayload

  **What to do**:
  - 创建 `net.lanzr.time_reward.network.BackpackCarriedUpdatePayload` record
  - Channel: `time_reward:backpack_carried_update`
  - 字段:
    - `containerId: int`
    - `carried: ItemStack` — 光标物品（空=无物品）
  - 使用 `RegistryFriendlyByteBuf` + `ItemStack.OPTIONAL_STREAM_CODEC`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准 Payload 模式
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 1)
  - **Blocks**: Tasks 8, 9, 10
  - **Blocked By**: None

  **References**:
  - `BackpackSlotSyncPayload.java` — ItemStack StreamCodec 使用模式

  **Acceptance Criteria**:
  - [ ] Payload 编译通过
  - [ ] ItemStack 序列化/反序列化正确
  - [ ] OptionalItemStack（空物品）正确处理

  **QA Scenarios**:
  ```
  Scenario: Payload 编译验证
    Tool: Bash (gradlew compileJava)
    Preconditions: 代码写入完成
    Steps:
      1. 运行 gradlew compileJava
    Expected Result: 编译成功
    Evidence: .omo/evidence/task-5-compile.txt
  ```

  **Commit**: NO (group with Task 8 or 9)

---

- [x] 6. 新增客户端 ContainerStateManager

  **What to do**:
  - 创建 `net.lanzr.time_reward.client.ContainerStateManager` 类
  - 管理客户端侧打开的自定义背包状态：
    - `BACKPACK_CONTAINER_IDS: Set<Integer>` — 标记哪些 containerId 是自定义背包
    - `registerBackpack(containerId)` — 注册一个 containerId 为背包
    - `isBackpackContainer(containerId)` — 检查是否为背包
    - `unregisterBackpack(containerId)` — 取消注册
  - 提供 `getStorageContainer(containerId): SimpleContainer` — 获取背包物品存储
  - 线程安全的（使用 `ConcurrentHashMap` 或同步块）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 客户端状态管理，需要线程安全设计
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Tasks 9, 10
  - **Blocked By**: None

  **References**:
  - `ClientPayloadHandler.java` — 客户端 Payload 处理模式

  **Acceptance Criteria**:
  - [ ] 编译通过
  - [ ] `registerBackpack(id)` + `isBackpackContainer(id)` 返回正确
  - [ ] `unregisterBackpack(id)` 后 `isBackpackContainer(id)` 返回 false
  - [ ] 线程安全：多线程并发访问不抛异常

  **QA Scenarios**:
  ```
  Scenario: 容器 ID 注册与检测
    Tool: Bash (gradlew compileJava + 单元测试验证)
    Preconditions: 代码写入完成
    Steps:
      1. 运行 gradlew compileJava
    Expected Result: 编译成功
    Evidence: .omo/evidence/task-6-compile.txt
  ```

  **Commit**: NO (group with Task 9)

---

- [x] 7. 新增服务端 BackpackContainerManager

  **What to do**:
  - 创建 `net.lanzr.time_reward.server.BackpackContainerManager` 类
  - 管理登录后服务端打开的 BackpackContainer 实例：
    - `playerContainers: Map<UUID, BackpackContainer>`
    - `openContainer(player, storageContainer): BackpackContainer` — 创建并存储
      - 创建 BackpackContainer（使用 `MenuType.GENERIC_9x6`）
      - 设置 `null` Synchronizer（阻止自动广播）
      - 存入 Map
      - **手动设置** `player.containerMenu = container`（无 OpenScreenPacket）
      - 发送 `OpenBackpackScreenPayload` 给客户端
    - `getContainer(playerUUID): BackpackContainer` — 获取
    - `closeContainer(playerUUID)` — 关闭并调用 `removed()`
    - `removePlayer(playerUUID)` — 从 Map 移除
  - 在 `ServerStartedEvent` 初始化实例

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 服务端容器生命周期管理，需要理解 Minecraft 容器系统
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 1)
  - **Blocks**: Tasks 8, 10
  - **Blocked By**: None

  **References**:
  - `ServerPayloadHandler.handleOpenBackpack()` — 当前 B 键处理逻辑（将被改造）
  - `ServerPlayer.openMenu()` — 被替代的逻辑
  - `BackpackContainer` — 需要管理的容器类

  **Acceptance Criteria**:
  - [ ] 编译通过
  - [ ] `openContainer()` 创建 BackpackContainer 并使用 `MenuType.GENERIC_9x6`
  - [ ] `openContainer()` 设置了空 Synchronizer（不自发广播）
  - [ ] `openContainer()` 正确设置 `player.containerMenu`
  - [ ] `getContainer()` 返回正确的容器实例
  - [ ] `closeContainer()` 调用 `container.removed()`

  **QA Scenarios**:
  ```
  Scenario: 管理器编译验证
    Tool: Bash (gradlew compileJava)
    Preconditions: 代码写入完成
    Steps:
      1. 运行 gradlew compileJava
    Expected Result: 编译成功
    Evidence: .omo/evidence/task-7-compile.txt
  ```

  **Commit**: NO (group with Task 8)

---

- [x] 8. 修改 ServerPayloadHandler（B 键流程不走 openMenu）

  **What to do**:
  - 修改 `handleOpenBackpack()`：
    - 不再调用 `player.openMenu(new MenuProvider() {...})`
    - 改为调用 `BackpackContainerManager.openContainer(player, container)`
    - 保持所有现有逻辑（等级计算、容器加载/创建、保存回调设置）
  - 修改 `handleScrollChange()`：
    - 从 `player.containerMenu` 获取 BackpackContainer（现在通过 Map）
    - 保持现有滚动处理逻辑
  - 修改 `handleSort()`：
    - 从 `BackpackContainerManager` 获取容器
    - 保持现有排序逻辑
  - 新增 `handleBackpackClick(BackpackClickPayload, IPayloadContext)`：
    - 验证 `ctx.player()` 是 ServerPlayer
    - 从 BackpackContainerManager 获取 BackpackContainer
    - 调用 `bc.clicked(data.slotId(), data.button(), ClickType.values()[data.clickTypeOrdinal()], player)`
    - 调用 `bc.broadcastChanges()`（但已被覆盖为空操作）
    - 发送 `BackpackSlotSyncPayload`（全量槽位同步）
    - 发送 `BackpackCarriedUpdatePayload`（光标物品同步）
  - 新增 `handleBackpackClose(BackpackClosePayload, IPayloadContext)`：
    - 调用 `BackpackContainerManager.closeContainer(playerUUID)`

  **Must NOT do**:
  - 不改动 PaginationContainer 相关逻辑
  - 不改动命令处理逻辑

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 核心服务端流程改造，需要理解 Minecraft 容器系统
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after Task 11)
  - **Blocks**: Task 12
  - **Blocked By**: Tasks 1, 7, 11

  **References**:
  - `ServerPayloadHandler.java` — 当前全部处理逻辑
  - `ServerPlayer.openMenu()` — Minecraft 源码（理解容器打开机制）
  - `BackpackContainerManager.java` — Task 7 的新服务

  **Acceptance Criteria**:
  - [ ] 按 B 键后不调用 `player.openMenu()`（日志验证）
  - [ ] 按 B 键后客户端收到 `OpenBackpackScreenPayload`
  - [ ] 点击槽位时服务端 `bc.clicked()` 被调用
  - [ ] 关闭背包时服务端调用了 `container.removed()` 和保存回调

  **QA Scenarios**:
  ```
  Scenario: B 键打开流程不走 openMenu
    Tool: Bash (启动专用服务器 + 日志 grep)
    Preconditions: 服务端启动完成，Mod 安装客户端连接
    Steps:
      1. Mod 客户端按 B 键
      2. 检查服务端日志：无 "openMenu" 相关调用
      3. 检查客户端日志：收到 OpenBackpackScreenPayload
    Expected Result: BackpackScreen 显示
    Evidence: .omo/evidence/task-8-open-flow.txt

  Scenario: 点击交互
    Tool: Bash (服务端日志)
    Preconditions: BackpackScreen 已打开
    Steps:
      1. 点击一个非空格子
      2. 检查服务端日志：bc.clicked() 被调用
      3. 检查客户端收到 BackpackSlotSyncPayload
    Expected Result: 物品被正常拾取
    Evidence: .omo/evidence/task-8-click.txt
  ```

  **Commit**: YES
  - Message: `refactor: replace player.openMenu() with custom payload flow for B key`
  - Files: `ServerPayloadHandler.java`, `BackpackContainerManager.java`

---

- [x] 9. 修改 ClientPayloadHandler + ClientModEvents

  **What to do**:
  - 修改 `ClientPayloadHandler`：
    - 新增 `handleOpenBackpackScreen(OpenBackpackScreenPayload, IPayloadContext)`：
      - 从 Payload 读取容器元数据
      - 通过 `ContainerStateManager` 注册 containerId 为背包
      - 在客户端创建 `SimpleContainer(containerSize)` 存储物品
      - 创建 `BackpackContainer`（使用 `MenuType.GENERIC_9x6` + 本地 StorageContainer）
      - 打开 `BackpackScreen`（通过 `Minecraft.getInstance().setScreen()`）
    - 修改 `handleBackpackState()` — 调整以通过 ContainerStateManager 获取容器
    - 修改 `handleBackpackSlotSync()` — 调整写入容器逻辑
    - 新增 `handleCarriedUpdate(BackpackCarriedUpdatePayload, IPayloadContext)` — 更新光标物品
  - 修改 `ClientModEvents`：
    - 移除 `registerScreens()` 中 `event.register(ModMenuTypes.get(), BackpackScreen::new)`
    - 由于不再使用 `RegisterMenuScreensEvent`

  **Must NOT do**:
  - 不删除 `ClientModEvents` 中其他事件处理器（按键注册等保留）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 客户端 Payload 处理重构，需要理解 Minecraft 客户端屏幕系统
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 12
  - **Blocked By**: Tasks 1, 6

  **References**:
  - `ClientPayloadHandler.java` — 当前客户端处理逻辑
  - `ClientModEvents.java` — 当前注册逻辑
  - `ContainerStateManager.java` — Task 6 的客户端管理器

  **Acceptance Criteria**:
  - [ ] 收到 `OpenBackpackScreenPayload` 后打开 BackpackScreen
  - [ ] 点击后光标物品正确同步

  **QA Scenarios**:
  ```
  Scenario: Payload 触发屏幕打开
    Tool: Bash (客户端日志)
    Preconditions: 客户端 Mod 安装完成
    Steps:
      1. 按 B 键
      2. 检查 `Minecraft.getInstance().screen instanceof BackpackScreen`
    Expected Result: BackpackScreen 显示
    Evidence: .omo/evidence/task-9-screen-open.txt
  ```

  **Commit**: YES
  - Message: `refactor: update client payload handling for custom backpack flow`
  - Files: `ClientPayloadHandler.java`, `ClientModEvents.java`

---

- [x] 10. 修改 BackpackScreen（自定义 Payload 点击交互）

  **What to do**:
  - BackpackScreen 仍继承 `AbstractContainerScreen<BackpackContainer>`
  - **核心改动**: 覆盖鼠标点击方法，发送 C2S Payload 而非调 `menu.clicked()`
  - 覆盖 `mouseClicked(double, double, int)`：
    - 检测点击是否在容器槽位上
    - 计算 `slotId`、`button`、`ClickType`
    - 发送 `BackpackClickPayload(containerId, slotId, button, clickTypeOrdinal, carried.isEmpty() == false)`
    - 返回 `true`（阻止默认处理）
    - 对于玩家物品栏槽位（48-179）也走自定义 Payload
    - 对于非槽位区域（标题、背景等）调用 `super.mouseClicked()`
  - 覆盖 `mouseDragged()` — 拖拽操作也走自定义 Payload
  - 覆盖 `keyPressed()` — 数字键换栏、Q 丢弃等操作走 Payload
  - 覆盖 `onClose()` — 发送 `BackpackClosePayload`
  - 监听 `BackpackSlotSyncPayload` 和 `BackpackCarriedUpdatePayload` 更新本地状态
  - 保持现有 UI 逻辑（滚动面板、搜索框、排序按钮）完全不变

  **Must NOT do**:
  - 不改变 `BackpackScrollPanel`、搜索框、排序按钮逻辑
  - 不改动渲染逻辑（`renderBg`, `renderLabels`, `renderTooltip`）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 需要精确理解 Minecraft 点击系统 + AbstractContainerScreen 交互
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after Task 11)
  - **Blocks**: Task 12
  - **Blocked By**: Tasks 1, 6, 11

  **References**:
  - `BackpackScreen.java` — 当前屏幕代码（671 行）
  - `AbstractContainerScreen.mouseClicked()` — Minecraft 源码（理解默认点击处理）
  - `AbstractContainerMenu.clicked()` — Minecraft 源码（理解点击处理逻辑）
  - `BackpackClickPayload` — Task 3 的新 Payload

  **Acceptance Criteria**:
  - [ ] 左键点击物品→物品拾取到光标
  - [ ] 右键点击→半组/单个放置
  - [ ] Shift+点击→快速移动
  - [ ] Q 键→丢弃
  - [ ] 数字键→热键栏交换
  - [ ] 关闭界面→发送 BackpackClosePayload

  **QA Scenarios**:
  ```
  Scenario: 左键点击拾取物品
    Tool: Bash (客户端日志 + 截图)
    Preconditions: BackpackScreen 已打开，有一组钻石
    Steps:
      1. 左键点击钻石槽位
      2. 验证发送了 BackpackClickPayload
      3. 验证光标上出现钻石
    Expected Result: 物品被拾取
    Evidence: .omo/evidence/task-10-left-click.png

  Scenario: Shift+点击快速移动
    Tool: Bash (客户端日志)
    Preconditions: BackpackScreen 已打开，有物品
    Steps:
      1. Shift+左键点击物品
      2. 验证发送了 BackpackClickPayload 且 clickType=QUICK_MOVE
      3. 验证物品移入玩家背包
    Expected Result: 物品快速移动
    Evidence: .omo/evidence/task-10-shift-click.txt
  ```

  **Commit**: YES
  - Message: `refactor: route BackpackScreen clicks through custom payloads`
  - Files: `BackpackScreen.java`

---

- [x] 11. 修改 BackpackContainer（可配置 MenuType + 自定义广播）

  **What to do**:
  - 修改两个构造方法接受 `MenuType<?>` 参数，而非硬编码 `ModMenuTypes.get()`：
    - `BackpackContainer(MenuType<?> type, int id, Inventory playerInventory, Container container, int scrollOffset)`
    - `BackpackContainer(MenuType<?> type, int id, Inventory playerInventory, FriendlyByteBuf buf)` — 客户端构造
  - 保留现有 `MenuType` 默认值构造方法（兼容性）：
    - `BackpackContainer(int id, Inventory playerInventory, Container container, int scrollOffset)` 调用 `this(MenuType.GENERIC_9x6, id, ...)`
  - 覆盖 `broadcastChanges()`：
    - 阻止超槽位同步：不调用 `super.broadcastChanges()`（阻止向 ChestMenu 发送 180 槽的更新）
    - 保留自定义脏标记逻辑（`lastOccupiedDirty` 检测和 `BackpackStatePayload` 发送）
    - 修改 `sendBackpackStateToPlayer()` 如果 `player.containerMenu` 不是 BackpackContainer 则不发送
  - 保留 `setupSlots()`、`sort()`、`quickMoveStack()` 等所有现有逻辑

  **Must NOT do**:
  - 不改动 12 列布局
  - 不改动 `DynamicScrollSlot`
  - 不改动滚动偏移管理

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 需要精确理解 broadcastChanges 和容器同步机制
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (MUST run before Tasks 8, 10)
  - **Blocks**: Tasks 8, 10
  - **Blocked By**: Task 1

  **References**:
  - `BackpackContainer.java` — 当前代码（501 行）
  - `AbstractContainerMenu.broadcastChanges()` — Minecraft 源码
  - `ModMenuTypes.java` — 将被移除的自定义 MenuType

  **Acceptance Criteria**:
  - [ ] BackpackContainer 可使用 `MenuType.GENERIC_9x6` 构造
  - [ ] `broadcastChanges()` 不发送超范围槽位更新
  - [ ] 保存回调正常触发

  **QA Scenarios**:
  ```
  Scenario: 容器使用 GENERIC_9x6
    Tool: Bash (日志)
    Preconditions: 服务端启动
    Steps:
      1. 按 B 键打开背包
      2. 检查 BackpackContainer.getType() == MenuType.GENERIC_9x6
    Expected Result: 容器类型为 GENERIC_9x6
    Evidence: .omo/evidence/task-11-type.txt
  ```

  **Commit**: YES
  - Message: `refactor: make BackpackContainer accept configurable MenuType`
  - Files: `BackpackContainer.java`

---

- [x] 12. 修改 TimeReward（Payload 注册更新）

  **What to do**:
  - 从 `TimeReward` 构造函数中移除 `ModMenuTypes.MENUS.register(modEventBus)`（已在 Task 1 完成）
  - 在 `registerPayloads()` 中添加新 Payload 注册：
    - `registrar.playToClient(OpenBackpackScreenPayload.TYPE, ..., ClientPayloadHandler::handleOpenBackpackScreen)`
    - `registrar.playToServer(BackpackClickPayload.TYPE, ..., ServerPayloadHandler::handleBackpackClick)`
    - `registrar.playToServer(BackpackClosePayload.TYPE, ..., ServerPayloadHandler::handleBackpackClose)`
    - `registrar.playToClient(BackpackCarriedUpdatePayload.TYPE, ..., ClientPayloadHandler::handleCarriedUpdate)`
  - 验证 `ServerStartedEvent` 中初始化 `BackpackContainerManager`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 注册更新，模板化操作
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (final integration)
  - **Blocks**: F1-F4
  - **Blocked By**: Tasks 8, 9, 10

  **References**:
  - `TimeReward.java` — 注册 Payload 和事件监听器

  **Acceptance Criteria**:
  - [ ] 所有新 Payload 已注册
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 编译验证全部改动完成
    Tool: Bash (gradlew build)
    Preconditions: 所有 Task 1-11 完成
    Steps:
      1. 运行 gradlew build
    Expected Result: BUILD SUCCESSFUL
    Evidence: .omo/evidence/task-12-build.txt
  ```

  **Commit**: YES
  - Message: `feat: register new custom payloads for container-compatible backpack flow`
  - Files: `TimeReward.java`

---

## Final Verification Wave (MANDATORY)

- [x] F1. **Plan Compliance Audit** — `oracle`
  检查计划完整性：每个 Must Have 都有对应实现。检查 Must NOT Have 没有违规。
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  运行 `gradlew build`。检查：无 `@ts-ignore`/`as any` 等价物、无空 catch、无日志泄露、无死代码。
  Output: `Build [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high`
  从纯净状态启动。完整测试 B 键流程：打开、拾取、放置、Shift+点击、滚动、排序、关闭。测试 `/tyj-reward get` 不受影响。
  Output: `B-Key Flow [PASS/FAIL] | Tyj-Reward Flow [PASS/FAIL] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  逐任务检查：实际改动 vs 计划。确认 PaginationContainer 未接触。确认无 Mixin 引入。
  Output: `Tasks [N/N compliant] | Scope Creep [CLEAN/N issues] | VERDICT`

---

## Commit Strategy

- **Task 1**: `fix: remove custom MenuType registration from synced registry` - ModMenuTypes.java, TimeReward.java
- **Task 8**: `refactor: replace player.openMenu() with custom payload flow for B key` - ServerPayloadHandler.java, BackpackContainerManager.java
- **Task 9**: `refactor: update client payload handling for custom backpack flow` - ClientPayloadHandler.java, ClientModEvents.java
- **Task 10**: `refactor: route BackpackScreen clicks through custom payloads` - BackpackScreen.java
- **Task 11**: `refactor: make BackpackContainer accept configurable MenuType` - BackpackContainer.java
- **Task 12**: `feat: register new custom payloads for container-compatible backpack flow` - TimeReward.java

---

## Success Criteria

### Verification Commands
```bash
gradlew build  # Expected: BUILD SUCCESSFUL
```

### Final Checklist
- [ ] 未安装 Mod 客户端可连接服务器
- [ ] 未安装 Mod 客户端可执行 `/tyj-reward get`
- [ ] 安装 Mod 客户端可按 B 键打开 12 列滚动背包
- [ ] 背包内取放物品正常
- [ ] 背包内滚动正常
- [ ] 背包内排序正常
- [ ] 关闭后物品自动保存

