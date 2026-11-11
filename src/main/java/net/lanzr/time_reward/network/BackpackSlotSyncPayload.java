package net.lanzr.time_reward.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** A contiguous batch of storage slots starting at an absolute row. */
public record BackpackSlotSyncPayload(int containerId, int startRow, List<ItemStack> items) {
    public static void encode(BackpackSlotSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.startRow());
        buf.writeVarInt(payload.items().size());
        payload.items().forEach(arg_0 -> ((FriendlyByteBuf)buf).writeItem(arg_0));
    }

    public static BackpackSlotSyncPayload decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int startRow = buf.readVarInt();
        int size = buf.readVarInt();
        ArrayList<ItemStack> items = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; ++i) {
            items.add(buf.readItem());
        }
        return new BackpackSlotSyncPayload(containerId, startRow, items);
    }
}
