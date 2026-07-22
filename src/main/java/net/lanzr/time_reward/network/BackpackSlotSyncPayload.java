package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record BackpackSlotSyncPayload(int containerId, int scrollOffset, List<ItemStack> items) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackSlotSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "backpack_slot_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackSlotSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerId());
                buf.writeVarInt(payload.scrollOffset());
                int size = payload.items().size();
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    ItemStack.STREAM_CODEC.encode(buf, payload.items().get(i));
                }
            },
            buf -> {
                int containerId = buf.readVarInt();
                int scrollOffset = buf.readVarInt();
                int size = buf.readVarInt();
                List<ItemStack> items = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    items.add(ItemStack.STREAM_CODEC.decode(buf));
                }
                return new BackpackSlotSyncPayload(containerId, scrollOffset, items);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
