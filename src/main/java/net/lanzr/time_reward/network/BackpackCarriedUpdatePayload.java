package net.lanzr.time_reward.network;

import net.lanzr.time_reward.TimeReward;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record BackpackCarriedUpdatePayload(int containerId, ItemStack carried) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackCarriedUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TimeReward.MODID, "backpack_carried_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackCarriedUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerId());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.carried());
            },
            buf -> {
                int containerId = buf.readVarInt();
                ItemStack carried = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                return new BackpackCarriedUpdatePayload(containerId, carried);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
