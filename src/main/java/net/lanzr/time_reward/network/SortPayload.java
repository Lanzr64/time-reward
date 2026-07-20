package net.lanzr.time_reward.network;

import io.netty.buffer.ByteBuf;
import net.lanzr.time_reward.TimeReward;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SortPayload(int containerId, int sortOrdinal) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SortPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "sort"));
    public static final StreamCodec<ByteBuf, SortPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SortPayload::containerId,
            ByteBufCodecs.VAR_INT,
            SortPayload::sortOrdinal,
            SortPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
