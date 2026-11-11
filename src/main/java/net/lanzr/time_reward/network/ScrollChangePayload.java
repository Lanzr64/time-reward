package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;

public record ScrollChangePayload(int containerId, int newOffset, int visibleRows) {
    public static void encode(ScrollChangePayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.newOffset());
        buf.writeVarInt(payload.visibleRows());
    }

    public static ScrollChangePayload decode(FriendlyByteBuf buf) {
        return new ScrollChangePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
