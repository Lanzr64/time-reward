package net.lanzr.time_reward.api;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class LZCommonForgeApi {
    public static enum MsgTypes {
        NORMAL(ChatFormatting.YELLOW),
        ALERT(ChatFormatting.RED),
        OTHER(ChatFormatting.AQUA);
        public final ChatFormatting FORMAT;
        MsgTypes(ChatFormatting format) {
            this.FORMAT = format;
        }
        public ChatFormatting getmFmt() {
            return FORMAT;
        }
    }
    public static void sendSystemMessage(ServerPlayer player, String message, ChatFormatting format) {
        player.sendSystemMessage(Component.literal(message).withStyle(format), false);
    }
    public static void sendCenterSystemMessage(ServerPlayer player, String message, ChatFormatting format) {
        player.sendSystemMessage(Component.literal(message).withStyle(format), true);
    }

    public static int giveItem(ItemStack itemstack, ServerPlayer serverplayer) throws CommandSyntaxException {
        int i = itemstack.getMaxStackSize();
        int num = itemstack.getCount();
        int j = i * 100;
        if (num > j) {
//            ctx.sendFailure(Component.translatable("commands.give.failed.toomanyitems", j, item.createItemStack(num, false).getDisplayName()));
            return 0;
        } else {
            int k = num;

            while(k > 0) {
                int l = Math.min(i, k);
                k -= l;
                boolean flag = serverplayer.getInventory().add(itemstack);
                if (flag && itemstack.isEmpty()) {
                    itemstack.setCount(1);
                    ItemEntity itementity1 = serverplayer.drop(itemstack, false);
                    if (itementity1 != null) {
                        itementity1.makeFakeItem();
                    }

                    serverplayer.level().playSound((Player)null, serverplayer.getX(), serverplayer.getY(), serverplayer.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, ((serverplayer.getRandom().nextFloat() - serverplayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
                    serverplayer.containerMenu.broadcastChanges();
                } else {
                    ItemEntity itementity = serverplayer.drop(itemstack, false);
                    if (itementity != null) {
                        itementity.setNoPickUpDelay();
                        itementity.setTarget(serverplayer.getUUID());
                    }
                }
            }


            return 1;
        }
    }
}
