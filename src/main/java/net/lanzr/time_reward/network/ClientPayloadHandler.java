package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.client.gui.BackpackScreen;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles S2C (client-bound) payloads on the client thread.
 *
 * <p>All handler methods are invoked via {@link IPayloadContext#enqueueWork} to
 * ensure they run on the main client thread.</p>
 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    /**
     * Handles the authoritative {@link BackpackStatePayload} from the server.
     *
     * <p>Updates the bound {@link BackpackContainer}'s lastOccupiedRow field when
     * the open menu matches the payload's containerId, then notifies the active
     * {@link BackpackScreen} so it can re-evaluate its scroll panel (recreate or
     * destroy) and re-clamp the scroll distance against the new content height.</p>
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