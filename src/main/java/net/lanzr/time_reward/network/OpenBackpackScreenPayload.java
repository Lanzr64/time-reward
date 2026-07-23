package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBackpackScreenPayload(int containerId, int containerSize, int scrollOffset, int lastOccupiedRow, Component title) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenBackpackScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "open_backpack_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            OpenBackpackScreenPayload::containerId,
            ByteBufCodecs.VAR_INT,
            OpenBackpackScreenPayload::containerSize,
            ByteBufCodecs.VAR_INT,
            OpenBackpackScreenPayload::scrollOffset,
            ByteBufCodecs.VAR_INT,
            OpenBackpackScreenPayload::lastOccupiedRow,
            ComponentSerialization.STREAM_CODEC,
            OpenBackpackScreenPayload::title,
            OpenBackpackScreenPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
