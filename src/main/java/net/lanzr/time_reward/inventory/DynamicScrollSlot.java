package net.lanzr.time_reward.inventory;

import java.util.function.IntSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class DynamicScrollSlot
extends Slot {
    private final int displayIndex;
    private final int slotsPerRow;
    private final IntSupplier scrollOffsetSupplier;
    private final Container storageContainer;

    public DynamicScrollSlot(Container container, int displayIndex, int slotsPerRow, IntSupplier scrollOffsetSupplier, int x, int y) {
        super((Container)new SimpleContainer(1), 0, x, y);
        this.storageContainer = container;
        this.displayIndex = displayIndex;
        this.slotsPerRow = slotsPerRow;
        this.scrollOffsetSupplier = scrollOffsetSupplier;
    }

    private int getActualIndex() {
        int row = this.displayIndex / this.slotsPerRow + this.scrollOffsetSupplier.getAsInt();
        int col = this.displayIndex % this.slotsPerRow;
        return row * this.slotsPerRow + col;
    }

    private boolean isInRange() {
        return this.getActualIndex() < this.storageContainer.getContainerSize();
    }

    @NotNull
    public ItemStack getItem() {
        if (this.isInRange()) {
            return this.storageContainer.getItem(this.getActualIndex());
        }
        return ItemStack.EMPTY;
    }

    public void set(@NotNull ItemStack stack) {
        if (this.isInRange()) {
            this.storageContainer.setItem(this.getActualIndex(), stack);
            super.setChanged();
        }
    }

    @NotNull
    public ItemStack remove(int amount) {
        if (this.isInRange()) {
            return this.storageContainer.removeItem(this.getActualIndex(), amount);
        }
        return ItemStack.EMPTY;
    }

    public boolean mayPlace(@NotNull ItemStack stack) {
        return this.isInRange();
    }

    public boolean mayPickup(@NotNull Player player) {
        return this.isInRange();
    }

    public boolean hasItem() {
        if (this.isInRange()) {
            return !this.storageContainer.getItem(this.getActualIndex()).isEmpty();
        }
        return false;
    }
}
