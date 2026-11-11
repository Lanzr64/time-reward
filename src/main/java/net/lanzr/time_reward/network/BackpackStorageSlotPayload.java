package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Updates an absolute storage index, independent of the current scroll position. */
public record BackpackStorageSlotPayload(int containerId, int storageIndex, ItemStack item) {
    public static void encode(BackpackStorageSlotPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.storageIndex());
        buf.writeItem(payload.item());
    }

    public static BackpackStorageSlotPayload decode(FriendlyByteBuf buf) {
        return new BackpackStorageSlotPayload(buf.readVarInt(), buf.readVarInt(), buf.readItem());
    }
}
