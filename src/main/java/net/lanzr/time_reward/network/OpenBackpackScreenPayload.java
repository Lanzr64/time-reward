package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public record OpenBackpackScreenPayload(int containerId, int containerSize, int scrollOffset, int lastOccupiedRow, Component title) {
    public static void encode(OpenBackpackScreenPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.containerSize());
        buf.writeVarInt(payload.scrollOffset());
        buf.writeVarInt(payload.lastOccupiedRow());
        buf.writeComponent(payload.title());
    }

    public static OpenBackpackScreenPayload decode(FriendlyByteBuf buf) {
        return new OpenBackpackScreenPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readComponent());
    }
}
