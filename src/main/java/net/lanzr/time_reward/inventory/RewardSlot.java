package net.lanzr.time_reward.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

public class RewardSlot extends Slot {

    public RewardSlot(Container container, int slotIndex, int xPos, int yPos) {
        super(container, slotIndex, xPos, yPos);
    }
    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.isEmpty() || stack.getItem() == Items.DIRT;
    }
}
