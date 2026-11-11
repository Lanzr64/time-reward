package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;

public record BackpackClosePayload(int containerId) {
    public static void encode(BackpackClosePayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
    }

    public static BackpackClosePayload decode(FriendlyByteBuf buf) {
        return new BackpackClosePayload(buf.readVarInt());
    }
}
