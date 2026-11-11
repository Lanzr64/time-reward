package net.lanzr.time_reward.events;

import net.lanzr.time_reward.commands.RewardCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="time_reward", bus=Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvent {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static synchronized void onPlayerCloned(PlayerEvent.Clone event) {
        Player old_player = event.getOriginal();
        Player new_player = event.getEntity();
        if (!old_player.getPersistentData().contains("time_reward")) {
            return;
        }
        CompoundTag old_Tag = event.getOriginal().getPersistentData().getCompound("time_reward");
        new_player.getPersistentData().put("time_reward", (Tag)old_Tag);
    }

    @SubscribeEvent
    public static void CommandRegistration(RegisterCommandsEvent event) {
        RewardCommand.register(event);
    }
}
