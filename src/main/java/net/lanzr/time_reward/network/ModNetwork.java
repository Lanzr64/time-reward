package net.lanzr.time_reward.network;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.lanzr.time_reward.network.BackpackCarriedUpdatePayload;
import net.lanzr.time_reward.network.BackpackClickPayload;
import net.lanzr.time_reward.network.BackpackClosePayload;
import net.lanzr.time_reward.network.BackpackSlotSyncPayload;
import net.lanzr.time_reward.network.BackpackStatePayload;
import net.lanzr.time_reward.network.OpenBackpackPayload;
import net.lanzr.time_reward.network.OpenBackpackScreenPayload;
import net.lanzr.time_reward.network.ScrollChangePayload;
import net.lanzr.time_reward.network.ServerPayloadHandler;
import net.lanzr.time_reward.network.SortPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel((ResourceLocation)new ResourceLocation("time_reward", "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    private static BiConsumer<BackpackStatePayload, Supplier<NetworkEvent.Context>> backpackStateHandler = ModNetwork::ignoreClientPacket;
    private static BiConsumer<BackpackSlotSyncPayload, Supplier<NetworkEvent.Context>> backpackSlotSyncHandler = ModNetwork::ignoreClientPacket;
    private static BiConsumer<OpenBackpackScreenPayload, Supplier<NetworkEvent.Context>> openBackpackScreenHandler = ModNetwork::ignoreClientPacket;
    private static BiConsumer<BackpackCarriedUpdatePayload, Supplier<NetworkEvent.Context>> carriedUpdateHandler = ModNetwork::ignoreClientPacket;
    private static BiConsumer<BackpackStorageSlotPayload, Supplier<NetworkEvent.Context>> storageSlotHandler = ModNetwork::ignoreClientPacket;

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenBackpackPayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(OpenBackpackPayload::encode).decoder(OpenBackpackPayload::decode).consumerNetworkThread(ServerPayloadHandler::handleOpenBackpack).add();
        CHANNEL.messageBuilder(ScrollChangePayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(ScrollChangePayload::encode).decoder(ScrollChangePayload::decode).consumerNetworkThread(ServerPayloadHandler::handleScrollChange).add();
        CHANNEL.messageBuilder(SortPayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(SortPayload::encode).decoder(SortPayload::decode).consumerNetworkThread(ServerPayloadHandler::handleSort).add();
        CHANNEL.messageBuilder(BackpackClickPayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(BackpackClickPayload::encode).decoder(BackpackClickPayload::decode).consumerNetworkThread(ServerPayloadHandler::handleBackpackClick).add();
        CHANNEL.messageBuilder(BackpackClosePayload.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(BackpackClosePayload::encode).decoder(BackpackClosePayload::decode).consumerNetworkThread(ServerPayloadHandler::handleBackpackClose).add();
        CHANNEL.messageBuilder(BackpackStatePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(BackpackStatePayload::encode).decoder(BackpackStatePayload::decode).consumerNetworkThread(ModNetwork::handleBackpackState).add();
        CHANNEL.messageBuilder(BackpackSlotSyncPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(BackpackSlotSyncPayload::encode).decoder(BackpackSlotSyncPayload::decode).consumerNetworkThread(ModNetwork::handleBackpackSlotSync).add();
        CHANNEL.messageBuilder(OpenBackpackScreenPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(OpenBackpackScreenPayload::encode).decoder(OpenBackpackScreenPayload::decode).consumerNetworkThread(ModNetwork::handleOpenBackpackScreen).add();
        CHANNEL.messageBuilder(BackpackCarriedUpdatePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(BackpackCarriedUpdatePayload::encode).decoder(BackpackCarriedUpdatePayload::decode).consumerNetworkThread(ModNetwork::handleCarriedUpdate).add();
        CHANNEL.messageBuilder(BackpackStorageSlotPayload.class, id, NetworkDirection.PLAY_TO_CLIENT).encoder(BackpackStorageSlotPayload::encode).decoder(BackpackStorageSlotPayload::decode).consumerNetworkThread(ModNetwork::handleStorageSlot).add();
    }

    public static void initClientHandlers(BiConsumer<BackpackStatePayload, Supplier<NetworkEvent.Context>> backpackState, BiConsumer<BackpackSlotSyncPayload, Supplier<NetworkEvent.Context>> backpackSlotSync, BiConsumer<OpenBackpackScreenPayload, Supplier<NetworkEvent.Context>> openBackpackScreen, BiConsumer<BackpackCarriedUpdatePayload, Supplier<NetworkEvent.Context>> carriedUpdate, BiConsumer<BackpackStorageSlotPayload, Supplier<NetworkEvent.Context>> storageSlot) {
        backpackStateHandler = backpackState;
        backpackSlotSyncHandler = backpackSlotSync;
        openBackpackScreenHandler = openBackpackScreen;
        carriedUpdateHandler = carriedUpdate;
        storageSlotHandler = storageSlot;
    }

    private static void handleBackpackState(BackpackStatePayload data, Supplier<NetworkEvent.Context> context) {
        backpackStateHandler.accept(data, context);
    }

    private static void handleBackpackSlotSync(BackpackSlotSyncPayload data, Supplier<NetworkEvent.Context> context) {
        backpackSlotSyncHandler.accept(data, context);
    }

    private static void handleOpenBackpackScreen(OpenBackpackScreenPayload data, Supplier<NetworkEvent.Context> context) {
        openBackpackScreenHandler.accept(data, context);
    }

    private static void handleCarriedUpdate(BackpackCarriedUpdatePayload data, Supplier<NetworkEvent.Context> context) {
        carriedUpdateHandler.accept(data, context);
    }

    private static void handleStorageSlot(BackpackStorageSlotPayload data, Supplier<NetworkEvent.Context> context) {
        storageSlotHandler.accept(data, context);
    }

    private static <T> void ignoreClientPacket(T data, Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
