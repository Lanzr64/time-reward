package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;

public record SortPayload(int containerId, int sortOrdinal) {
    public static void encode(SortPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.sortOrdinal());
    }

    public static SortPayload decode(FriendlyByteBuf buf) {
        return new SortPayload(buf.readVarInt(), buf.readVarInt());
    }
}
