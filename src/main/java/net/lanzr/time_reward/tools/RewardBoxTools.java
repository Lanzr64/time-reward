package net.lanzr.time_reward.tools;

import io.netty.channel.CoalescingBufferQueue;
import net.lanzr.time_reward.container.PaginationContainer;
import net.lanzr.time_reward.save.LZSavedData;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public class RewardBoxTools {
    private static final int ITEM_SLOTS_PER_PAGE = PaginationContainer.ITEM_SLOTS_PER_PAGE;
    private static final int TOTAL_SLOTS = LZSavedData.getRewardBox().getContainerSize();
    private static final int MAX_PAGES = TOTAL_SLOTS / ITEM_SLOTS_PER_PAGE;
    private static final int LAST_PAGE_SLOTS = TOTAL_SLOTS % ITEM_SLOTS_PER_PAGE;
    private static int fillItems(int offset, int size, ItemStack itemStack) {
        SimpleContainer container = LZSavedData.getRewardBox();
        for (int i = 0; i < size; i++) {
            container.setItem(offset + i, itemStack.copy());
        }
        container.setChanged();
        return size;
    }

    public static int presetReward(ServerLevel level) {
        RegistryAccess registryAccess = level.registryAccess();
        int offset = 0;
        
        // 1
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, new ItemStack(Items.COAL, 64));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, new ItemStack(Items.COOKED_CHICKEN, 16));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, new ItemStack(Items.LEATHER, 16));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE, new ItemStack(Items.IRON_INGOT, 16));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE, new ItemStack(Items.DIAMOND, 16));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE, new ItemStack(Items.NETHERITE_INGOT, 1));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, new ItemStack(Items.DIAMOND_PICKAXE, 1));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, new ItemStack(Items.DIAMOND_CHESTPLATE, 1));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, new ItemStack(Items.DIAMOND_HOE, 1));
        // 6
        ItemStack mendingBook = new ItemStack(Items.ENCHANTED_BOOK);
        Holder<Enchantment> mending = registryAccess.registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.MENDING).orElseThrow();
        mendingBook.enchant(mending, 1);
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, mendingBook);
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, mendingBook);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        offset += fillItems(offset,ITEM_SLOTS_PER_PAGE/3, pickaxe);
        // 7
        int currentPage = offset / ITEM_SLOTS_PER_PAGE;
        for (int i = currentPage; i < MAX_PAGES; i++) {
            offset += fillItems(offset,ITEM_SLOTS_PER_PAGE, new ItemStack(Items.GOLD_INGOT, 64));
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
