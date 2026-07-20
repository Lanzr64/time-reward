package net.lanzr.time_reward.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;

/**
 * A custom Slot that dynamically maps a fixed display index to a container index
 * based on a scroll offset supplied by an {@link IntSupplier}.
 *
 * <p>The display grid is laid out as {@code slotsPerRow} columns &#215; dynamic rows.
 * The scroll offset (in rows) shifts which portion of the backing container is visible.
 * When the offset changes, every slot transparently recalculates its backing index.</p>
 *
 * <p>Pattern follows {@code PaginationContainer.DynamicPageSlot}.</p>
 */
public class DynamicScrollSlot extends Slot {

    private final int displayIndex;
    private final int slotsPerRow;
    private final IntSupplier scrollOffsetSupplier;
    private final Container storageContainer;

    /**
     * @param container            the backing {@link Container} to read/write items from
     * @param displayIndex         0-based index of this slot in the visible grid
     * @param slotsPerRow          number of columns in the grid (e.g. 12)
     * @param scrollOffsetSupplier supplies the current scroll row offset
     * @param x                    screen x position of the slot
     * @param y                    screen y position of the slot
     */
    public DynamicScrollSlot(Container container, int displayIndex, int slotsPerRow,
                             IntSupplier scrollOffsetSupplier, int x, int y) {
        super(new SimpleContainer(1), 0, x, y);
        this.storageContainer = container;
        this.displayIndex = displayIndex;
        this.slotsPerRow = slotsPerRow;
        this.scrollOffsetSupplier = scrollOffsetSupplier;
    }

    /**
     * Computes the actual index in the backing container based on the display
     * position and the current scroll offset.
     */
    private int getActualIndex() {
        int row = displayIndex / slotsPerRow + scrollOffsetSupplier.getAsInt();
        int col = displayIndex % slotsPerRow;
        return row * slotsPerRow + col;
    }

    /**
     * @return {@code true} if the computed actual index is within the bounds
     *         of the backing container
     */
    private boolean isInRange() {
        return getActualIndex() < storageContainer.getContainerSize();
    }

    @Override
    @NotNull
    public ItemStack getItem() {
        if (isInRange()) {
            return storageContainer.getItem(getActualIndex());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        if (isInRange()) {
            storageContainer.setItem(getActualIndex(), stack);
            super.setChanged();
        }
    }

    @Override
    @NotNull
    public ItemStack remove(int amount) {
        if (isInRange()) {
            return storageContainer.removeItem(getActualIndex(), amount);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return isInRange();
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return isInRange();
    }

    @Override
    public boolean hasItem() {
        if (isInRange()) {
            return !storageContainer.getItem(getActualIndex()).isEmpty();
        }
        return false;
    }
}
