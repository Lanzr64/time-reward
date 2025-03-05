package net.lanzr.time_reward.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.jetbrains.annotations.NotNull;


public class LZMenu {
    static final String PATH = "lzFiles";
    static final String COMMENT_RECORD_PATH = PATH + "/comment-record.json";
    static public void openMenu(ServerPlayer player, Container container) {
        player.openMenu(new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return Component.literal("评论奖励");
            }
            @Override
            public  AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player1) {
                return ChestMenu.threeRows(id,inventory, container);
            }
        });
    }
}
