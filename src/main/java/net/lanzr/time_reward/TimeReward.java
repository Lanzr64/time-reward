package net.lanzr.time_reward;

import com.mojang.logging.LogUtils;
import net.lanzr.time_reward.save.LZSavedData;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(TimeReward.MODID)
public class TimeReward
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "time_reward";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public TimeReward()
    {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () ->
                new IExtensionPoint.DisplayTest(() ->
                        NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
//
//        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
//
        MinecraftForge.EVENT_BUS.register(this);
//
//        RewardContainerTypes.CONTAINERS.register(modBus);

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel world = event.getServer().getLevel(Level.OVERWORLD);
        assert world != null;
        if (!world.isClientSide) {
            LZSavedData worldData = world.getDataStorage().computeIfAbsent(LZSavedData::new, LZSavedData::new, LZSavedData.SAVE_DATA_NAME);
            LZSavedData.setInstance(worldData);
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
