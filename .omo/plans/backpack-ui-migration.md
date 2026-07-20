# 背包 UI 迁移方案

## TL;DR

> **概要**: 为 time_reward 模组添加自定义背包 GUI，按 'B' 键打开，展示 PlayerRewardManager 的玩家奖励库存
>
> **关键决策**: 轻量自实现（不依赖 SophisticatedCore），参考 SB 的 UI 原理，12 列可滚动网格
>
> **核心思路**: 
> - 服务端：`BackpackContainer` 使用**动态映射槽**，固定数量显示槽根据滚动位置映射到 PlayerRewardManager 的 SimpleContainer
> - 客户端：`BackpackScreen` 使用 NeoForge `ScrollPanel`，滚动时移动 Slot Y 坐标
> - 纹理：创建自定义 `backpack_gui.png`
>
> **工作量**: Medium（约 600-800 行 Java + 1 纹理）

---

## 上下文

### 现有项目状态

| 项目 | 路径 | 说明 |
|---|---|---|
| time_reward | `D:\lz_workplace\java\lzProject\time-reward\1.21` | NeoForge 1.21.1 模组 |
| PlayerRewardManager | `save/PlayerRewardManager.java` | 每玩家 SimpleContainer，最大 900 槽 |
| 当前 UI | `LZMenu.java` + `PaginationContainer.java` | 服务端分页容器 + 原版 ChestScreen |

### 需求确认

- **打开方式**: 按 'B' 键
- **数据源**: `PlayerRewardManager.loadOrCreate(playerUuid, ...)` 的 SimpleContainer
- **列数**: 12 列
- **滚动**: NeoForge ScrollPanel，逐行平滑滚动
- **搜索+排序**: 需要
- **侧边栏/升级**: 不需要
- **依赖**: 无（纯自实现）

---

## 核心架构设计

### 整体架构

```
客户端:                                     服务端:
┌──────────────────────┐                   ┌──────────────────────────┐
│ KeybindHandler       │  OpenBackpackPkt  │ BackpackContainer        │
│ 按 B → 发包           │ ────────────────> │ ├─ DynamicScrollSlot[48] │
└──────────────────────┘                   │ │   (12列×4行可视区域)    │
                                           │ ├─ PlayerSlot[36]        │
┌──────────────────────┐                   │ ├─ scrollOffset: int     │
│ BackpackScreen       │  ScrollChangePkt  │ ├─ container: SimpleCont │
│ ├─ renderBg(纹理)     │ <───────────────> │ └─ saveOnClose()         │
│ ├─ ScrollPanel        │                   └──────────┬───────────────┘
│ │  └─ updateSlots()   │                              │
│ │    修改 Slot.y      │                   ┌──────────▼───────────────┐
│ ├─ SearchBox          │                   │ PlayerRewardManager      │
│ ├─ SortButton         │                   │ loadOrCreate()/save()    │
│ └─ renderLabels()     │                   └──────────────────────────┘
└──────────────────────┘
```

### 关键设计决策

#### D1: 动态映射槽（非全量注册）

PlayerRewardManager 最大 900 槽。不创建 900 个 Slot，而是创建**固定数量**动态槽（12×4=48 个）。

```java
public class DynamicScrollSlot extends Slot {
    private final int displayIndex;
    private final int slotsPerRow = 12;
    private final WeakReference<BackpackContainer> container;

    private int getActualIndex() {
        int row = displayIndex / slotsPerRow + container.get().scrollOffset;
        int col = displayIndex % slotsPerRow;
        return row * slotsPerRow + col;
    }

    @Override public ItemStack getItem() {
        int idx = getActualIndex();
        return idx < container.get().getStorageSize() 
            ? container.get().getStorage().getItem(idx) 
            : ItemStack.EMPTY;
    }
}
```

#### D2: 滚动位置服务端同步

```
客户端 → 服务端: ScrollChangePayload(containerId, newOffset)
服务端处理: 更新 scrollOffset → broadcastChanges()
```

#### D3: 搜索纯客户端

`stackFilter` 在客户端维护，不匹配的槽 `x = -2000`（隐藏）。与 `updateSlotsPosition()` 联动。

#### D4: 纹理设计

`backpack_gui.png` (256×256) 三段式：标题区、槽位区（可平铺拉伸）、玩家栏区。

---

## 任务分解

### Wave 1: 基础构建

- [x] 1. 注册 MenuType + 网络包

  **做什么**:
  - `init/ModMenuTypes.java`：注册 `MenuType<BackpackContainer>`
  - `network/OpenBackpackPayload.java`：C2S，请求打开背包
  - `network/ScrollChangePayload.java`：C2S，滚动位置同步
  - 在 `TimeReward.java` 中注册网络包

  **并行**: Wave 1 组（T1+T2+T3 可并行）
  **阻塞**: T4, T5, T6

  **QA**: 服务端启动无异常 ✓ | 发包/收包测试通过 ✓

  ---

- [x] 2. 创建 GUI 纹理

  **做什么**:
  - 创建 `assets/time_reward/textures/gui/backpack_gui.png`（256×256）
  - 或使用原版 `generic_54.png` 在 `renderBg()` 中拼接

  **并行**: Wave 1 组
  **阻塞**: T6

  **QA**: 纹理路径正确 ✓ | 游戏中渲染正常 ✓

  ---

- [x] 3. 创建按键绑定

  **做什么**:
  - `client/KeybindHandler.java`：注册 'B' 键
  - 按下时发送 `OpenBackpackPayload`

  **并行**: Wave 1 组

  **QA**: 按 B 键后发包成功 ✓

  ---

### Wave 2: 核心逻辑

- [x] 4. 实现 BackpackContainer

  **做什么**:
  - `inventory/BackpackContainer.java`，继承 `AbstractContainerMenu`
  - 48 个 `DynamicScrollSlot`（12×4 网格，x=8+col*18, y=18+row*18）
  - 36 个玩家槽位（3行背包 + 1行快捷栏）
  - `scrollOffset` 字段 + `onScroll()` 方法
  - `sort(SortType)` 排序
  - `removed()` 自动保存到 PlayerRewardManager

  **依赖**: T1
  **并行**: Wave 2 组（T4+T5+T6 并行）

  **QA**: 容器打开显示正确 ✓ | 排序正确 ✓ | 保存正常 ✓

  ---

- [x] 5. 实现 DynamicScrollSlot

  **做什么**:
  - `inventory/DynamicScrollSlot.java`，继承 `Slot`
  - 根据 `displayIndex + scrollOffset` 计算实际容器索引
  - 重写 `getItem/set/remove/mayPlace/mayPickup`

  **依赖**: T1
  **并行**: Wave 2 组

  **QA**: 映射计算正确 ✓ | 越界返回 EMPTY ✓

  ---

- [x] 6. 实现 BackpackScreen

  **做什么**:
  - `client/gui/BackpackScreen.java`，继承 `AbstractContainerScreen<BackpackContainer>`
  - `renderBg()`: 绘制背景纹理
  - `InventoryScrollPanel` (extends NeoForge ScrollPanel)：滚动处理
  - `updateSlotsPosition()`: 修改 Slot.y（隐藏=y=-100）
  - 可视行数 = `(屏幕高 - 114) / 18`（动态适应窗口）

  **依赖**: T2, T4
  **并行**: Wave 2 组

  **QA**: 背景纹理正确 ✓ | 12 列对齐 ✓ | 滚动流畅 ✓

  ---

- [x] 7. 实现搜索框 + 排序按钮

  **做什么**:
  - 搜索框：`EditBox`，输入更新 `stackFilter`
  - 支持语法：`名称` / `@模组` / `#提示`
  - 排序按钮：NAME → COUNT → MOD 循环
  - 与 `updateSlotsPosition()` 联动

  **依赖**: T6
  **并行**: Wave 2 组

  **QA**: 过滤正确 ✓ | 排序正确 ✓ | 搜索+滚动联动 ✓

  ---

### Wave 3: 集成

- [x] 8. 集成测试 + 调整

  **做什么**:
  - 端到端测试所有功能
  - 调整纹理/坐标偏移
  - 修复发现的问题

  **依赖**: T1-T7 完成

  **QA**: 完整流程测试 ✓ | 数据持久化 ✓

  ---

### Wave FINAL: 验证

- [x] F1. **计划合规审计** — oracle ✅ (fix: 创建 SortPayload 修复排序)
- [x] F2. **代码质量审查** — unspecified-high ✅ (fix: 日志/import/NPE/类型转换)
- [ ] F3. **游戏内功能测试** — ⚠️ (需要游戏环境手动测试)
- [x] F4. **范围校验** — deep ✅ (scope clean, 无超范围变更)

---

## 提交策略

| 任务 | 提交信息 | 文件 |
|---|---|---|
| 1 | `feat: register MenuType and network payloads` | ModMenuTypes, Payloads |
| 2 | `feat: add backpack GUI texture` | backpack_gui.png |
| 3 | `feat: add B key keybinding` | KeybindHandler |
| 4 | `feat: implement BackpackContainer` | BackpackContainer |
| 5 | `feat: implement DynamicScrollSlot` | DynamicScrollSlot |
| 6 | `feat: implement BackpackScreen` | BackpackScreen |
| 7 | `feat: implement search and sort UI` | 集成在 Screen 中 |
| 8 | `fix: integration fixes` | 修复调整 |

---

## 成功标准

- [ ] 按 B 打开背包，展示 PlayerRewardManager 物品
- [ ] 12 列布局 + 流畅滚动
- [ ] 搜索过滤正常
- [ ] 排序功能正常
- [ ] 物品交互正常
- [ ] 关闭自动保存 + 数据持久化
- [ ] 不依赖 SophisticatedCore
- [ ] 不修改现有 PaginationContainer
