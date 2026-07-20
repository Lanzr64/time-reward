package net.lanzr.time_reward.network;

import io.netty.buffer.ByteBuf;
import net.lanzr.time_reward.TimeReward;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScrollChangePayload(int containerId, int newOffset) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ScrollChangePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "scroll_change"));
    public static final StreamCodec<ByteBuf, ScrollChangePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ScrollChangePayload::containerId,
            ByteBufCodecs.VAR_INT,
            ScrollChangePayload::newOffset,
            ScrollChangePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
