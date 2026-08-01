package net.lanzr.time_reward.network;

import io.netty.buffer.ByteBuf;
import net.lanzr.time_reward.TimeReward;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BackpackClosePayload(int containerId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackClosePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "backpack_close"));
    public static final StreamCodec<ByteBuf, BackpackClosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            BackpackClosePayload::containerId,
            BackpackClosePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
