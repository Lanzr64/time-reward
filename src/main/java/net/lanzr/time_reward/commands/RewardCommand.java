package net.lanzr.time_reward.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sun.jdi.connect.Connector;
import net.lanzr.time_reward.api.*;
import net.lanzr.time_reward.inventory.LZMenu;
import net.lanzr.time_reward.inventory.playerRewardContainer;
import net.lanzr.time_reward.save.LZSavedData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.command.EnumArgument;
import org.jetbrains.annotations.Nullable;

public class RewardCommand {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tyj-reward").executes(ctx -> cb_getreward(ctx.getSource().getPlayer()))
        );
        event.getDispatcher().register(
                Commands.literal("tyj-rewardSetDone").executes(ctx -> cb_rewardSetDone(ctx.getSource().getPlayer()))
                        .requires(ctx-> ctx.hasPermission(4))
        );
        event.getDispatcher().register(
                Commands.literal("tyj-rewardClear")
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(ctx -> cb_rewardClear(EntityArgument.getPlayer(ctx,"target"))))
                        .requires(ctx-> ctx.hasPermission(4))
        );
        event.getDispatcher().register(
                Commands.literal("tyj-setReward")
                        .executes(ctx -> cb_setReward(ctx.getSource().getPlayer()))
                        .requires(ctx-> ctx.hasPermission(4))
        );
        event.getDispatcher().register(
                Commands.literal("tyj-rewardAddRecord")
                        .then(Commands.argument("target", StringArgumentType.string())
                            .then(Commands.argument("year", IntegerArgumentType.integer())
                                    .then(Commands.argument("month", IntegerArgumentType.integer())
                                            .then(Commands.argument("day", IntegerArgumentType.integer())
                            .executes(ctx -> cb_addRecord(
                                    StringArgumentType.getString(ctx,"target"),
                                    IntegerArgumentType.getInteger(ctx,"year"),
                                    IntegerArgumentType.getInteger(ctx,"month"),
                                    IntegerArgumentType.getInteger(ctx,"day")
                                    )
                            )))))
                        .requires(ctx-> ctx.hasPermission(4))
        );
        event.getDispatcher().register(
                Commands.literal("tyj-tst").requires(ctx -> ctx.hasPermission(4)).
                        executes(ctx -> cb_tst(ctx.getSource().getPlayer()))
        );
    }

    private static int cb_getreward(@Nullable ServerPlayer player) {
        SimpleContainer patternContain = LZSavedData.getRewardBox();
        try {
            playerRewardContainer container = new playerRewardContainer(27);
            // 判断奖励是否设置完成
            if(!LZSavedData.SET_DONE){
                LZCommonForgeApi.sendSystemMessage(player,"奖励还没有设置好！", LZCommonForgeApi.MsgTypes.ALERT.getmFmt());
                return 0;
            }
            // 判断玩家是否有领取权限
            CommentInfo commentInfo = new CommentInfo();
            PlayerCommentTools.getPlayerComment(player.getName().getString(),commentInfo);
            int rewardLevel = commentInfo.level;
            if(rewardLevel == -1) {
                LZCommonForgeApi.sendSystemMessage(player,"你还没有 MCMOD 评论记录哦，请去 https://play.mcmod.cn/sv20187752.html 评论后联系服主", LZCommonForgeApi.MsgTypes.ALERT.getmFmt());
                return 0;
            }
            // 判断玩家是否领取过
            RewardTag rewardTag = new RewardTag(player);
            int playerLevel = rewardTag.getLevel();
            if(playerLevel >= rewardLevel) {
                // 当前等级不大于领取等级
                LZCommonForgeApi.sendSystemMessage(player, String.format("记录时间： %s，你现在的奖励等级是 : %d 级，距离下个奖励等级还有 %d 天",
                        commentInfo.markTime,
                        commentInfo.level,
                        commentInfo.nextLevelRemainDays), LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
                LZCommonForgeApi.sendSystemMessage(player,"have fun!", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
                LZCommonForgeApi.sendSystemMessage(player,"你已经领取过了！", LZCommonForgeApi.MsgTypes.ALERT.getmFmt());
                return 0;
            }
            //  直接推给玩家，并跳过已经领取的等级
            for(int i = 0; i < rewardLevel+1; i++) {
                ItemStack item = patternContain.getItem(i).copy();
                if(item.getItem() != Items.AIR) {
                    if(playerLevel < i) {
                        LZCommonForgeApi.giveItem(item, player);
                    }
                }
//                player.addItem(patternContain.getItem(i).copy());
//                player.drop(patternContain.getItem(i).copy(), false);
            }

            rewardTag.setLevel(rewardLevel);

            LZCommonForgeApi.sendSystemMessage(player, String.format("记录时间： %s，你现在的奖励等级是 : %d 级，距离下个奖励等级还有 %d 天",
                    commentInfo.markTime,
                    commentInfo.level,
                    commentInfo.nextLevelRemainDays), LZCommonForgeApi.MsgTypes.OTHER.getmFmt());

            LZCommonForgeApi.sendSystemMessage(player,"have fun!", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        }  catch (Exception e) {
            e.printStackTrace();
        }

        return 1;
    }
    private static int cb_rewardSetDone(@Nullable ServerPlayer player) {
        LZSavedData.setDone(true);
        return 0;
    }
    private static int cb_rewardClear(ServerPlayer target) {
        RewardTag rewardTag = new RewardTag(target);
        rewardTag.setLevel(-1);
        LZCommonForgeApi.sendSystemMessage(target,"领取记录已经被清除", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        return 0;
    }
    private static int cb_setReward(ServerPlayer player) {
        SimpleContainer container = LZSavedData.getRewardBox();
        LZMenu.openMenu(player, container);
        return 0;
    }
    private static int cb_addRecord(String player, int year, int month, int day) {
        PlayerCommentTools.addPlayerRecord(player, year, month, day);
        return 0;
    }
    private static int cb_tst(ServerPlayer player) {
        CommentInfo commentInfo = new CommentInfo();
        PlayerCommentTools.getPlayerComment(player.getName().getString(),commentInfo);
        return 0;
    }

}
