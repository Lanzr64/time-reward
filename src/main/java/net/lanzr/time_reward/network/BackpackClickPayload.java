package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;

public record BackpackClickPayload(int containerId, int slotId, int button, int clickTypeOrdinal, boolean hasCarried) {
    public static void encode(BackpackClickPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.slotId());
        buf.writeVarInt(payload.button());
        buf.writeVarInt(payload.clickTypeOrdinal());
        buf.writeBoolean(payload.hasCarried());
    }

    public static BackpackClickPayload decode(FriendlyByteBuf buf) {
        return new BackpackClickPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
