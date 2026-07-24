package net.lanzr.time_reward.client;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.network.BackpackCarriedUpdatePayload;
import net.lanzr.time_reward.network.BackpackSlotSyncPayload;
import net.lanzr.time_reward.network.BackpackStatePayload;
import net.lanzr.time_reward.network.ClientPayloadHandler;
import net.lanzr.time_reward.network.OpenBackpackPayload;
import net.lanzr.time_reward.network.OpenBackpackScreenPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = TimeReward.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeybindHandler.getOpenBackpackKey());
    }

    @SubscribeEvent
    public static void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        // S2C - handlers run on CLIENT only
        registrar.playToClient(BackpackStatePayload.TYPE, BackpackStatePayload.STREAM_CODEC, ClientPayloadHandler::handleBackpackState);
        registrar.playToClient(BackpackSlotSyncPayload.TYPE, BackpackSlotSyncPayload.STREAM_CODEC, ClientPayloadHandler::handleBackpackSlotSync);
        registrar.playToClient(OpenBackpackScreenPayload.TYPE, OpenBackpackScreenPayload.STREAM_CODEC, ClientPayloadHandler::handleOpenBackpackScreen);
        registrar.playToClient(BackpackCarriedUpdatePayload.TYPE, BackpackCarriedUpdatePayload.STREAM_CODEC, ClientPayloadHandler::handleCarriedUpdate);
    }
}

@EventBusSubscriber(modid = TimeReward.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
class ClientGameEvents {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (KeybindHandler.getOpenBackpackKey().consumeClick()) {
            PacketDistributor.sendToServer(new OpenBackpackPayload());
        }
    }
}
