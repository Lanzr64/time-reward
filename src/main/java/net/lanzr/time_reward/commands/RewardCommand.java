package net.lanzr.time_reward.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.lanzr.time_reward.api.*;
import net.lanzr.time_reward.inventory.LZMenu;
import net.lanzr.time_reward.inventory.RewardScreen;
import net.lanzr.time_reward.inventory.playerRewardContainer;
import net.lanzr.time_reward.save.LZSavedData;
import net.lanzr.time_reward.save.PlayerSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

public class RewardCommand {
    public static void register(RegisterCommandsEvent event) {

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        final LiteralArgumentBuilder<CommandSourceStack> literalargumentBuilder =
                Commands.literal("tyj-reward");

        literalargumentBuilder.then(Commands.literal("get").executes(ctx -> cb_getreward(ctx.getSource().getPlayer())).requires(ctx -> ctx.hasPermission(0)))
            .then(Commands.literal("set").executes(ctx -> cb_setReward(ctx.getSource().getPlayer())).requires(ctx -> ctx.hasPermission(4)))
            .then(Commands.literal("enable").executes(ctx -> cb_rewardSetDone(ctx.getSource().getPlayer())).requires(ctx -> ctx.hasPermission(4)))
            .then(Commands.literal("reset").executes(ctx -> cb_rewardClear(ctx.getSource().getPlayer())).requires(ctx -> ctx.hasPermission(4)));

        dispatcher.register(literalargumentBuilder);
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
            int rewardLevel = PlayerCommentTools.getPlayerComment(player.getName().getString(),commentInfo);
            if(rewardLevel == -1) {
                LZCommonForgeApi.sendSystemMessage(player,"你还没有 MCMOD 评论记录哦，请去 https://play.mcmod.cn/sv20187752.html 评论后联系服主", LZCommonForgeApi.MsgTypes.ALERT.getmFmt());
                return 0;
            }
            // 判断玩家是否领取过
            RewardTag rewardTag = new RewardTag(player);
            if(rewardTag.getFlag()) {
                // 已经获得过
                LZCommonForgeApi.sendSystemMessage(player, String.format("记录时间： %s，你现在的奖励等级是 : %d 级，距离下个奖励等级还有 %d 天",
                        commentInfo.markTime,
                        commentInfo.level,
                        commentInfo.nextLevelRemainDays), LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
                LZCommonForgeApi.sendSystemMessage(player,"have fun!", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
                LZCommonForgeApi.sendSystemMessage(player,"你已经领取过了！", LZCommonForgeApi.MsgTypes.ALERT.getmFmt());
                return 0;
            }
            //  直接推给玩家
            for(int i = 0; i < rewardLevel+1; i++) {
                ItemStack item = patternContain.getItem(i).copy();
                if(item.getItem() != Items.AIR) {
                    LZCommonForgeApi.giveItem(item, player);
                }
//                player.addItem(patternContain.getItem(i).copy());
//                player.drop(patternContain.getItem(i).copy(), false);
            }

            rewardTag.setFlag(true);
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
        rewardTag.setFlag(false);
        LZCommonForgeApi.sendSystemMessage(target,"领取记录已经被清除", LZCommonForgeApi.MsgTypes.OTHER.getmFmt());
        return 0;
    }
    private static int cb_setReward(ServerPlayer player) {
        System.out.println("setReward");
        SimpleContainer container = LZSavedData.getRewardBox();
        LZMenu.openMenu(player, container);
        return 0;
    }
    private static int cb_tst(ServerPlayer player) {
        CommentInfo commentInfo = new CommentInfo();
        PlayerCommentTools.getPlayerComment(player.getName().getString(),commentInfo);
        return 0;
    }

}
