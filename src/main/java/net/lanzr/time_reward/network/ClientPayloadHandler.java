package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.client.ContainerStateManager;
import net.lanzr.time_reward.client.gui.BackpackScreen;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.BackpackCarriedUpdatePayload;
import net.lanzr.time_reward.network.OpenBackpackScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 在客户端线程上处理S2C（客户端绑定）数据包。
 *
 * <p>所有处理方法都通过{@link IPayloadContext#enqueueWork}调用，
 * 以确保它们在主客户端线程上运行。</p>
 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    /**
     * 处理服务端发来的打开背包屏幕包，在客户端创建BackpackContainer和BackpackScreen。
     */
    public static void handleOpenBackpackScreen(OpenBackpackScreenPayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 从负载数据创建存储容器
            SimpleContainer storage = new SimpleContainer(data.containerSize());
            ContainerStateManager.getInstance().registerBackpack(data.containerId(), storage);

            // 使用服务端构造器在客户端创建BackpackContainer，
            // 然后覆写服务端recomputeLastOccupiedRow()的结果
            BackpackContainer bc = new BackpackContainer(
                    data.containerId(), mc.player.getInventory(), storage, data.scrollOffset());
            bc.setLastOccupiedRow(data.lastOccupiedRow());

            // 打开背包屏幕
            mc.setScreen(new BackpackScreen(bc, mc.player.getInventory(), data.title()));
        });
    }

    /**
     * 处理来自服务端的权威{@link BackpackStatePayload}。
     *
     * <p>当打开的菜单与负载的containerId匹配时，
     * 更新绑定的{@link BackpackContainer}的lastOccupiedRow字段，
     * 然后通知活动的{@link BackpackScreen}，使其可以重新评估滚动面板（重建或销毁）
     * 并根据新的内容高度重新钳制滚动距离。</p>
     */
    public static void handleBackpackState(BackpackStatePayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null
                    && mc.player.containerMenu instanceof BackpackContainer bc
                    && bc.containerId == data.containerId()) {
                int oldRow = bc.getLastOccupiedRow();
                bc.setLastOccupiedRow(data.lastOccupiedRow());
                TimeReward.LOGGER.info(
                        "[BackpackState-Diag] received lastOccupiedRow: old={} new={} (containerId={})",
                        oldRow, data.lastOccupiedRow(), data.containerId());
                if (mc.screen instanceof BackpackScreen bs) {
                    bs.onLastOccupiedRowChanged();
                }
            }
        });
    }

    /**
     * 处理服务端发来的背包槽位同步包，直接将物品写入本地 storageContainer。
     */
    public static void handleBackpackSlotSync(BackpackSlotSyncPayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null
                    && mc.player.containerMenu instanceof BackpackContainer bc
                    && bc.containerId == data.containerId()) {

                Container storage = bc.getStorageContainer();
                int offset = data.scrollOffset();
                java.util.List<ItemStack> items = data.items();

                for (int i = 0; i < items.size() && i < BackpackContainer.TOTAL_DISPLAY_SLOTS; i++) {
                    int row = i / BackpackContainer.COLS;
                    int col = i % BackpackContainer.COLS;
                    int actualIndex = (row + offset) * BackpackContainer.COLS + col;
                    if (actualIndex < storage.getContainerSize()) {
                        storage.setItem(actualIndex, items.get(i));
                    }
                }
            }
        });
    }

    /**
     * 处理服务端发来的背包光标物品更新包，直接设置客户端BackpackContainer的carried物品。
     */
    public static void handleCarriedUpdate(BackpackCarriedUpdatePayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof BackpackContainer bc
                    && bc.containerId == data.containerId()) {
                bc.setCarried(data.carried());
            }
        });
    }
}