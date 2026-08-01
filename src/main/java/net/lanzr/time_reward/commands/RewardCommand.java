package net.lanzr.time_reward.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.api.*;
import net.lanzr.time_reward.container.PaginationContainer;
import net.lanzr.time_reward.save.LZSavedData;
import net.lanzr.time_reward.save.PlayerRewardManager;
import net.lanzr.time_reward.tools.RewardBoxTools;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class RewardCommand {
    public static void register(RegisterCommandsEvent event) {

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        final LiteralArgumentBuilder<CommandSourceStack> literalargumentBuilder =
                Commands.literal("tyj-reward");
        final LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder_sub_admin =
                Commands.literal("admin")
                        .requires(ctx -> ctx.hasPermission(4));
        literalargumentBuilder.executes(ctx -> cb_showURL(ctx.getSource().getPlayer()));
        literalargumentBuilder
                .then(Commands.literal("get").executes(ctx -> cb_getreward(ctx.getSource().getPlayer())));

        literalArgumentBuilder_sub_admin
                .then(Commands.literal("set").executes(ctx -> cb_setReward(ctx.getSource().getPlayer())))
                .then(Commands.literal("enable").executes(ctx -> cb_rewardSetDone(ctx.getSource().getServer())))
                .then(Commands.literal("preset").executes(ctx -> cb_presetReward(ctx.getSource().getPlayer())))
                .then(Commands.literal("clean").executes(ctx -> cb_cleanReward(ctx.getSource().getPlayer())))
                .then(Commands.literal("addRecord")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .then(Commands.argument("year", IntegerArgumentType.integer())
                                        .then(Commands.argument("month", IntegerArgumentType.integer())
                                                .then(Commands.argument("day", IntegerArgumentType.integer())
                                                        .executes(ctx -> cb_addRecord(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx,"target"),
                                                                IntegerArgumentType.getInteger(ctx,"year"),
                                                                IntegerArgumentType.getInteger(ctx,"month"),
                                                                IntegerArgumentType.getInteger(ctx,"day")
                                                        ))
                                                )))));
        literalargumentBuilder.then(literalArgumentBuilder_sub_admin);
        dispatcher.register(literalargumentBuilder);
    }

    private static int cb_showURL(@Nullable ServerPlayer player) {
        MutableComponent message = Component.literal("没事就应该多评论评论服务器！");
        message = message.append(Component.literal("[点击打开 MCMOD服务器页面]")
                .withStyle(style -> style.withColor(LZCommonForgeApi.MsgTypes.OTHER.getmFmt())
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://play.mcmod.cn/sv20187752.html"))));
        if (player != null) {
            player.sendSystemMessage(message);
        }
        return 0;
    }

    private static int cb_getreward(@Nullable ServerPlayer player) {
        if(player==null) {
            return 0;
        }
        // 判断奖励是否设置完成
        if(!LZSavedData.SET_DONE){
            LZCommonForgeApi.sendSystemMessage(player,"奖励还没有设置好！", LZCommonForgeApi.MsgTypes.ALERT.getmFmt());
            return 0;
        }
        // 判断玩家是否有领取权限
        CommentInfo commentInfo = new CommentInfo();
        int haveReward = PlayerCommentTools.getPlayerComment(player.getName().getString(), commentInfo);
        if(haveReward == -1) {
            MutableComponent message = Component.literal("你还没有 MCMOD 评论记录哦，请去MCMOD 服务器页面评论后联系服主");
            message = message.append(Component.literal("[点击打开 MCMOD服务器页面]")
                    .withStyle(style -> style.withColor(LZCommonForgeApi.MsgTypes.OTHER.getmFmt())
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://play.mcmod.cn/sv20187752.html"))));
            player.sendSystemMessage(message);
            return 0;
        }

        int rewardLevel = commentInfo.level;
        int expectedSlots = Math.min(rewardLevel * LZSavedData.SLOTS_PER_LEVEL, LZSavedData.CONTAINER_SIZE);

        UUID playerUUID = player.getUUID();

        // Check if this is first claim or level-up for message control
        int storedLevel = PlayerRewardManager.getStoredLevel(playerUUID);
        boolean isNewClaim = storedLevel < 0;
        boolean isLevelUp = storedLevel >= 0 && rewardLevel > storedLevel;

        HolderLookup.Provider lookup = player.serverLevel().registryAccess();
        SimpleContainer playerContainer = PlayerRewardManager.loadOrCreate(
            playerUUID, expectedSlots, LZSavedData.getRewardBox(), lookup
        );

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("评论奖励");
            }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                PaginationContainer pc = new PaginationContainer(id, inv, playerContainer);
                pc.setSaveCallback(() -> {
                    try {
                        PlayerRewardManager.save(playerUUID, playerContainer, lookup, rewardLevel);
                        System.out.println("[RewardCommand] Save callback fired for " + player.getName().getString());
                    } catch (Exception e) {
                        System.err.println("[RewardCommand] Save callback error: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                return pc;
            }
        });

        // Only show reward info on first claim or level-up
        if (isNewClaim || isLevelUp) {
            LZCommonForgeApi.sendSystemMessage(player, String.format("记录时间： %s，你现在的奖励等级是 : %d 级，距离下个奖励等级还有 %d 天",
                    commentInfo.markTime,
                    commentInfo.level,
                    commentInfo.nextLevelRemainDays), LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
            LZCommonForgeApi.sendSystemMessage(player,"have fun!", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        }

        return 1;
    }
    private static int cb_rewardSetDone(MinecraftServer server) {
        LZSavedData.setDone(true);
        server.getPlayerList().getPlayers().forEach(player ->{
            MutableComponent message = Component.literal("评论奖励已经设置，可以使用命令/tyj-reward get获取奖励了~。");
            message = message.append(Component.literal("[领取奖励点我]")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tyj-reward get"))));
            player.sendSystemMessage(message);
        });
        return 0;
    }

    private static int cb_setReward(ServerPlayer player) {
        SimpleContainer container = LZSavedData.getRewardBox();
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("奖励管理");
            }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new PaginationContainer(id, inv, container);
            }
        });
        return 0;
    }
    private static int cb_presetReward(ServerPlayer player) {
        RewardBoxTools.presetReward(player.serverLevel());
        LZCommonForgeApi.sendSystemMessage(player, "奖励库存已填充预设物品！", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        return 0;
    }

    private static int cb_cleanReward(ServerPlayer player) {
        RewardBoxTools.cleanReward();
        LZCommonForgeApi.sendSystemMessage(player, "奖励库存已清空！", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        return 0;
    }

    private static int cb_addRecord(CommandSourceStack src, String targetName, int year, int month, int day) throws CommandSyntaxException {
        month = Math.min(Math.max(month, 1), 12);
        day = Math.min(Math.max(day, 1), 31);
        String msg = String.format("记录：%s : %d-%d-%d",targetName,year,month,day);
        if(src.isPlayer()){
            ServerPlayer player = src.getPlayerOrException();
            LZCommonForgeApi.sendCenterSystemMessage(player,msg,LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        }
        PlayerCommentTools.addPlayerRecord(targetName, year, month, day);
        return 0;
    }
}
