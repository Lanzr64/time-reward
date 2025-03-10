package net.lanzr.time_reward.events;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.commands.RewardCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = TimeReward.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModEvent {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static synchronized void onPlayerCloned(PlayerEvent.Clone event) {
        Player old_player = event.getOriginal();
        Player new_player = event.getEntity();

        if(!old_player.getPersistentData().contains(TimeReward.MODID))
            return;

        // 必须有新的数据
        CompoundTag old_Tag = event.getOriginal().getPersistentData().getCompound(TimeReward.MODID);
        new_player.getPersistentData().put(TimeReward.MODID, old_Tag);
    }

    @SubscribeEvent
    public static void CommandRegistration(RegisterCommandsEvent event) {
        RewardCommand.register(event);
    }

}
