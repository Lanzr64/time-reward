package net.lanzr.time_reward;

import com.mojang.logging.LogUtils;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.api.RewardTag;
import net.lanzr.time_reward.save.LZSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
    public static final Logger LOGGER= LogUtils.getLogger();

    public TimeReward()
    {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () ->
                new IExtensionPoint.DisplayTest(() ->
                        NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
//
//        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
//
        MinecraftForge.EVENT_BUS.register(this);
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
        PlayerCommentTools.init();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (LZSavedData.SET_DONE) {
            RewardTag rewardTag = new RewardTag(player);
            int playerLevel = rewardTag.getLevel();
            if (playerLevel < 0) {
                MutableComponent message = Component.literal("评论奖励已经设置，可以使用命令/tyj-reward get获取奖励了~。");
                message = message.append(Component.literal("[领取奖励点我]")
                        .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tyj-reward get"))));
                player.sendSystemMessage(message);
            }
        }

    }
}
