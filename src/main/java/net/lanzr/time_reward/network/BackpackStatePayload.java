package net.lanzr.time_reward.network;

import io.netty.buffer.ByteBuf;
import net.lanzr.time_reward.TimeReward;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BackpackStatePayload(int containerId, int lastOccupiedRow) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "backpack_state"));
    public static final StreamCodec<ByteBuf, BackpackStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            BackpackStatePayload::containerId,
            ByteBufCodecs.VAR_INT,
            BackpackStatePayload::lastOccupiedRow,
            BackpackStatePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}