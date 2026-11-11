package net.lanzr.time_reward;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.api.RewardTag;
import net.lanzr.time_reward.network.ModNetwork;
import net.lanzr.time_reward.save.LZSavedData;
import net.lanzr.time_reward.save.PlayerRewardManager;
import net.lanzr.time_reward.server.BackpackContainerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value="time_reward")
public class TimeReward {
    public static final String MODID = "time_reward";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TimeReward() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> "OHNOES\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31\ud83d\ude31", (a, b) -> true));
        ModNetwork.register();
        MinecraftForge.EVENT_BUS.register((Object)this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel world = Objects.requireNonNull(event.getServer().getLevel(Level.OVERWORLD), "Overworld must exist when server starts");
        LZSavedData worldData = (LZSavedData)world.getDataStorage().computeIfAbsent(LZSavedData::new, LZSavedData::new, "timeReward-SavedData");
        LZSavedData.setInstance(worldData);
        PlayerRewardManager.initPath(world);
        PlayerCommentTools.init();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player;
        block5: {
            block4: {
                Player player2 = event.getEntity();
                if (!(player2 instanceof ServerPlayer)) break block4;
                player = (ServerPlayer)player2;
                if (LZSavedData.SET_DONE) break block5;
            }
            return;
        }
        RewardTag rewardTag = new RewardTag(player);
        if (rewardTag.getLevel() < 0) {
            MutableComponent message = Component.literal((String)"\u8bc4\u8bba\u5956\u52b1\u5df2\u7ecf\u8bbe\u7f6e\uff0c\u53ef\u4ee5\u4f7f\u7528\u547d\u4ee4/tyj-reward get\u83b7\u53d6\u5956\u52b1\u4e86~\u3002");
            message = message.append((Component)Component.literal((String)"[\u9886\u53d6\u5956\u52b1\u70b9\u6211]").withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tyj-reward get"))));
            player.sendSystemMessage((Component)message);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            BackpackContainerManager.getInstance().removePlayer(player2.getUUID());
        }
    }
}
