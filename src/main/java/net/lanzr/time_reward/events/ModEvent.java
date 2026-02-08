package net.lanzr.time_reward.events;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.commands.RewardCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TimeReward.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvent {

    @Mod.EventBusSubscriber(modid = TimeReward.MODID)
    public static class RegisterCommands {
        static public void printS(String str) {
            System.out.println(str);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static synchronized void onPlayerCloned(PlayerEvent.Clone event) {
            Player old_player = event.getOriginal();
            Player new_player = event.getEntity();

            if(!old_player.getPersistentData().contains(TimeReward.MODID))
                return;

            CompoundTag old_Tag = event.getOriginal().getPersistentData().getCompound(TimeReward.MODID);
            new_player.getPersistentData().put(TimeReward.MODID, old_Tag);
        }
        @SubscribeEvent
        public static void CommandRegistration(RegisterCommandsEvent event) {
            RewardCommand.register(event);
//            NewRewardCommand.register(event);
        }
    }

}
