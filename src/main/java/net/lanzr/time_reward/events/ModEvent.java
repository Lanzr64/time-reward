package net.lanzr.time_reward.events;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.commands.RewardCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TimeReward.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvent {

    @Mod.EventBusSubscriber(modid = TimeReward.MODID)
    public static class RegisterCommands {
        static public void printSTr(String str) {
            System.out.println(str);
        }

        @SubscribeEvent
        public static void CommandRegistration(RegisterCommandsEvent event) {
            RewardCommand.register(event);
        }
    }

}
