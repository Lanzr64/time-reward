package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
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
     * Placeholder handler for {@link BackpackStatePayload}.
     *
     * <p>Task 5 will replace the LOGGER.info body with the actual client-side
     * re-clamp logic for the backpack scroll offset.</p>
     */
    public static void handleBackpackState(BackpackStatePayload data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            TimeReward.LOGGER.info("[BackpackState-Placeholder] received lastOccupiedRow={} for containerId={}",
                    data.lastOccupiedRow(), data.containerId());
        });
    }
}