package net.lanzr.time_reward.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.Optional;
import net.lanzr.time_reward.api.CommentInfo;
import net.lanzr.time_reward.api.LZCommonForgeApi;
import net.lanzr.time_reward.api.PlayerCommentTools;
import net.lanzr.time_reward.api.RewardTag;
import net.lanzr.time_reward.container.PaginationContainer;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.save.LZSavedData;
import net.lanzr.time_reward.save.PlayerRewardManager;
import net.lanzr.time_reward.server.BackpackContainerManager;
import net.lanzr.time_reward.tools.RewardBoxTools;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

public class RewardCommand {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher dispatcher = event.getDispatcher();
        LiteralArgumentBuilder literalargumentBuilder = Commands.literal((String)"tyj-reward");
        LiteralArgumentBuilder literalArgumentBuilder_sub_admin = (LiteralArgumentBuilder)Commands.literal((String)"admin").requires(ctx -> ctx.hasPermission(4));
        literalargumentBuilder.executes(ctx -> RewardCommand.cb_showURL(((CommandSourceStack)ctx.getSource()).getPlayer()));
        literalargumentBuilder.then(Commands.literal((String)"get").executes(ctx -> RewardCommand.cb_getreward(((CommandSourceStack)ctx.getSource()).getPlayer())));
        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literalArgumentBuilder_sub_admin.then(Commands.literal((String)"set").executes(ctx -> RewardCommand.cb_setReward(((CommandSourceStack)ctx.getSource()).getPlayer())))).then(Commands.literal("enable")
                .executes(ctx -> RewardCommand.cb_rewardSetDone(((CommandSourceStack)ctx.getSource()).getServer(), true))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ctx -> RewardCommand.cb_rewardSetDone(((CommandSourceStack)ctx.getSource()).getServer(),
                        BoolArgumentType.getBool(ctx, "enabled")))))).then(Commands.literal((String)"preset").executes(ctx -> RewardCommand.cb_presetReward(((CommandSourceStack)ctx.getSource()).getPlayer())))).then(Commands.literal((String)"clean").executes(ctx -> RewardCommand.cb_cleanReward(((CommandSourceStack)ctx.getSource()).getPlayer())))).then(Commands.literal((String)"addRecord").then(Commands.argument((String)"target", (ArgumentType)StringArgumentType.string()).then(Commands.argument((String)"year", (ArgumentType)IntegerArgumentType.integer()).then(Commands.argument((String)"month", (ArgumentType)IntegerArgumentType.integer()).then(Commands.argument((String)"day", (ArgumentType)IntegerArgumentType.integer()).executes(ctx -> RewardCommand.cb_addRecord((CommandSourceStack)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"target"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"year"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"month"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"day"))))))));
        literalArgumentBuilder_sub_admin.then(Commands.literal("reset")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> RewardCommand.cb_resetReward((CommandSourceStack) ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "target")))));
        literalArgumentBuilder_sub_admin.then(Commands.literal("view")
                .then(Commands.argument("playerId", StringArgumentType.word())
                        .executes(ctx -> RewardCommand.cb_viewReward((CommandSourceStack) ctx.getSource(),
                                StringArgumentType.getString(ctx, "playerId")))));
        literalargumentBuilder.then((ArgumentBuilder)literalArgumentBuilder_sub_admin);
        dispatcher.register(literalargumentBuilder);
    }

    private static int cb_showURL(@Nullable ServerPlayer player) {
        MutableComponent message = Component.literal((String)"\u6ca1\u4e8b\u5c31\u5e94\u8be5\u591a\u8bc4\u8bba\u8bc4\u8bba\u670d\u52a1\u5668\uff01");
        message = message.append((Component)Component.literal((String)"[\u70b9\u51fb\u6253\u5f00 MCMOD\u670d\u52a1\u5668\u9875\u9762]").withStyle(style -> style.withColor(LZCommonForgeApi.MsgTypes.OTHER.getmFmt()).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://play.mcmod.cn/sv20187752.html"))));
        if (player != null) {
            player.sendSystemMessage((Component)message);
        }
        return 0;
    }

    private static int cb_getreward(final @Nullable ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        if (!LZSavedData.SET_DONE) {
            LZCommonForgeApi.sendSystemMessage(player, "\u5956\u52b1\u8fd8\u6ca1\u6709\u8bbe\u7f6e\u597d\uff01", LZCommonForgeApi.MsgTypes.ALERT.getmFmt());
            return 0;
        }
        CommentInfo commentInfo = new CommentInfo();
        int haveReward = PlayerCommentTools.getPlayerComment(player.getName().getString(), commentInfo);
        if (haveReward == -1) {
            MutableComponent message = Component.literal((String)"\u4f60\u8fd8\u6ca1\u6709 MCMOD \u8bc4\u8bba\u8bb0\u5f55\u54e6\uff0c\u8bf7\u53bbMCMOD \u670d\u52a1\u5668\u9875\u9762\u8bc4\u8bba\u540e\u8054\u7cfb\u670d\u4e3b");
            message = message.append((Component)Component.literal((String)"[\u70b9\u51fb\u6253\u5f00 MCMOD\u670d\u52a1\u5668\u9875\u9762]").withStyle(style -> style.withColor(LZCommonForgeApi.MsgTypes.OTHER.getmFmt()).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://play.mcmod.cn/sv20187752.html"))));
            player.sendSystemMessage((Component)message);
            return 0;
        }
        final int rewardLevel = commentInfo.level;
        int expectedSlots = Math.min(rewardLevel * 10, 360);
        final UUID playerUUID = player.getUUID();
        int storedLevel = PlayerRewardManager.getStoredLevel(playerUUID);
        boolean isNewClaim = storedLevel < 0;
        boolean isLevelUp = storedLevel >= 0 && rewardLevel > storedLevel;
        final SimpleContainer playerContainer = PlayerRewardManager.loadOrCreate(playerUUID, expectedSlots, LZSavedData.getRewardBox());
        player.openMenu(new MenuProvider(){

            public Component getDisplayName() {
                return Component.literal((String)"\u8bc4\u8bba\u5956\u52b1");
            }

            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                PaginationContainer pc = new PaginationContainer(id, inv, (Container)playerContainer);
                pc.setSaveCallback(() -> {
                    try {
                        PlayerRewardManager.save(playerUUID, playerContainer, rewardLevel);
                        System.out.println("[RewardCommand] Save callback fired for " + player.getName().getString());
                    }
                    catch (Exception e) {
                        System.err.println("[RewardCommand] Save callback error: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                return pc;
            }
        });
        if (isNewClaim || isLevelUp) {
            LZCommonForgeApi.sendSystemMessage(player, String.format("\u8bb0\u5f55\u65f6\u95f4\uff1a %s\uff0c\u4f60\u73b0\u5728\u7684\u5956\u52b1\u7b49\u7ea7\u662f : %d \u7ea7\uff0c\u8ddd\u79bb\u4e0b\u4e2a\u5956\u52b1\u7b49\u7ea7\u8fd8\u6709 %d \u5929", commentInfo.markTime, commentInfo.level, commentInfo.nextLevelRemainDays), LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
            LZCommonForgeApi.sendSystemMessage(player, "have fun!", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        }
        return 1;
    }

    private static int cb_rewardSetDone(MinecraftServer server, boolean enabled) {
        LZSavedData.setDone(enabled);
        server.getPlayerList().getPlayers().forEach(player -> {
            if (!enabled) {
                if (player.containerMenu instanceof PaginationContainer || player.containerMenu instanceof BackpackContainer) {
                    player.closeContainer();
                    BackpackContainerManager.getInstance().forgetContainer(player.getUUID());
                }
                player.sendSystemMessage(Component.literal("评论奖励已关闭，暂时无法领取。"));
                return;
            }
            MutableComponent message = Component.literal("评论奖励已经设置，可以使用命令 /tyj-reward get 获取奖励了。");
            message = message.append(Component.literal("[领取奖励点我]")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tyj-reward get"))));
            player.sendSystemMessage(message);
        });
        return 1;
    }

    private static int cb_setReward(ServerPlayer player) {
        final SimpleContainer container = LZSavedData.getRewardBox();
        player.openMenu(new MenuProvider(){

            public Component getDisplayName() {
                return Component.literal((String)"\u5956\u52b1\u7ba1\u7406");
            }

            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new PaginationContainer(id, inv, (Container)container);
            }
        });
        return 0;
    }

    private static int cb_presetReward(ServerPlayer player) {
        RewardBoxTools.presetReward(player.serverLevel());
        LZCommonForgeApi.sendSystemMessage(player, "\u5956\u52b1\u5e93\u5b58\u5df2\u586b\u5145\u9884\u8bbe\u7269\u54c1\uff01", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        return 0;
    }

    private static int cb_cleanReward(ServerPlayer player) {
        RewardBoxTools.cleanReward();
        LZCommonForgeApi.sendSystemMessage(player, "\u5956\u52b1\u5e93\u5b58\u5df2\u6e05\u7a7a\uff01", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        return 0;
    }

    private static int cb_resetReward(CommandSourceStack source, ServerPlayer target) {
        UUID playerUUID = target.getUUID();
        // Close the menu first: its close callback can otherwise recreate the file after reset.
        if (target.containerMenu instanceof PaginationContainer || target.containerMenu instanceof BackpackContainer) {
            target.closeContainer();
        }
        BackpackContainerManager.getInstance().forgetContainer(playerUUID);
        if (!PlayerRewardManager.reset(playerUUID)) {
            source.sendFailure(Component.literal("清除玩家奖励存档失败，请查看服务端日志。"));
            return 0;
        }
        new RewardTag(target).setLevel(-1);
        source.sendSuccess(() -> Component.literal("已重置 " + target.getName().getString() + " 的奖励领取记录。"), true);
        target.sendSystemMessage(Component.literal("你的奖励领取记录已被管理员重置。"));
        return 1;
    }

    private static int cb_viewReward(CommandSourceStack source, String playerId) throws CommandSyntaxException {
        ServerPlayer viewer = source.getPlayerOrException();
        UUID playerUUID;
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(playerId);
        if (online != null) {
            playerUUID = online.getUUID();
        } else {
            try {
                playerUUID = UUID.fromString(playerId);
            } catch (IllegalArgumentException invalidUuid) {
                Optional<GameProfile> cached = source.getServer().getProfileCache().get(playerId);
                if (cached.isEmpty()) {
                    source.sendFailure(Component.literal("找不到玩家。请使用玩家 UUID 或服务器已识别的玩家名。"));
                    return 0;
                }
                playerUUID = cached.get().getId();
            }
        }

        SimpleContainer snapshot;
        BackpackContainer activeMenu = BackpackContainerManager.getInstance().getContainer(playerUUID);
        if (activeMenu != null) {
            Container live = activeMenu.getStorageContainer();
            snapshot = new SimpleContainer(live.getContainerSize());
            for (int i = 0; i < live.getContainerSize(); i++) {
                snapshot.setItem(i, live.getItem(i).copy());
            }
        } else {
            Optional<SimpleContainer> existing = PlayerRewardManager.loadExisting(playerUUID);
            if (existing.isEmpty()) {
                source.sendFailure(Component.literal("该玩家没有可查看的奖励存档。"));
                return 0;
            }
            snapshot = existing.get();
        }

        viewer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("奖励查看：" + playerId);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                return new PaginationContainer(id, inv, snapshot, true);
            }
        });
        return 1;
    }

    private static int cb_addRecord(CommandSourceStack src, String targetName, int year, int month, int day) throws CommandSyntaxException {
        month = Math.min(Math.max(month, 1), 12);
        day = Math.min(Math.max(day, 1), 31);
        String msg = String.format("\u8bb0\u5f55\uff1a%s : %d-%d-%d", targetName, year, month, day);
        if (src.isPlayer()) {
            ServerPlayer player = src.getPlayerOrException();
            LZCommonForgeApi.sendCenterSystemMessage(player, msg, LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        }
        PlayerCommentTools.addPlayerRecord(targetName, year, month, day);
        return 0;
    }
}
