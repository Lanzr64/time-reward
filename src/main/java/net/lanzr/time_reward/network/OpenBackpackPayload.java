package net.lanzr.time_reward.network;

import net.minecraft.network.FriendlyByteBuf;

public record OpenBackpackPayload() {
    public static void encode(OpenBackpackPayload payload, FriendlyByteBuf buf) {
    }

    public static OpenBackpackPayload decode(FriendlyByteBuf buf) {
        return new OpenBackpackPayload();
    }
}
