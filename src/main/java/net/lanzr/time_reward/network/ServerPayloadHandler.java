package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.api.CommentInfo;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.BackpackCarriedUpdatePayload;
import net.lanzr.time_reward.network.BackpackClickPayload;
import net.lanzr.time_reward.network.BackpackClosePayload;
import net.lanzr.time_reward.save.LZSavedData;
import net.lanzr.time_reward.save.PlayerRewardManager;
import net.lanzr.time_reward.server.BackpackContainerManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * 在服务端线程上处理C2S（服务端绑定）数据包。
 *
 * <p>所有处理方法都通过{@link IPayloadContext#enqueueWork}调用，
 * 以确保它们在主服务端线程上运行。</p>
 */
public final class ServerPayloadHandler {
    private ServerPayloadHandler() {}

    /**
     * 处理客户端打开背包奖励UI的请求。
     *
     * <p>根据玩家当前的奖励等级加载或创建奖励容器，
     * 然后打开一个{@link BackpackContainer}菜单，
     * 并带有一个在容器关闭时持久化更改的保存回调。</p>
     */
    public static void handleOpenBackpack(OpenBackpackPayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // 检查奖励是否已启用（需管理员执行 /tyj-reward admin enable）
            if (!LZSavedData.SET_DONE) return;

            // 从玩家评论获取当前奖励等级（与/tyj-reward get相同）
            CommentInfo commentInfo = new CommentInfo();
            int haveReward = PlayerCommentTools.getPlayerComment(player.getName().getString(), commentInfo);
            int currentLevel = haveReward >= 0 ? Math.max(commentInfo.level, 0) : 0;

            UUID playerUUID = player.getUUID();
            int storedLevel = PlayerRewardManager.getStoredLevel(playerUUID);

            // 使用当前等级和存储等级中的较高者作为容器大小
            int effectiveLevel = Math.max(currentLevel, storedLevel);
            int expectedSlots = Math.min(effectiveLevel * LZSavedData.SLOTS_PER_LEVEL, LZSavedData.CONTAINER_SIZE);

            HolderLookup.Provider lookup = player.serverLevel().registryAccess();
            SimpleContainer container = PlayerRewardManager.loadOrCreate(
                    playerUUID, expectedSlots, LZSavedData.getRewardBox(), lookup
            );

            final int saveLevel = effectiveLevel;

            // 空容器的哨兵值为-1（不是0），因此客户端的
            // contentRows = max(0, lastOccupiedRow + 1)会折叠为0并且不会创建
            // 滚动面板。服务端的BackpackContainer也通过其构造方法中的
            // recomputeLastOccupiedRow()以相同方式初始化该字段。
            int lastOccupiedRow = -1;
            for (int i = container.getContainerSize() - 1; i >= 0; i--) {
                if (!container.getItem(i).isEmpty()) {
                    lastOccupiedRow = i / BackpackContainer.COLS;
                    break;
                }
            }
            if (lastOccupiedRow == -1) {
                TimeReward.LOGGER.info(
                        "[BackpackContainer] handleOpenBackpack empty container lastOccupiedRow=-1");
            }

            final int finalLastOccupiedRow = lastOccupiedRow;

            BackpackContainerManager.getInstance().openContainer(
                player, container, 0, finalLastOccupiedRow, () -> {
                    try {
                        PlayerRewardManager.save(playerUUID, container, lookup, saveLevel);
                    } catch (Exception e) {
                        TimeReward.LOGGER.error("Save callback error", e);
                    }
                }
            );
        });
    }

    /**
     * 处理客户端对打开的背包容器的滚动偏移量更新。
     *
     * <p>验证玩家当前打开的容器是{@link BackpackContainer}，
     * 然后更新其滚动偏移量并将更改同步回客户端。</p>
     */
    public static void handleScrollChange(ScrollChangePayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (player.containerMenu instanceof BackpackContainer backpack) {
                backpack.setClientVisibleRows(data.visibleRows());
                backpack.setScrollOffset(data.newOffset());
                backpack.broadcastChanges();
                // Send custom slot sync - standard broadcastChanges() doesn't handle DynamicScrollSlot
                sendBackpackSlotSync(player, backpack);
            }
        });
    }

    /**
     * 滚动后将当前 scrollOffset 对应的 144 个显示槽位物品发给客户端。
     * 绕过 broadcastChanges() 的增量同步（其对 DynamicScrollSlot 不生效）。
     */
    private static void sendBackpackSlotSync(ServerPlayer player, BackpackContainer backpack) {
        int scrollOffset = backpack.getScrollOffset();
        java.util.List<ItemStack> items = new java.util.ArrayList<>(BackpackContainer.TOTAL_DISPLAY_SLOTS);
        for (int i = 0; i < BackpackContainer.TOTAL_DISPLAY_SLOTS; i++) {
            items.add(backpack.getSlot(i).getItem());
        }
        PacketDistributor.sendToPlayer(player,
                new BackpackSlotSyncPayload(backpack.containerId, scrollOffset, items));
    }

    /**
     * 处理客户端对打开的背包容器的排序请求。
     *
     * <p>验证玩家当前打开的容器是{@link BackpackContainer}，
     * 然后应用请求的排序顺序并将更改同步回客户端。</p>
     */
    public static void handleSort(SortPayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (player.containerMenu instanceof BackpackContainer bc) {
                bc.sort(BackpackContainer.SortType.values()[data.sortOrdinal()]);
                bc.broadcastChanges();
            }
        });
    }

    /**
     * 处理客户端对背包容器的点击操作。
     *
     * <p>验证玩家当前打开的容器匹配其容器ID，
     * 然后将点击转发到{@link BackpackContainer#clicked(int, int, ClickType, Player)}。
     * 点击处理后发送槽位同步和当前物品更新。</p>
     */
    public static void handleBackpackClick(BackpackClickPayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof BackpackContainer bc)
                    || bc.containerId != data.containerId()) return;

            ClickType clickType = ClickType.values()[data.clickTypeOrdinal()];
            bc.clicked(data.slotId(), data.button(), clickType, player);
            // 不发送自定义同步——broadcastChanges() 内部的 super.broadcastChanges()
            // 已通过真实的 ContainerSynchronizer 发送原版槽位更新
        });
    }

    public static void handleBackpackClose(BackpackClosePayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            BackpackContainerManager.getInstance().closeContainer(player.getUUID());
        });
    }
}
