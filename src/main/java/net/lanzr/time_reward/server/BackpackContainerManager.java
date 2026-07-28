package net.lanzr.time_reward.server;

import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.OpenBackpackScreenPayload;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理通过B键（绕过{@link ServerPlayer#openMenu}）打开的{@link BackpackContainer}实例的服务端管理器。
 *
 * <p>该类替代了{@code player.openMenu()}的标准流程。我们不发送{@code OpenScreenPacket}，
 * 而是手动设置{@code player.containerMenu}并通过{@link OpenBackpackScreenPayload}通知客户端。
 * 使用无操作的{@link ContainerSynchronizer}防止自动的数据包广播，
 * 因为背包容器的同步是通过自定义的有效负载（滚动、排序等）处理的。</p>
 *
 * <p>此类是线程安全的，使用{@link ConcurrentHashMap}存储按玩家UUID索引的打开容器。</p>
 */
public final class BackpackContainerManager {

    private static final BackpackContainerManager INSTANCE = new BackpackContainerManager();

    /** 当前打开的容器，按玩家UUID索引。 */
    private final Map<UUID, BackpackContainer> openContainers = new ConcurrentHashMap<>();

    /** 容器计数器的反射字段（{@link ServerPlayer#containerCounter}）。 */
    private static final Field CONTAINER_COUNTER_FIELD;

    static {
        try {
            CONTAINER_COUNTER_FIELD = ServerPlayer.class.getDeclaredField("containerCounter");
            CONTAINER_COUNTER_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("无法访问 ServerPlayer.containerCounter 字段", e);
        }
    }

    /**
     * 无操作的容器同步器，用于阻止{@link AbstractContainerMenu#broadcastChanges()}发送原版同步数据包。
     *
     * <p>当{@code player.openMenu()}被绕过时，我们手动管理所有同步（滚动、排序、槽位更新等），
     * 因此不需要原版的{@link ContainerSynchronizer}发送自动更新。</p>
     */
    private static final ContainerSynchronizer NO_OP_SYNC = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carriedItem, int[] data) {
            // 无操作——防止自动的初始容器数据包
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack) {
            // 无操作——通过自定义有效负载处理槽位同步
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu container, ItemStack carriedItem) {
            // 无操作——通过自定义有效负载处理当前物品同步
        }

        @Override
        public void sendDataChange(AbstractContainerMenu container, int id, int value) {
            // 无操作——通过自定义有效负载处理数据变更同步
        }
    };

    private BackpackContainerManager() {}

    /**
     * @return 此管理器的单例实例
     */
    public static BackpackContainerManager getInstance() {
        return INSTANCE;
    }

    /**
     * 为给定玩家打开一个{@link BackpackContainer}，绕过{@link ServerPlayer#openMenu}。
     *
     * <p>这个方法：</p>
     * <ol>
     *   <li>如果玩家已有打开的容器，则关闭它</li>
     *   <li>为容器ID分配一个新的容器计数器值</li>
     *   <li>创建一个{@link BackpackContainer}，其{@code scrollOffset}为{@code 0}</li>
     *   <li>在容器上设置一个无操作的同步器（防止自动广播）</li>
     *   <li>设置{@code player.containerMenu = container}（无{@code OpenScreenPacket}）</li>
     *   <li>发送{@link OpenBackpackScreenPayload}到客户端</li>
     *   <li>将容器存储在玩家的UUID键映射中</li>
     *   <li>将提供的{@code saveCallback}附加到容器上</li>
     * </ol>
     *
     * @param player          要为其打开容器的服务端玩家
     * @param storageContainer 支持此容器的{@link SimpleContainer}
     * @param scrollOffset    初始滚动偏移量（行数），通常为{@code 0}
     * @param lastOccupiedRow 包含物品的最后一行（用于客户端UI布局），如果容器为空则为{@code -1}
     * @param saveCallback    当容器关闭时（通过{@link BackpackContainer#removed}）运行的回调
     * @return 已创建并打开的{@link BackpackContainer}
     */
    public BackpackContainer openContainer(
            ServerPlayer player,
            SimpleContainer storageContainer,
            int scrollOffset,
            int lastOccupiedRow,
            Runnable saveCallback) {

        UUID playerUUID = player.getUUID();

        // 如果玩家已有打开的背包容器，则先关闭
        if (openContainers.containsKey(playerUUID)) {
            closeContainer(playerUUID);
        }

        // 分配一个新的容器ID
        int containerId = incrementContainerCounter(player);

        // 创建BackpackContainer
        BackpackContainer container = new BackpackContainer(
                containerId,
                player.getInventory(),
                storageContainer,
                scrollOffset
        );

        // 设置真实的 ContainerSynchronizer 以启用原版槽位同步
        container.setSynchronizer(new ContainerSynchronizer() {
            @Override public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {}
            @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot, stack));
            }
            @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), -1, carried));
            }
            @Override public void sendDataChange(AbstractContainerMenu menu, int id, int value) {}
        });

        // 附加保存回调
        container.setSaveCallback(saveCallback);

        // 手动设置player.containerMenu，绕过player.openMenu()
        player.containerMenu = container;

        // 将容器存储在映射中
        openContainers.put(playerUUID, container);

        // 发送自定义打开负载到客户端
        PacketDistributor.sendToPlayer(player, new OpenBackpackScreenPayload(
                containerId,
                storageContainer.getContainerSize(),
                scrollOffset,
                lastOccupiedRow,
                Component.literal("奖励背包")
        ));

        // 发送初始槽位同步，填充客户端的空存储容器
        java.util.List<net.minecraft.world.item.ItemStack> initialItems = new java.util.ArrayList<>(BackpackContainer.TOTAL_DISPLAY_SLOTS);
        int sendCount = Math.min(BackpackContainer.TOTAL_DISPLAY_SLOTS, storageContainer.getContainerSize());
        for (int i = 0; i < sendCount; i++) {
            initialItems.add(storageContainer.getItem(i));
        }
        // 如果容器尺寸小于显示槽位数，补充空物品
        for (int i = sendCount; i < BackpackContainer.TOTAL_DISPLAY_SLOTS; i++) {
            initialItems.add(net.minecraft.world.item.ItemStack.EMPTY);
        }
        PacketDistributor.sendToPlayer(player, new net.lanzr.time_reward.network.BackpackSlotSyncPayload(
                containerId, scrollOffset, initialItems));

        return container;
    }

    /**
     * 重载方法，使用{@code scrollOffset = 0}和通过重新扫描容器确定的{@code lastOccupiedRow}。
     *
     * @param player          要为其打开容器的服务端玩家
     * @param storageContainer 支持此容器的{@link SimpleContainer}
     * @param saveCallback    当容器关闭时运行的回调
     * @return 已创建并打开的{@link BackpackContainer}
     */
    public BackpackContainer openContainer(
            ServerPlayer player,
            SimpleContainer storageContainer,
            Runnable saveCallback) {

        int lastOccupiedRow = computeLastOccupiedRow(storageContainer);
        return openContainer(player, storageContainer, 0, lastOccupiedRow, saveCallback);
    }

    /**
     * 获取指定玩家UUID当前打开的{@link BackpackContainer}。
     *
     * @param playerUUID 玩家的UUID
     * @return 如果该玩家当前有打开的背包容器则返回它，否则返回{@code null}
     */
    public BackpackContainer getContainer(UUID playerUUID) {
        return openContainers.get(playerUUID);
    }

    /**
     * 获取指定玩家当前打开的{@link BackpackContainer}。
     *
     * @param player 服务端玩家
     * @return 如果该玩家当前有打开的背包容器则返回它，否则返回{@code null}
     */
    public BackpackContainer getContainer(ServerPlayer player) {
        return openContainers.get(player.getUUID());
    }

    /**
     * 关闭指定玩家UUID的{@link BackpackContainer}。
     *
     * <p>调用{@link BackpackContainer#removed(Player)}来触发保存回调，
     * 然后从打开的容器映射中移除条目。</p>
     *
     * @param playerUUID 玩家的UUID
     */
    public void closeContainer(UUID playerUUID) {
        BackpackContainer container = openContainers.remove(playerUUID);
        if (container != null) {
            // removed(Player)触发保存回调
            container.removed(null);
        }
    }

    /**
     * 当服务端玩家断开连接时调用，以清理其打开的容器。
     *
     * <p>与{@link #closeContainer(UUID)}不同，此方法在玩家断开连接时调用，
     * 此时不应再发送任何数据包。</p>
     *
     * @param playerUUID 正在断开连接的玩家的UUID
     */
    public void removePlayer(UUID playerUUID) {
        BackpackContainer container = openContainers.remove(playerUUID);
        if (container != null) {
            try {
                container.removed(null);
            } catch (Exception e) {
                // 断开连接时记录日志但不传播异常
            }
        }
    }

    /**
     * 检查给定玩家当前是否有打开的背包容器。
     *
     * @param playerUUID 玩家的UUID
     * @return 如果该玩家当前有打开的背包容器则返回{@code true}
     */
    public boolean hasOpenContainer(UUID playerUUID) {
        return openContainers.containsKey(playerUUID);
    }

    // ========== 内部辅助方法 ==========

    /**
     * 通过反射递增{@link ServerPlayer}的容器计数器并返回新值。
     * 这模拟了{@link ServerPlayer#nextContainerCounter()}的效果，
     * 而后者是私有方法，无法直接从外部调用。
     */
    private static int incrementContainerCounter(ServerPlayer player) {
        try {
            int id = CONTAINER_COUNTER_FIELD.getInt(player);
            id = id % 100 + 1;
            CONTAINER_COUNTER_FIELD.setInt(player, id);
            return id;
        } catch (IllegalAccessException e) {
            // 不应发生——我们在静态初始化中调用了setAccess(true)
            throw new RuntimeException("无法递增容器计数器", e);
        }
    }

    /**
     * 从最后一个索引向下扫描{@link SimpleContainer}以找到包含物品的最高行索引。
     *
     * @param container 要扫描的{@link SimpleContainer}
     * @return 包含物品的最后行索引，如果容器为空则返回{@code -1}
     */
    private static int computeLastOccupiedRow(SimpleContainer container) {
        for (int i = container.getContainerSize() - 1; i >= 0; i--) {
            if (!container.getItem(i).isEmpty()) {
                return i / BackpackContainer.COLS;
            }
        }
        return -1;
    }
}
