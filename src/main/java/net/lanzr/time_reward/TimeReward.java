package net.lanzr.time_reward;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.api.RewardTag;
import net.lanzr.time_reward.network.BackpackCarriedUpdatePayload;
import net.lanzr.time_reward.network.BackpackClickPayload;
import net.lanzr.time_reward.network.BackpackClosePayload;
import net.lanzr.time_reward.network.BackpackSlotSyncPayload;
import net.lanzr.time_reward.network.BackpackStatePayload;
import net.lanzr.time_reward.network.OpenBackpackPayload;
import net.lanzr.time_reward.network.OpenBackpackScreenPayload;
import net.lanzr.time_reward.network.ScrollChangePayload;
import net.lanzr.time_reward.network.ServerPayloadHandler;
import net.lanzr.time_reward.network.SortPayload;
import net.lanzr.time_reward.save.LZSavedData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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

    // S2C payload handler delegates.
    // Registered via playBidirectional with no-op defaults so the server knows the types.
    // Actual client handlers set by ClientModEvents via initClientHandlers().
    // Lambdas in registerPayloads read these fields at invocation time (field reference, not capture).
    private static IPayloadHandler<BackpackStatePayload> $backpackStateHandler = (d, c) -> {};
    private static IPayloadHandler<BackpackSlotSyncPayload> $backpackSlotSyncHandler = (d, c) -> {};
    private static IPayloadHandler<OpenBackpackScreenPayload> $openBackpackScreenHandler = (d, c) -> {};
    private static IPayloadHandler<BackpackCarriedUpdatePayload> $carriedUpdateHandler = (d, c) -> {};

    /**
     * Called from {@link net.lanzr.time_reward.client.ClientModEvents} (client only) to replace the no-op
     * S2C payload handlers with the real client-side implementations.
     */
    public static void initClientHandlers(
            IPayloadHandler<BackpackStatePayload> backpackState,
            IPayloadHandler<BackpackSlotSyncPayload> backpackSlotSync,
            IPayloadHandler<OpenBackpackScreenPayload> openBackpackScreen,
            IPayloadHandler<BackpackCarriedUpdatePayload> carriedUpdate
    ) {
        $backpackStateHandler = backpackState;
        $backpackSlotSyncHandler = backpackSlotSync;
        $openBackpackScreenHandler = openBackpackScreen;
        $carriedUpdateHandler = carriedUpdate;
    }

    public TimeReward(IEventBus modEventBus, ModContainer modContainer)    {
        NeoForge.EVENT_BUS.register(this);

        // Register network payloads
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, this::registerPayloads);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(
                OpenBackpackPayload.TYPE,
                OpenBackpackPayload.STREAM_CODEC,
                ServerPayloadHandler::handleOpenBackpack
        );
        registrar.playToServer(
                ScrollChangePayload.TYPE,
                ScrollChangePayload.STREAM_CODEC,
                ServerPayloadHandler::handleScrollChange
        );
        registrar.playToServer(
                SortPayload.TYPE,
                SortPayload.STREAM_CODEC,
                ServerPayloadHandler::handleSort
        );
        registrar.playToServer(
                BackpackClickPayload.TYPE,
                BackpackClickPayload.STREAM_CODEC,
                ServerPayloadHandler::handleBackpackClick
        );
        registrar.playToServer(
                BackpackClosePayload.TYPE,
                BackpackClosePayload.STREAM_CODEC,
                ServerPayloadHandler::handleBackpackClose
        );

        // S2C payload type registration (server must know the type to SEND them).
        // Handlers delegate to static fields: on the server the no-op default is never called
        // (S2C payloads are never received by server); on the client the fields are replaced
        // by ClientModEvents.initClientHandlers() before gameplay starts.
        registrar.playBidirectional(BackpackStatePayload.TYPE, BackpackStatePayload.STREAM_CODEC, (d, c) -> $backpackStateHandler.handle(d, c));
        registrar.playBidirectional(BackpackSlotSyncPayload.TYPE, BackpackSlotSyncPayload.STREAM_CODEC, (d, c) -> $backpackSlotSyncHandler.handle(d, c));
        registrar.playBidirectional(OpenBackpackScreenPayload.TYPE, OpenBackpackScreenPayload.STREAM_CODEC, (d, c) -> $openBackpackScreenHandler.handle(d, c));
        registrar.playBidirectional(BackpackCarriedUpdatePayload.TYPE, BackpackCarriedUpdatePayload.STREAM_CODEC, (d, c) -> $carriedUpdateHandler.handle(d, c));
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel world = java.util.Objects.requireNonNull(
                event.getServer().getLevel(Level.OVERWORLD),
                "Overworld must exist when server starts"
        );
        if (!world.isClientSide) {
            LZSavedData worldData = world.getDataStorage().computeIfAbsent(LZSavedData.FACTORY, LZSavedData.SAVE_DATA_NAME);
            LZSavedData.setInstance(worldData);
        }
        PlayerCommentTools.init();
    }
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
