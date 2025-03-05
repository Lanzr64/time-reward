package net.lanzr.time_reward;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.save.LZSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(TimeReward.MODID)
public class TimeReward
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "time_reward";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public TimeReward(IEventBus modEventBus, ModContainer modContainer)    {

        NeoForge.EVENT_BUS.register(this);

    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel world = event.getServer().getLevel(Level.OVERWORLD);
        assert world != null;
        if (!world.isClientSide) {
            LZSavedData worldData = world.getDataStorage().computeIfAbsent(LZSavedData.FACTORY, LZSavedData.SAVE_DATA_NAME);
            LZSavedData.setInstance(worldData);
        }
        PlayerCommentTools.init();
    }

}
