package net.lanzr.time_reward.container;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

public class FixedSlot extends Slot {
    public FixedSlot(int x, int y) {
        super(new SimpleContainer(1), 0, x, y);
    }
    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

}
