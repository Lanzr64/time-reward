package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;

public record BackpackStatePayload(int containerId, int lastOccupiedRow) {
    public static void encode(BackpackStatePayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.lastOccupiedRow());
    }

    public static BackpackStatePayload decode(FriendlyByteBuf buf) {
        return new BackpackStatePayload(buf.readVarInt(), buf.readVarInt());
    }
}
