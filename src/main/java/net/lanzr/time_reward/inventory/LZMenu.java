package net.lanzr.time_reward.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;

import java.io.FileReader;
import java.io.Reader;
import java.util.Map;
import java.util.Set;

public class LZMenu {
    static final String PATH = "lzFiles";
    static final String COMMENT_RECORD_PATH = PATH + "/comment-record.json";
    static public void openMenu(ServerPlayer player, Container container) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("评论奖励");
            }
            @Override
            public  AbstractContainerMenu createMenu(int id, Inventory inventory, Player player1) {
                return ChestMenu.threeRows(id,inventory, container);
            }
        });
    }
//    static public void openMenutst(ServerPlayer player, Container container) {
//        player.openMenu(new MenuProvider() {
//            @Override
//            public Component getDisplayName() {
//                return Component.literal("评论奖励");
//            }
//            @Override
//            public  AbstractContainerMenu createMenu(int id, Inventory inventory, Player player1) {
//                return RewardMenu_depreacted.createTSTContainer(id,inventory, container);
////                return ChestMenu.threeRows(id,inventory, container);
//            }
//        });
//    }
    static public void tst() {

    }

    // 从json中读取参数
    static public void tst_readfJson() {
        JsonParser parser = new JsonParser();
        try (Reader reader = new FileReader(COMMENT_RECORD_PATH) ) {
            JsonElement je = parser.parse(reader);
            JsonObject jo = je.getAsJsonObject();
            Set<Map.Entry<String,JsonElement>> entrySet = jo.entrySet();

            for (Map.Entry<String,JsonElement> entry : entrySet) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                System.out.println(key);
                System.out.println(value.getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
