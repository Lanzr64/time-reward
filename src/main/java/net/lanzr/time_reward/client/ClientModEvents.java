package net.lanzr.time_reward.client;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.network.OpenBackpackPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TimeReward.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeybindHandler.getOpenBackpackKey());
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
