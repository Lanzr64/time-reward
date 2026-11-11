package net.lanzr.time_reward.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.api.CommentInfo;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.BackpackClickPayload;
import net.lanzr.time_reward.network.BackpackClosePayload;
import net.lanzr.time_reward.network.ModNetwork;
import net.lanzr.time_reward.network.OpenBackpackPayload;
import net.lanzr.time_reward.network.ScrollChangePayload;
import net.lanzr.time_reward.network.SortPayload;
import net.lanzr.time_reward.save.LZSavedData;
import net.lanzr.time_reward.save.PlayerRewardManager;
import net.lanzr.time_reward.server.BackpackContainerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraftforge.network.NetworkEvent;

public final class ServerPayloadHandler {
    private ServerPayloadHandler() {
    }

    public static void handleOpenBackpack(OpenBackpackPayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ((NetworkEvent.Context)ctx.get()).getSender();
            if (player == null) {
                return;
            }
            if (!LZSavedData.SET_DONE) {
                return;
            }
            CommentInfo commentInfo = new CommentInfo();
            int haveReward = PlayerCommentTools.getPlayerComment(player.getName().getString(), commentInfo);
            int currentLevel = haveReward >= 0 ? Math.max(commentInfo.level, 0) : 0;
            UUID playerUUID = player.getUUID();
            int storedLevel = PlayerRewardManager.getStoredLevel(playerUUID);
            int effectiveLevel = Math.max(currentLevel, storedLevel);
            int expectedSlots = Math.min(effectiveLevel * 10, 360);
            SimpleContainer container = PlayerRewardManager.loadOrCreate(playerUUID, expectedSlots, LZSavedData.getRewardBox());
            int saveLevel = effectiveLevel;
            int lastOccupiedRow = -1;
            for (int i = container.getContainerSize() - 1; i >= 0; --i) {
                if (container.getItem(i).isEmpty()) continue;
                lastOccupiedRow = i / 12;
                break;
            }
            if (lastOccupiedRow == -1) {
                TimeReward.LOGGER.info("[BackpackContainer] handleOpenBackpack empty container lastOccupiedRow=-1");
            }
            int finalLastOccupiedRow = lastOccupiedRow;
            BackpackContainerManager.getInstance().openContainer(player, container, 0, finalLastOccupiedRow, () -> {
                try {
                    PlayerRewardManager.save(playerUUID, container, saveLevel);
                }
                catch (Exception e) {
                    TimeReward.LOGGER.error("Save callback error", (Throwable)e);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleScrollChange(ScrollChangePayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ((NetworkEvent.Context)ctx.get()).getSender();
            if (player == null) {
                return;
            }
            AbstractContainerMenu patt4180$temp = player.containerMenu;
            if (patt4180$temp instanceof BackpackContainer) {
                BackpackContainer backpack = (BackpackContainer)patt4180$temp;
                if (backpack.containerId == data.containerId()) {
                    backpack.setClientVisibleRows(data.visibleRows());
                    backpack.setScrollOffset(data.newOffset());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSort(SortPayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ((NetworkEvent.Context)ctx.get()).getSender();
            if (player == null) {
                return;
            }
            AbstractContainerMenu patt5775$temp = player.containerMenu;
            if (patt5775$temp instanceof BackpackContainer) {
                BackpackContainer bc = (BackpackContainer)patt5775$temp;
                if (bc.containerId == data.containerId() && data.sortOrdinal() >= 0 && data.sortOrdinal() < BackpackContainer.SortType.values().length) {
                    bc.sort(BackpackContainer.SortType.values()[data.sortOrdinal()]);
                    BackpackContainerManager.getInstance().sendStorageSnapshot(player, bc);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleBackpackClick(BackpackClickPayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            BackpackContainer bc;
            ServerPlayer player;
            block5: {
                block4: {
                    player = ((NetworkEvent.Context)ctx.get()).getSender();
                    if (player == null) {
                        return;
                    }
                    AbstractContainerMenu patt6680$temp = player.containerMenu;
                    if (!(patt6680$temp instanceof BackpackContainer)) break block4;
                    bc = (BackpackContainer)patt6680$temp;
                    if (bc.containerId == data.containerId() && data.clickTypeOrdinal() >= 0 && data.clickTypeOrdinal() < ClickType.values().length) break block5;
                }
                return;
            }
            ClickType clickType = ClickType.values()[data.clickTypeOrdinal()];
            bc.clicked(data.slotId(), data.button(), clickType, (Player)player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleBackpackClose(BackpackClosePayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ((NetworkEvent.Context)ctx.get()).getSender();
            if (player != null && player.containerMenu.containerId == data.containerId()) {
                BackpackContainerManager.getInstance().closeContainer(player.getUUID());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
