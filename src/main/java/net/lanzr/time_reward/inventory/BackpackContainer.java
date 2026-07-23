package net.lanzr.time_reward.inventory;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.network.BackpackStatePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 背包UI的服务端容器。
 * <p>
 * 显示由{@link Container}支持的{@link DynamicScrollSlot}的可滚动网格（12列 × 4可见行），
 * 以及玩家的物品栏和快捷栏。
 * 支持滚动、排序（名称/数量/模组）和关闭时自动保存。
 * </p>
 *
 * <h3>构造方式</h3>
 * <ul>
 *   <li><b>服务端</b>: {@link #BackpackContainer(int, Inventory, Container, int)} —
 *       接收真实的存储容器和初始滚动偏移量。</li>
 *   <li><b>客户端</b>: {@link #BackpackContainer(int, Inventory, FriendlyByteBuf)} —
 *       由{@link net.neoforged.neoforge.common.extensions.IMenuTypeExtension}使用
 *       （在{@link ModMenuTypes}中注册）。从网络缓冲区读取滚动偏移量和容器大小，
 *       创建一个虚拟容器。</li>
 * </ul>
 *
 * <h3>槽位布局</h3>
 * <pre>
 *   行0-3:  DynamicScrollSlots（48个槽位，索引0-47）
 *   行4-6:  玩家物品栏        （27个槽位，索引48-74）
 *   行7:    快捷栏             （ 9个槽位，索引75-83）
 * </pre>
 *
 * <p><b>滚动偏移同步策略</b>：偏移量是一个普通的{@code int}字段。
 * 客户端界面管理其本地偏移量（初始来自缓冲区，随后在滚动输入时更新）。
 * 当服务端处理滚动（通过C2S包）时，它调用{@link #setScrollOffset(int)}更新字段
 * 并触发{@link #broadcastChanges()}，使所有显示槽位的物品在新偏移量下重新发送
 * —— 客户端在物品到达时已拥有正确的偏移量，避免了数据与槽位同步的顺序竞争风险。</p>
 */
public class BackpackContainer extends AbstractContainerMenu {

    // ========== 布局常量 ==========

    /** 可滚动网格的列数。 */
    public static final int COLS = 12;
    /** 可滚动网格的可见行数。 */
    public static final int MAX_VISIBLE_ROWS = 12;
    /** 总显示槽位数（12 × MAX_VISIBLE_ROWS）。 */
    public static final int TOTAL_DISPLAY_SLOTS = COLS * MAX_VISIBLE_ROWS;
    /** 玩家物品栏起始槽位索引。 */
    public static final int PLAYER_INV_START = TOTAL_DISPLAY_SLOTS;        // 144
    /** 快捷栏起始槽位索引。 */
    public static final int HOTBAR_START = PLAYER_INV_START + 27;          // 171
    /** 该菜单的总槽位数。 */
    public static final int TOTAL_SLOTS = HOTBAR_START + 9;                // 180

    // ========== 字段 ==========

    private final Container storageContainer;
    private final Player player;
    private Runnable saveCallback = () -> { };

    /** 当前滚动偏移量（行数）。在两侧分别管理。 */
    private int scrollOffset;

    /** 
     * 客户端的实际可见行数（由屏幕高度决定）。 
     * 用于计算最大滚动偏移量，替代硬编码的 MAX_VISIBLE_ROWS。
     */
    private int clientVisibleRows = MAX_VISIBLE_ROWS;

    /** 包含物品的最后一行索引（从0开始），由服务端在打开时设置。 */
    private int lastOccupiedRow;

    /**
     * 当存储容器的已占行边界可能发生变化时（物品添加/移除/排序）设置的脏标记。
     * {@link #broadcastChanges()}的覆写方法会检查此标记，以决定在向客户端发送更新之前
     * 是否需要对容器执行O(n)重新扫描。此脏标记门控确保重新计算不会每个tick都触发
     * &mdash; 仅在已知的变更路径（排序、滚动设置、Shift+点击移动）后触发。
     */
    private boolean lastOccupiedDirty = true;

    /**
     * 上次通过{@link BackpackStatePayload}发送给客户的{@link #lastOccupiedRow}的缓存值。
     * 用于{@link #broadcastChanges()}中的增量检测，以确保S2C数据包仅在该值实际变化时发送，
     * 而不会在每个脏tick都发送。
     */
    private int lastSentLastOccupiedRow;

    /**
     * 每次{@link #broadcastChanges()}调用{@link #recomputeLastOccupiedRow()}时递增的实例计数器。
     * 记录用于QA验证脏标记门控节奏 &mdash; 不应每个服务端tick都增加，
     * 仅在触发变更的广播时增加。
     */
    private int recomputeCallCount = 0;

    // ========== 构造方法 ==========

    /**
     * 服务端构造方法。
     *
     * @param id             容器ID
     * @param playerInventory 打开玩家的物品栏
     * @param container      用于读写物品的真实存储容器
     * @param scrollOffset   初始滚动偏移量（行数）
     */
    public BackpackContainer(int id, Inventory playerInventory, Container container, int scrollOffset) {
        super(MenuType.GENERIC_9x6, id);
        this.player = playerInventory.player;
        this.storageContainer = container;
        this.scrollOffset = scrollOffset;
        setupSlots(playerInventory);
        // 通过新扫描初始化服务端lastOccupiedRow字段，使得
        // 第一次broadcastChanges()不会发送虚假增量
        // （ServerPayloadHandler写入客户端缓冲区的打开时负载已经携带了相同的值）。
        this.lastOccupiedRow = recomputeLastOccupiedRow();
        this.lastSentLastOccupiedRow = this.lastOccupiedRow;
        this.lastOccupiedDirty = false;
    }

    /**
     * 客户端构造方法，由{@code IMenuTypeExtension.create()}从网络缓冲区调用。
     *
     * @param id             容器ID
     * @param playerInventory 客户端玩家的物品栏
     * @param buf            包含{@code containerSize}和{@code scrollOffset}的网络缓冲区
     */
    public BackpackContainer(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(MenuType.GENERIC_9x6, id);
        this.player = playerInventory.player;
        int containerSize = buf.readInt();
        this.storageContainer = new SimpleContainer(containerSize);
        this.scrollOffset = buf.readInt();
        this.lastOccupiedRow = buf.readInt();
        setupSlots(playerInventory);
    }

    // ========== 槽位布局 ==========

    /**
     * 创建并添加所有槽位：
     * <ol>
     *   <li>{@code TOTAL_DISPLAY_SLOTS} 个{@link DynamicScrollSlot}（可见网格）</li>
     *   <li>3行玩家物品栏（27个槽位）</li>
     *   <li>1行快捷栏（9个槽位）</li>
     * </ol>
     */
    private void setupSlots(Inventory playerInventory) {
        // ---- 可滚动的显示网格 ----
        for (int row = 0; row < MAX_VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int displayIndex = row * COLS + col;
                addSlot(new DynamicScrollSlot(
                        storageContainer, displayIndex, COLS,
                        this::getScrollOffset,
                        8 + col * 18, 18 + row * 18));
            }
        }

        // ---- 玩家物品栏（3行 × 9列） ----
        int playerInvX = 8 + 30; // 8 + PLAYER_INV_X_OFFSET 用于WIDER_12_SLOT布局
        int playerInvY = 18 + MAX_VISIBLE_ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + col + row * 9,
                        playerInvX + col * 18, playerInvY + row * 18));
            }
        }

        // ---- 快捷栏（1行 × 9列） ----
        int hotbarY = playerInvY + 3 * 18 + 4;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    playerInvX + col * 18, hotbarY));
        }
    }

    // ========== 滚动偏移管理 ==========

    /**
     * @return 当前滚动偏移量（行数）
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getLastOccupiedRow() {
        return lastOccupiedRow;
    }

    /**
     * @return 底层存储容器的总槽位数。
     * 委托给{@link Container#getContainerSize()}。
     */
    public int getContainerSize() {
        return storageContainer.getContainerSize();
    }

    /**
     * 直接覆写{@code lastOccupiedRow}字段，<b>不</b>产生任何广播副作用。
     * 用于客户端重新钳位的路径（如{@code ClientPayloadHandler}），
     * 调用方在更新此字段后单独驱动重新钳位。
     *
     * @param row 新的最后占用行值（使用{@code -1}表示"容器完全为空"）
     */
    public void setLastOccupiedRow(int row) {
        this.lastOccupiedRow = row;
    }

    /**
     * 服务端辅助方法，从最后一个索引向下扫描{@code storageContainer}到{@code 0}，
     * 找到最高的已占用行，就地更新{@link #lastOccupiedRow}字段，并返回重新计算的值。
     * <p>
     * 空容器的语义：{@code -1}（不是{@code 0}）。
     *
     * @return 重新计算的最后占用行索引，如果容器完全为空则返回{@code -1}
     */
    public int recomputeLastOccupiedRow() {
        int containerSize = storageContainer.getContainerSize();
        for (int i = containerSize - 1; i >= 0; i--) {
            if (!storageContainer.getItem(i).isEmpty()) {
                this.lastOccupiedRow = i / COLS;
                return this.lastOccupiedRow;
            }
        }
        this.lastOccupiedRow = -1;
        return -1;
    }

    /**
     * 在客户端侧直接设置滚动偏移量（由屏幕在滚动时调用）。
     * 这很快 &mdash; 仅是一个字段更新 &mdash; 因此客户端可以在服务端确认滚动之前立即渲染。
     */
    public void setClientScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, Math.min(offset, getClientMaxScrollOffset()));
    }

    /**
     * 设置客户端的实际可见行数。
     * 由 {@link net.lanzr.time_reward.client.gui.BackpackScreen} 在初始化或窗口resize时调用，
     * 使最大滚动偏移量的计算与实际显示行数一致。
     *
     * @param rows 实际可见行数（至少为1，不超过 MAX_VISIBLE_ROWS）
     */
    public void setClientVisibleRows(int rows) {
        this.clientVisibleRows = Math.max(1, Math.min(rows, MAX_VISIBLE_ROWS));
    }

    /**
     * 根据lastOccupiedRow（实际内容范围），而非容器容量，返回客户端允许的最大滚动偏移量。
     * 这与{@code BackpackScrollPanel.getContentHeight()}的语义一致。
     */
    private int getClientMaxScrollOffset() {
        return Math.max(0, lastOccupiedRow + 1 - clientVisibleRows);
    }

    /**
     * 在服务端设置滚动偏移量，钳位到有效范围，并立即触发{@link #broadcastChanges()}，
     * 以便显示槽位的物品在新偏移量下重新发送。
     */
    public void setScrollOffset(int offset) {
        this.scrollOffset = clampScrollOffset(offset);
        // 滚动本身不会改变物品，但随后的broadcastChanges可能需要
        // 在刷新槽位的同时发送当前的lastOccupiedRow。
        // 廉价地标记脏；重新扫描仅在未过时门控时触发
        // （扫描后脏标记被清除，因此后续无变更的tick不会重新扫描）。
        this.lastOccupiedDirty = true;
        broadcastChanges();
    }

    /**
     * 按{@code delta}行调整滚动偏移量（正值 = 向下滚动）
     * 并广播由此产生的物品更改。
     */
    public void onScroll(int delta) {
        setScrollOffset(this.scrollOffset + delta);
    }

    /**
     * @return 最大滚动偏移量（行数），如果所有行都适合屏幕则返回0
     * <p>基于{@link #lastOccupiedRow}（实际内容行数）而非容器总容量计算，
     * 与客户端{@link #getClientMaxScrollOffset()}保持一致，避免两端
     * 滚动上限不一致导致槽位物品写入错位。</p>
     */
    public int getMaxScrollOffset() {
        return Math.max(0, lastOccupiedRow + 1 - clientVisibleRows);
    }

    private int clampScrollOffset(int offset) {
        return Math.max(0, Math.min(offset, getMaxScrollOffset()));
    }

    // ========== 排序 ==========

    /** 背包内容的排序条件。 */
    public enum SortType {
        NAME,
        COUNT,
        MOD
    }

    /**
     * 按给定的条件对存储容器中的物品进行排序，
     * 然后将滚动位置重置到顶部并广播更改。
     */
    public void sort(SortType type) {
        // 初始标记脏，使setScrollOffset(0)内部的broadcastChanges
        // 能够获取到刚排序完的布局。
        this.lastOccupiedDirty = true;

        // 收集容器中的所有物品
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < storageContainer.getContainerSize(); i++) {
            items.add(storageContainer.getItem(i));
        }

        // 分离非空物品进行排序
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                nonEmpty.add(stack);
            }
        }

        // 构建比较器
        Comparator<ItemStack> comparator = buildSortComparator(type);
        nonEmpty.sort(comparator);

        // 将排序后的物品写回容器
        int idx = 0;
        for (ItemStack stack : nonEmpty) {
            storageContainer.setItem(idx++, stack.copy());
        }
        // 用空物品堆填满剩余槽位
        for (; idx < storageContainer.getContainerSize(); idx++) {
            storageContainer.setItem(idx, ItemStack.EMPTY);
        }

        // 重置滚动到顶部（内部触发broadcastChanges —— 该调用将
        // 重新计算一次lastOccupiedRow，因为我们上面标记了脏标记，
        // 如果检测到增量则向玩家发送BackpackStatePayload）。
        setScrollOffset(0);

        // 排序结束时的最终权威重新计算 + 增量发送。这与
        // setScrollOffset(0)内部的broadcast重复，但它是
        // 规范的"排序结果"锚点 —— 针对lastSentLastOccupiedRow的增量检测
        // 保证整个排序操作只发送一个BackpackStatePayload。
        int newRow = recomputeLastOccupiedRow();
        TimeReward.LOGGER.info(
                "[BackpackContainer] recompute result post-sort lastOccupiedRow={}",
                newRow);
        if (newRow != lastSentLastOccupiedRow) {
            lastSentLastOccupiedRow = newRow;
            sendBackpackStateToPlayer(newRow);
        }
        this.lastOccupiedDirty = false;
    }

    private static Comparator<ItemStack> buildSortComparator(SortType type) {
        switch (type) {
            case NAME:
                return Comparator.comparing(
                        stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            case COUNT:
                return Comparator.<ItemStack>comparingInt(ItemStack::getCount)
                        .reversed()
                        .thenComparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            case MOD:
                return Comparator.<ItemStack, String>comparing(
                                stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                        .thenComparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            default:
                return (a, b) -> 0;
        }
    }

    // ==========  Shift+点击（快速移动） ==========

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();

        if (slotIndex < PLAYER_INV_START) {
            // 显示网格 → 玩家物品栏
            if (!moveItemStackTo(stackInSlot, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家物品栏 → 显示网格
            if (!moveItemStackTo(stackInSlot, 0, TOTAL_DISPLAY_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        // 无论哪个方向都会改变底层storageContainer的已占行边界
        // （源在显示网格时移除物品；目标在显示网格时添加物品）。
        // 标记脏，使得下一次broadcastChanges()（在此点击后的原版容器同步tick中调用）
        // 重新扫描并在发生变化时推送BackpackStatePayload。
        this.lastOccupiedDirty = true;

        return result;
    }

    // ========== 广播/同步 ==========

    /**
     * 原版容器同步钩子。先委托给{@code super}以执行所有标准槽位/状态同步，
     * 然后检查{@link #lastOccupiedDirty}标记，以决定是否需要对存储容器执行
     * O(n)重新扫描。当重新计算的lastOccupiedRow与上次发送给查看器的值
     * ({@link #lastSentLastOccupiedRow})不同时，发送{@link BackpackStatePayload}。
     *
     * <p>脏标记门控确保此扫描不会每个tick都运行 &mdash; 仅在已知的变更路径
     * ({@link #sort(SortType)}, {@link #setScrollOffset(int)},
     * {@link #quickMoveStack(Player, int)})之后的tick上运行。</p>
     */
    @Override
    public void broadcastChanges() {
        // Don't call super — vanilla broadcast would send 180-slot updates
        // to a client that only has 90 slots (ChestMenu), causing crashes.
        if (this.lastOccupiedDirty) {
            int row = recomputeLastOccupiedRow();
            recomputeCallCount++;
            TimeReward.LOGGER.info(
                    "[BackpackContainer] recompute-call-count n={} row={}",
                    recomputeCallCount, row);
            if (row != lastSentLastOccupiedRow) {
                lastSentLastOccupiedRow = row;
                sendBackpackStateToPlayer(row);
            }
            this.lastOccupiedDirty = false;
        }
    }

    /**
     * 向此容器的查看者发送携带新lastOccupiedRow的{@link BackpackStatePayload}。
     * 当查看者不是服务端玩家时（例如该菜单的客户端虚拟实例），不执行操作。
     * 根据规范，仅通知单个所属查看者 &mdash; 不广播给其他玩家。
     */
    private void sendBackpackStateToPlayer(int row) {
        if (this.player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new BackpackStatePayload(containerId, row));
        }
    }

    // ========== 生命周期 ==========

    @Override
    public void removed(Player player) {
        super.removed(player);
        saveCallback.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ========== 回调和访问器 ==========

    /**
     * 注册一个在容器关闭时调用的回调。
     * 通常用于通过{@link net.lanzr.time_reward.save.PlayerRewardManager}将容器持久化到磁盘。
     */
    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }

    /** 返回底层的存储容器。 */
    public Container getStorageContainer() {
        return storageContainer;
    }
}
