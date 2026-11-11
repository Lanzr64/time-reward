package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record BackpackCarriedUpdatePayload(int containerId, ItemStack carried) {
    public static void encode(BackpackCarriedUpdatePayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeItem(payload.carried());
    }

    public static BackpackCarriedUpdatePayload decode(FriendlyByteBuf buf) {
        return new BackpackCarriedUpdatePayload(buf.readVarInt(), buf.readItem());
    }
}
