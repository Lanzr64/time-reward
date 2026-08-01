# B键背包UI工作流程与网络关系

> 基于 `time-reward` 模组（NeoForge）分析
> 最后更新: 2026-07-21

---

## 1. 概述

B键背包系统是模组中"评论奖励"（评论得奖励）的核心交互入口。玩家按下 **B 键** 打开一个 **12列可滚动的奖励背包**，查看/整理通过评论获得的物品。系统涉及 **客户端GUI**、**网络通信**、**服务端容器管理**、**持久化存储** 四个层次。

### 1.1 涉及的包与文件

| 包 | 说明 |
|---|---|
| `client/gui/` | 背包屏幕界面（`BackpackScreen`） |
| `client/` | 键位绑定与客户端事件（`KeybindHandler`, `ClientModEvents`） |
| `inventory/` | 容器/槽位定义（`BackpackContainer`, `DynamicScrollSlot`） |
| `network/` | 网络载荷（C2S/S2C）与处理器 |
| `save/` | 持久化存储（`PlayerRewardManager`, `LZSavedData`） |
| `init/` | MenuType 注册（`ModMenuTypes`） |
| `commands/` | `/tyj-reward` 命令（**不属背包 UI，但共享数据**） |

### 1.2 文件清单

| # | 文件 | 角色 |
|---|---|---|
| 1 | `client/KeybindHandler.java` | 定义B键 `KeyMapping` |
| 2 | `client/ClientModEvents.java` | 注册键位 + 连接 `BackpackScreen` + 消费B键输入 |
| 3 | `client/gui/BackpackScreen.java` | 背包屏幕（含内类 `BackpackScrollPanel`） |
| 4 | `inventory/BackpackContainer.java` | 容器（服务端真实存储 + 客户端虚拟存储） |
| 5 | `inventory/DynamicScrollSlot.java` | 动态映射显示位置→容器索引的自定义槽位 |
| 6 | `network/OpenBackpackPayload.java` | C2S 空载荷：请求打开背包 |
| 7 | `network/ScrollChangePayload.java` | C2S：通知服务端滚动偏移变化 |
| 8 | `network/SortPayload.java` | C2S：通知服务端排序请求 |
| 9 | `network/BackpackStatePayload.java` | S2C：服务端推送权威 `lastOccupiedRow` |
| 10 | `network/ServerPayloadHandler.java` | 处理全部3个C2S载荷 |
| 11 | `network/ClientPayloadHandler.java` | 处理1个S2C载荷 |
| 12 | `init/ModMenuTypes.java` | 注册 `MenuType<BackpackContainer>` |
| 13 | `TimeReward.java` | `@Mod` 主类，注册网络载荷 + MenuType |
| 14 | `save/PlayerRewardManager.java` | 玩家背包数据的文件持久化 |
| 15 | `save/LZSavedData.java` | 管理员奖励池（世界级 `SavedData`） |

---

## 2. 完整工作流程

### 2.1 打开背包（B键 → GUI渲染）

```
[玩家] 按下 B 键
   │
   ▼
┌─── 客户端 ───────────────────────────────────────────────────────────┐
│                                                                      │
│  KeybindHandler.getOpenBackpackKey()                                 │
│    → KeyMapping(key="key.time_reward.open_backpack", key=GLFW_KEY_B) │
│                                                                      │
│  ClientGameEvents.onClientTick(ClientTickEvent.Post)                 │
│    → while (key.consumeClick())                                      │
│        → PacketDistributor.sendToServer(new OpenBackpackPayload())   │
│                                                                      │
│  [C2S 空载荷: time_reward:open_backpack ----> 服务端]                │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─── 服务端 ───────────────────────────────────────────────────────────┐
│                                                                      │
│  ServerPayloadHandler.handleOpenBackpack()                           │
│    ctx.enqueueWork(() -> {  // 主线程安全                            │
│                                                                      │
│  1. 获取玩家评论等级                                                 │
│     PlayerCommentTools.getPlayerComment(playerName, info)            │
│     → currentLevel = commentInfo.level                               │
│                                                                      │
│  2. 获取持久化存储等级                                               │
│     PlayerRewardManager.getStoredLevel(playerUUID)                   │
│     → effectiveLevel = max(currentLevel, storedLevel)                │
│                                                                      │
│  3. 计算容器大小                                                     │
│     expectedSlots = min(effectiveLevel × 15, 900)                    │
│                                                                      │
│  4. 加载或创建玩家容器                                               │
│     PlayerRewardManager.loadOrCreate(uuid, expectedSlots,            │
│                                      rewardBox, registryAccess)      │
│     → 返回 SimpleContainer                                           │
│                                                                      │
│  5. 从底部扫描 lastOccupiedRow（空容器=-1）                          │
│     for i = containerSize-1 to 0                                     │
│       if !container.getItem(i).isEmpty()                             │
│         lastOccupiedRow = i / COLS                                   │
│                                                                      │
│  6. 打开菜单 + 写入额外数据                                          │
│     player.openMenu(MenuProvider{                                    │
│       getDisplayName() → "奖励背包"                                  │
│       createMenu() → BackpackContainer(id, inv, container, 0)       │
│                       .setSaveCallback(() -> save(...))              │
│     }, buf → {                                                       │
│       buf.writeInt(containerSize)    // 额外数据1                     │
│       buf.writeInt(0)                // 额外数据2: scrollOffset      │
│       buf.writeInt(lastOccupiedRow)  // 额外数据3                     │
│     })                                                               │
│                                                                      │
│  7. 服务端 BackpackContainer 构造                                   │
│     setupSlots(): 144 × DynamicScrollSlot + 27 物品栏 + 9 快捷栏    │
│     lastOccupiedRow = recomputeLastOccupiedRow()                     │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
                              │
    [开放数据包 ----> 客户端]
                              │
                              ▼
┌─── 客户端 ───────────────────────────────────────────────────────────┐
│                                                                      │
│  8. BackpackContainer(id, inv, buf) 客户端构造                       │
│     containerSize = buf.readInt()  → SimpleContainer(n)             │
│     scrollOffset  = buf.readInt()  → 0                              │
│     lastOccupiedRow = buf.readInt()                                  │
│     setupSlots()                                                     │
│                                                                      │
│  9. BackpackScreen(menu, inv, title)                                 │
│     → 根据屏幕高度计算 visibleRows (4 ~ 12)                          │
│     → imageWidth=236, imageHeight 自适应                              │
│     → init(): initScrollPanel(), initSearchBox(),                    │
│               initSortButton(), updateSlotsPosition()                │
│                                                                      │
│ 10. 渲染GUI：                                                        │
│     ┌──────────────────────────────────────┐                        │
│     │  "奖励背包"        [🔍][N]           │                        │
│     ├──────────────────────────────────────┤                        │
│     │  □ □ □ □ □ □ □ □ □ □ □ □  ← 12列   │                        │
│     │  □ □ □ □ □ □ □ □ □ □ □ □            │                        │
│     │  □ □ □ □ □ □ □ □ □ □ □ □            │                        │
│     │  □ □ □ □ □ □ □ □ □ □ □ □            │                        │
│     │  ═══════════════════════ 滚动条      │                        │
│     ├──────────────────────────────────────┤                        │
│     │  物品栏 (3行 × 9列)                  │                        │
│     │  快捷栏 (1行 × 9列)                  │                        │
│     └──────────────────────────────────────┘                        │
│                                                                      │
│  — 背景纹理: storage_background_12_wider.png (分段blit)              │
│  — 格子纹理: slots_background.png (tiled)                            │
│  — 越界行: 0x80000000 暗色遮罩                                       │
│  — 搜索框在最上层 z=200                                              │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 滚动交互

```
[鼠标滚轮 / 拖动滚动条]
   │
   ▼
BackpackScrollPanel.mouseScrolled(x, y, scrollX, scrollY)
   │  super.mouseScrolled() → ScrollPanel 调整 scrollDistance
   │  (每次滚轮刻度 = 18px = SLOT_SIZE)
   │
   ├→ updateSlotsPosition() → repositionSlots()
   │   遍历 144 个显示槽位:
   │   ├ 搜索过滤不通过 → slot.x = -2000 (左移隐藏)
   │   ├ 超出可见区域 → slot.y = -2000 (上移隐藏)
   │   └ 可见 → slot.x = 7 + col×18, slot.y = 17 + row×18
   │   重定位物品栏/快捷栏在网格下方
   │
   ├→ menu.setClientScrollOffset(rowOffset)   // 乐观即时更新
   │   → scrollOffset = clamp(0, max, rowOffset)
   │
   └→ PacketDistributor.sendToServer(
         new ScrollChangePayload(containerId, rowOffset))
                          │
              [C2S: scroll_change ----> 服务端]
                          │
                          ▼
ServerPayloadHandler.handleScrollChange(data, ctx)
   │  backpack.setScrollOffset(data.newOffset())
   │    → clampScrollOffset()
   │    → lastOccupiedDirty = true
   │    → broadcastChanges()
   │
   ├→ super.broadcastChanges()  // 同步所有槽位物品
   │
   └→ if (lastOccupiedDirty)
         row = recomputeLastOccupiedRow()
         if (row != lastSentLastOccupiedRow)
           sendBackpackStateToPlayer(row)
                          │
              [S2C: backpack_state ----> 客户端]
                          │
                          ▼
ClientPayloadHandler.handleBackpackState(data, ctx)
   │  验证: mc.player.containerMenu == BackpackContainer
   │       && bc.containerId == data.containerId()
   │
   ├→ bc.setLastOccupiedRow(data.lastOccupiedRow())
   │
   └→ if (mc.screen instanceof BackpackScreen bs)
        bs.onLastOccupiedRowChanged()
          → initScrollPanel()    // 重建/销毁滚动面板
          → scrollPanel.reclamp()// 重新钳制滚动距离
          → updateSlotsPosition()
```

> **关键设计：乐观更新 + 脏标记门控**
> - 客户端立即应用滚动偏移（无网络等待），保证流畅体验
> - 服务端只有在 `lastOccupiedDirty=true` 时才扫描 O(n) 容器
> - S2C 只在 `lastOccupiedRow` **实际变化**时才发送，避免无用广播

### 2.3 排序交互

```
[点击排序按钮 "N" / "C" / "M"]
   │  枚举: NAME(0) → COUNT(1) → MOD(2) → 循环
   │
   ▼
BackpackScreen.cycleSort()
   → currentSort = SortType.values()[(ordinal+1) % 3]
   → updateSortButtonLabel()
   → onSortChanged()
       ├ scrollPanel.resetScrollDistance()  // 滚动回顶部
       ├ updateSlotsPosition()
       └ PacketDistributor.sendToServer(
             new SortPayload(containerId, currentSort.ordinal()))
                          │
              [C2S: sort ----> 服务端]
                          │
                          ▼
ServerPayloadHandler.handleSort(data, ctx)
   → bc.sort(SortType.values()[sortOrdinal])
       │
       1. lastOccupiedDirty = true
       2. 收集容器全部物品，分离非空
       3. 构建比较器:
          NAME: 按物品名 (locale-insensitive)
          COUNT: 按数量降序 → 按物品名
          MOD:   按注册命名空间 → 按物品名
       4. 排序后写回容器，空位填 ItemStack.EMPTY
       5. setScrollOffset(0) → broadcastChanges()
          (内部 recomputeLastOccupiedRow + 发送 BackpackStatePayload)
       6. 最终 recomputeLastOccupiedRow() + 增量发送
       │
   → broadcastChanges()  // lastOccupiedDirty 已清，无操作
                          │
              [S2C: backpack_state ----> 客户端]
                          │
                          ▼
     ClientPayloadHandler.handleBackpackState()
       → bs.onLastOccupiedRowChanged()
```

### 2.4 搜索交互（纯客户端）

```
[Ctrl+F 聚焦搜索框 / 输入文字]
   │
   ▼
searchBox.setResponder(this::onSearchTextChanged)
   → updateStackFilter(text)
       │
       按空格分词，构建 AND 谓词:
       ├ "hello"   → 物品名包含 "hello" (忽略大小写)
       ├ "@modid"  → 注册命名空间包含 "modid"
       └ "#kw"     → 任意 tooltip 行包含 "kw"
       │ 空文本 → stackFilter = stack → true
       │
   → scrollPanel.resetScrollDistance()
   → updateSlotsPosition()
       │ 过滤不通过 → slot.x = -2000 (隐藏)
```

> **搜索完全在客户端执行，不发送任何网络包。**

### 2.5 Shift+点击物品移动

```
[玩家 Shift+点击槽位]
   │
   ▼
BackpackContainer.quickMoveStack(player, slotIndex)
   │
   ├ slotIndex < PLAYER_INV_START (0-143):
   │   显示网格 → 玩家物品栏
   │   moveItemStackTo(stack, PLAYER_INV_START, TOTAL_SLOTS, true)
   │
   └ slotIndex >= PLAYER_INV_START (144-179):
       玩家物品栏 → 显示网格
       moveItemStackTo(stack, 0, TOTAL_DISPLAY_SLOTS, false)
   │
   → lastOccupiedDirty = true
   │
   ▼
[下一个服务端 tick]
   → broadcastChanges()
       ├ super.broadcastChanges()   // 同步槽位
       └ if dirty → recompute → 增量发送 BackpackStatePayload
```

### 2.6 关闭容器与持久化

```
[玩家关闭背包 / 切换屏幕]
   │
   ▼
BackpackContainer.removed(player)
   → saveCallback.run()
       │
       ▼
PlayerRewardManager.save(uuid, container, lookup, saveLevel)
   │
   序列化格式 (NBT):
   {
     Version: 2,
     Level: <effectiveLevel>,
     ContainerSize: <slotCount>,
     Items: [
       { Slot: 0, Item: <tag> },
       { Slot: 1 },                    // 空物品无 Item 键
       ...
     ]
   }
   │
   写入流程:
   1. synchronized (FILE_LOCK)
   2. NbtIo.writeCompressed(tag, tempFile)
   3. Files.move(tempFile, file, REPLACE_EXISTING)  // 原子替换
   │
   存储位置:
   <服务器根目录>/lzFiles/player-rewards/<uuid>.dat
```

---

## 3. 网络协议详解

### 3.1 注册机制

**位置**: `TimeReward.registerPayloads(RegisterPayloadHandlersEvent)`

```java
registrar = event.registrar("1");  // 协议版本 "1"

// 3 个 C2S
registrar.playToServer(OpenBackpackPayload.TYPE,  ..., ServerPayloadHandler::handleOpenBackpack);
registrar.playToServer(ScrollChangePayload.TYPE,  ..., ServerPayloadHandler::handleScrollChange);
registrar.playToServer(SortPayload.TYPE,          ..., ServerPayloadHandler::handleSort);

// 1 个 S2C
registrar.playToClient(BackpackStatePayload.TYPE, ..., ClientPayloadHandler::handleBackpackState);
```

- 使用 NeoForge 现代 `PayloadRegistrar` API（非 `SimpleChannel`）
- 所有载荷在游戏阶段（`play`）传输，无登录阶段载荷

### 3.2 载荷规格

| 载荷 | 方向 | 频道ID | 字段 | 编码 | 大小 |
|---|---|---|---|---|---|
| `OpenBackpackPayload` | C2S | `open_backpack` | 无 | `StreamCodec.unit()` | 0 字节 |
| `ScrollChangePayload` | C2S | `scroll_change` | `containerId, newOffset` | `VAR_INT × 2` | ~2-6 字节 |
| `SortPayload` | C2S | `sort` | `containerId, sortOrdinal` | `VAR_INT × 2` | ~2-6 字节 |
| `BackpackStatePayload` | S2C | `backpack_state` | `containerId, lastOccupiedRow` | `VAR_INT × 2` | ~2-6 字节 |

### 3.3 数据流图

```
┌───────────┐          ┌──────────────┐
│  客户端    │          │   服务端      │
├───────────┤          ├──────────────┤
│           │          │              │
│ [B键按下] │          │              │
│  Open───► │─ C2S ──► │ handleOpen   │
│           │          │  → 加载容器   │
│           │          │  → 打开菜单   │
│           │◄─ S2C ── │  [开放数据包] │
│           │ (容器数据)│              │
│           │          │              │
│ [滚轮滚动]│          │              │
│ Scroll──►│─ C2S ──► │ handleScroll │
│           │          │  → 更新偏移   │
│           │          │  → broadcast  │
│           │◄─ S2C ── │  [State if   │
│           │  (可能)   │   changed]   │
│           │          │              │
│ [点击排序]│          │              │
│ Sort───► │─ C2S ──► │ handleSort   │
│           │          │  → 排序物品   │
│           │          │  → reset+send│
│           │◄─ S2C ── │  [State]     │
│           │          │              │
│ [Shift点击]│          │              │
│           │(槽位同步)│ quickMove    │
│           │          │  → dirty标记 │
│           │◄─ S2C ── │  [State if   │
│           │  (可能)   │   changed]   │
│           │          │              │
│ [关闭背包]│          │              │
│           │          │ save() → 磁盘│
└───────────┘          └──────────────┘
```

---

## 4. 容器与槽位架构

### 4.1 BackpackContainer 布局

```
索引范围      类型                      说明
────────────────────────────────────────────────
  0 - 143    DynamicScrollSlot         显示网格 (12列 × 12行可见)
144 - 170    Slot (普通)               玩家物品栏 (27)
171 - 179    Slot (普通)               快捷栏 (9)
────────────────────────────────────────────────
总计: 180 个槽位
```

### 4.2 DynamicScrollSlot 映射

```
getActualIndex() =
  (displayIndex / slotsPerRow + scrollOffset) × slotsPerRow
  + displayIndex % slotsPerRow

例如: displayIndex=5, scrollOffset=3, slotsPerRow=12
  → row = 0 + 3 = 3
  → col = 5
  → actualIndex = 3 × 12 + 5 = 41
```

- 无物品副本，始终从 `storageContainer` 实时读取
- `scrollOffset` 通过 `IntSupplier` 获取（指向 `BackpackContainer.scrollOffset`）

### 4.3 脏标记门控同步

```
字段:
  lastOccupiedRow      — 最后一个有物品的行 (-1 表示空)
  lastOccupiedDirty    — 标记上次变更后是否需重新扫描
  lastSentLastOccupiedRow — 上次已发送的值 (用于增量检测)

设置脏标记的路径:
  setScrollOffset()    → lastOccupiedDirty = true → broadcastChanges()
  sort()               → lastOccupiedDirty = true → broadcastChanges()
  quickMoveStack()     → lastOccupiedDirty = true (下次 tick broadcast)

broadcastChanges() 覆写:
  super.broadcastChanges();
  if (lastOccupiedDirty) {
    row = recomputeLastOccupiedRow();     // O(n) 从底部扫描
    if (row != lastSentLastOccupiedRow) { // 增量检测
      sendBackpackStateToPlayer(row);     // 发 S2C
    }
    lastOccupiedDirty = false;
  }
```

---

## 5. 持久化架构

### 5.1 双系统存储

| 方面 | 全局奖励池 (LZSavedData) | 玩家背包 (PlayerRewardManager) |
|---|---|---|
| 作用 | 管理员投放奖励物品的池子 | 每个玩家的个人容器 |
| 存储位置 | `world/data/timeReward-SavedData.dat` | `lzFiles/player-rewards/<uuid>.dat` |
| 持久化引擎 | Minecraft `SavedData` | 自定义 NBT 文件 I/O |
| 容器大小 | 固定 900 格 | 可变: `min(level × 15, 900)` |
| 序列化 | `SimpleContainer.createTag()` 原生 | 自定义: 按索引序列化全部槽位 |
| 版本控制 | 无（Minecraft SavedData 处理） | 显式 `Version: 2` |

### 5.2 Level-Up 扩容机制

```
容器大小 = min(max(currentLevel, storedLevel) × 15, 900)

当 savedContainerSize < expectedSize:
  1. 创建更大的 SimpleContainer(expectedSize)
  2. 复制旧物品到新容器
  3. 新扩容的槽位从管理员奖励池填充
  4. 立即可保存
```

### 5.3 保存回调注册

**位置**: `ServerPayloadHandler.handleOpenBackpack()`

```java
bc.setSaveCallback(() -> {
    try {
        PlayerRewardManager.save(playerUUID, container, lookup, saveLevel);
    } catch (Exception e) {
        TimeReward.LOGGER.error("Save callback error", e);
    }
});
```

**触发时机**: `BackpackContainer.removed()` — 容器关闭时。

---

## 6. 与 `/tyj-reward get` 命令的关系

| 触发器 | 容器类型 | 界面 | 标题 |
|---|---|---|---|
| **B键** | `BackpackContainer` | 12列可滚动网格 | "奖励背包" |
| **`/tyj-reward get`** | `PaginationContainer` | 分页领取界面 (6×9) | "评论奖励" |

两者**共享**：
- `PlayerCommentTools` — 从 MCMOD 网站 API 获取评论等级
- `PlayerRewardManager` — 玩家容器数据持久化
- `LZSavedData` — 管理员奖励池

两者**独立**：
- 容器实现（`BackpackContainer` vs `PaginationContainer`）
- 屏幕实现（12列滚动 vs 6×9分页）
- 保存回调（独立的 `saveCallback` 注册点）

---

## 7. 关键设计决策

1. **乐观更新** — 客户端在服务端确认前立即应用滚动偏移，避免网络往返卡顿
2. **脏标记门控** — O(n) 容器扫描不会每 tick 执行，仅在排序/滚动/物品移动后触发
3. **增量S2C** — `BackpackStatePayload` 仅在实际值变化时发送，非每脏tick
4. **客户端搜索** — 搜索过滤完全在客户端进行，零网络开销
5. **原子写入** — 持久化使用 `.tmp` + `Files.move()` 原子替换，防止文件损坏
6. **等级取最大值** — 容器大小取 `max(当前等级, 已存储等级)`，不会因等级下降而缩小

---

## 8. 发现的问题/待改进

1. **缺少语言文件** — `assets/time_reward/lang/` 下无任何 `.json` 文件，键位在控制设置中显示原始翻译键名
2. **命令/键位重叠** — B键背包与 `/tyj-reward get` 两种 UI 操作同一份玩家数据，可能存在玩家混淆
3. **客户端Tick while 循环** — `consumeClick()` 尽管每 tick 一次，但 `while` 确保了极端情况下的正确消费
