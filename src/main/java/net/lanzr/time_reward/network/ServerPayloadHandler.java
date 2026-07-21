package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.api.CommentInfo;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.save.LZSavedData;
import net.lanzr.time_reward.save.PlayerRewardManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Handles C2S (server-bound) payloads on the server thread.
 *
 * <p>All handler methods are invoked via {@link IPayloadContext#enqueueWork} to
 * ensure they run on the main server thread.</p>
 */
public final class ServerPayloadHandler {
    private ServerPayloadHandler() {}

    /**
     * Handles a client request to open the backpack reward UI.
     *
     * <p>Loads or creates the player's reward container based on their current
     * reward level, then opens a {@link BackpackContainer} menu with a save
     * callback that persists changes when the container closes.</p>
     */
    public static void handleOpenBackpack(OpenBackpackPayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // Get current reward level from player comments (same as /tyj-reward get)
            CommentInfo commentInfo = new CommentInfo();
            int haveReward = PlayerCommentTools.getPlayerComment(player.getName().getString(), commentInfo);
            int currentLevel = haveReward >= 0 ? Math.max(commentInfo.level, 0) : 0;

            UUID playerUUID = player.getUUID();
            int storedLevel = PlayerRewardManager.getStoredLevel(playerUUID);

            // Use the higher of current level and stored level for container size
            int effectiveLevel = Math.max(currentLevel, storedLevel);
            int expectedSlots = Math.min(effectiveLevel * 15, 900);

            HolderLookup.Provider lookup = player.serverLevel().registryAccess();
            SimpleContainer container = PlayerRewardManager.loadOrCreate(
                    playerUUID, expectedSlots, LZSavedData.getRewardBox(), lookup
            );

            final int saveLevel = effectiveLevel;

            // Empty-container sentinel is -1 (NOT 0) so the client's
            // contentRows = max(0, lastOccupiedRow + 1) collapses to 0 and no
            // scroll panel is created. Server-side BackpackContainer also seeds
            // its field the same way via recomputeLastOccupiedRow() in its ctor.
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

            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("奖励背包");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    BackpackContainer bc = new BackpackContainer(id, inv, container, 0);
                    bc.setSaveCallback(() -> {
                        try {
                            PlayerRewardManager.save(playerUUID, container, lookup, saveLevel);
                        } catch (Exception e) {
                            TimeReward.LOGGER.error("Save callback error", e);
                        }
                    });
                    return bc;
                }
            }, buf -> {
                buf.writeInt(container.getContainerSize());
                buf.writeInt(0);
                buf.writeInt(finalLastOccupiedRow);
            });
        });
    }

    /**
     * Handles a client scroll-offset update for the open backpack container.
     *
     * <p>Verifies that the player's currently open container is a
     * {@link BackpackContainer}, then updates its scroll offset and
     * synchronises the changes back to the client.</p>
     */
    public static void handleScrollChange(ScrollChangePayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (player.containerMenu instanceof BackpackContainer backpack) {
                backpack.setScrollOffset(data.newOffset());
                backpack.broadcastChanges();
            }
        });
    }

    /**
     * Handles a client sort request for the open backpack container.
     *
     * <p>Verifies that the player's currently open container is a
     * {@link BackpackContainer}, then applies the requested sort order and
     * synchronises the changes back to the client.</p>
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
}
