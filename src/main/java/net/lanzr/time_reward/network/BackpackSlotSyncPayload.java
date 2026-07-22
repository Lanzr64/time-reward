package net.lanzr.time_reward.network;

import io.netty.buffer.ByteBuf;
import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record BackpackSlotSyncPayload(int containerId, int scrollOffset, List<ItemStack> items) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackSlotSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "backpack_slot_sync"));

    public static final StreamCodec<ByteBuf, BackpackSlotSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            BackpackSlotSyncPayload::containerId,
            ByteBufCodecs.VAR_INT,
            BackpackSlotSyncPayload::scrollOffset,
            ByteBufCodecs.list(ItemStack.STREAM_CODEC, BackpackContainer.TOTAL_DISPLAY_SLOTS),
            BackpackSlotSyncPayload::items,
            BackpackSlotSyncPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
