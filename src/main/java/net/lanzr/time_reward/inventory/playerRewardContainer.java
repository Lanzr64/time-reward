package net.lanzr.time_reward.inventory;

import com.google.common.collect.Lists;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

public class playerRewardContainer implements Container, StackedContentsCompatible {
    private final int size;
    private final NonNullList<ItemStack> items;
    @Nullable
    private List<ContainerListener> listeners;

    public playerRewardContainer(int contain_size) {
        this.size = contain_size;
        this.items = NonNullList.withSize(contain_size, ItemStack.EMPTY);
    }

    public playerRewardContainer(ItemStack... items) {
        this.size = items.length;
        this.items = NonNullList.of(ItemStack.EMPTY, items);
    }


    public void addListener(ContainerListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    public void removeListener(ContainerListener listener) {
        if (this.listeners != null) {
            this.listeners.remove(listener);
        }

    }

    @Override
    public void fillStackedContents(StackedContents contents) {
        for(ItemStack itemstack : this.items) {
            contents.accountStack(itemstack);
        }
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for(ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < this.items.size() ? this.items.get(index) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int index, int num) {
        ItemStack itemstack = ContainerHelper.removeItem(this.items, index, num);
        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack itemstack = this.items.get(index);
        if (itemstack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.items.set(index, ItemStack.EMPTY);
            return itemstack;
        }
    }

    @Override
    public void setItem(int index, ItemStack item) {
        this.items.set(index, item);
        if (!item.isEmpty() && item.getCount() > this.getMaxStackSize()) {
            item.setCount(this.getMaxStackSize());
        }

        this.setChanged();
    }

    @Override
    public void setChanged() {
        if (this.listeners != null) {
            for(ContainerListener containerlistener : this.listeners) {
                containerlistener.containerChanged(this);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true ;
    }

    @Override
    public void startOpen(Player player) {
        Container.super.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        Container.super.stopOpen(player);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack item) {
        return false;
//        return Container.super.canPlaceItem(index, item);
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }
}
