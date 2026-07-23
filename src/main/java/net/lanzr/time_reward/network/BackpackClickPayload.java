package net.lanzr.time_reward.network;

import io.netty.buffer.ByteBuf;
import net.lanzr.time_reward.TimeReward;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BackpackClickPayload(int containerId, int slotId, int button, int clickTypeOrdinal, boolean hasCarried) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackClickPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "backpack_click"));
    public static final StreamCodec<ByteBuf, BackpackClickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            BackpackClickPayload::containerId,
            ByteBufCodecs.VAR_INT,
            BackpackClickPayload::slotId,
            ByteBufCodecs.VAR_INT,
            BackpackClickPayload::button,
            ByteBufCodecs.VAR_INT,
            BackpackClickPayload::clickTypeOrdinal,
            ByteBufCodecs.BOOL,
            BackpackClickPayload::hasCarried,
            BackpackClickPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
