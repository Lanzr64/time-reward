package net.lanzr.time_reward;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.api.RewardTag;
import net.lanzr.time_reward.save.LZSavedData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if(LZSavedData.SET_DONE) {
            RewardTag rewardTag = new RewardTag(player);
            int playerLevel = rewardTag.getLevel();
            if(playerLevel < 0 ) {
                MutableComponent message = Component.literal("评论奖励已经设置，可以使用命令/tyj-reward get获取奖励了~。");
                message = message.append(Component.literal("[领取奖励点我]")
                        .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tyj-reward get"))));
                player.sendSystemMessage(message);
            }
        }

    }
}
