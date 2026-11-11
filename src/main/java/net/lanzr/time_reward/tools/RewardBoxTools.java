package net.lanzr.time_reward.tools;

import net.lanzr.time_reward.save.LZSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;

public class RewardBoxTools {
    private static final int ITEM_SLOTS_PER_PAGE = 45;
    private static final int TOTAL_SLOTS = LZSavedData.getRewardBox().getContainerSize();
    private static final int MAX_PAGES = TOTAL_SLOTS / 45;
    private static final int LAST_PAGE_SLOTS = TOTAL_SLOTS % 45;

    private static int fillItems(int offset, int size, ItemStack itemStack) {
        SimpleContainer container = LZSavedData.getRewardBox();
        for (int i = 0; i < size; ++i) {
            container.setItem(offset + i, itemStack.copy());
        }
        container.setChanged();
        return size;
    }

    public static int presetReward(ServerLevel level) {
        int currentPage;
        int offset = 0;
        offset += RewardBoxTools.fillItems(offset, 15, new ItemStack((ItemLike)Items.COAL, 64));
        offset += RewardBoxTools.fillItems(offset, 15, new ItemStack((ItemLike)Items.COOKED_CHICKEN, 16));
        offset += RewardBoxTools.fillItems(offset, 15, new ItemStack((ItemLike)Items.LEATHER, 16));
        offset += RewardBoxTools.fillItems(offset, 45, new ItemStack((ItemLike)Items.IRON_INGOT, 16));
        offset += RewardBoxTools.fillItems(offset, 45, new ItemStack((ItemLike)Items.DIAMOND, 16));
        offset += RewardBoxTools.fillItems(offset, 45, new ItemStack((ItemLike)Items.NETHERITE_INGOT, 1));
        offset += RewardBoxTools.fillItems(offset, 15, new ItemStack((ItemLike)Items.DIAMOND_PICKAXE, 1));
        offset += RewardBoxTools.fillItems(offset, 15, new ItemStack((ItemLike)Items.DIAMOND_CHESTPLATE, 1));
        offset += RewardBoxTools.fillItems(offset, 15, new ItemStack((ItemLike)Items.DIAMOND_HOE, 1));
        ItemStack mendingBook = new ItemStack((ItemLike)Items.ENCHANTED_BOOK);
        mendingBook.enchant(Enchantments.MENDING, 1);
        offset += RewardBoxTools.fillItems(offset, 15, mendingBook);
        offset += RewardBoxTools.fillItems(offset, 15, mendingBook);
        ItemStack pickaxe = new ItemStack((ItemLike)Items.DIAMOND_PICKAXE);
        pickaxe.getOrCreateTag().putBoolean("Unbreakable", true);
        offset += RewardBoxTools.fillItems(offset, 15, pickaxe);
        for (int i = currentPage = offset / 45; i < MAX_PAGES; ++i) {
            offset += RewardBoxTools.fillItems(offset, 45, new ItemStack((ItemLike)Items.GOLD_INGOT, 64));
        }
        return 0;
    }

    public static int cleanReward() {
        SimpleContainer container = LZSavedData.getRewardBox();
        container.clearContent();
        container.setChanged();
        return 0;
    }
}
