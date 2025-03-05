package net.lanzr.time_reward.events;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.commands.RewardCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TimeReward.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModEvent {


    @SubscribeEvent
    public static void CommandRegistration(RegisterCommandsEvent event) {
        RewardCommand.register(event);
    }

}
