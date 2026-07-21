package net.lanzr.time_reward.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;

/**
 * 一个自定义槽位，根据{@link IntSupplier}提供的滚动偏移量，
 * 将固定的显示索引动态映射到容器索引。
 *
 * <p>显示网格以{@code slotsPerRow}列 × 动态行布局。
 * 滚动偏移量（行）决定底层容器的哪个部分可见。
 * 当偏移量变化时，每个槽位会透明地重新计算其底层索引。</p>
 *
 * <p>模式遵循{@code PaginationContainer.DynamicPageSlot}。</p>
 */
public class DynamicScrollSlot extends Slot {

    private final int displayIndex;
    private final int slotsPerRow;
    private final IntSupplier scrollOffsetSupplier;
    private final Container storageContainer;

    /**
     * @param container            用于读写物品的底层{@link Container}
     * @param displayIndex          该槽位在可见网格中的从0开始的索引
     * @param slotsPerRow          网格的列数（例如12）
     * @param scrollOffsetSupplier  提供当前滚动行偏移量的供应器
     * @param x                    槽位的屏幕x坐标
     * @param y                    槽位的屏幕y坐标
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
     * 基于显示位置和当前滚动偏移量计算在底层容器中的实际索引。
     */
    private int getActualIndex() {
        int row = displayIndex / slotsPerRow + scrollOffsetSupplier.getAsInt();
        int col = displayIndex % slotsPerRow;
        return row * slotsPerRow + col;
    }

    /**
     * @return 如果计算出的实际索引在底层容器的范围内则返回{@code true}
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
