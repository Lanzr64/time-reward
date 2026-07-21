package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.client.gui.BackpackScreen;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.minecraft.client.Minecraft;
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
}