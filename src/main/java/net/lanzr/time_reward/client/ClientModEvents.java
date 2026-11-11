package net.lanzr.time_reward.client;

import net.lanzr.time_reward.client.KeybindHandler;
import net.lanzr.time_reward.network.ClientPayloadHandler;
import net.lanzr.time_reward.network.ModNetwork;
import net.lanzr.time_reward.network.OpenBackpackPayload;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="time_reward", bus=Mod.EventBusSubscriber.Bus.MOD, value={Dist.CLIENT})
public class ClientModEvents {
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeybindHandler.getOpenBackpackKey());
        MinecraftForge.EVENT_BUS.addListener(ClientModEvents::onClientTick);
        ModNetwork.initClientHandlers(ClientPayloadHandler::handleBackpackState, ClientPayloadHandler::handleBackpackSlotSync, ClientPayloadHandler::handleOpenBackpackScreen, ClientPayloadHandler::handleCarriedUpdate, ClientPayloadHandler::handleStorageSlot);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (KeybindHandler.getOpenBackpackKey().consumeClick()) {
            ModNetwork.sendToServer(new OpenBackpackPayload());
        }
    }
}
