package net.lanzr.time_reward.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.inventory.DynamicScrollSlot;
import net.lanzr.time_reward.network.BackpackStatePayload;
import net.lanzr.time_reward.network.ModNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BackpackContainer
extends AbstractContainerMenu {
    public static final int COLS = 12;
    public static final int MAX_VISIBLE_ROWS = 12;
    public static final int TOTAL_DISPLAY_SLOTS = 144;
    public static final int PLAYER_INV_START = 144;
    public static final int HOTBAR_START = 171;
    public static final int TOTAL_SLOTS = 180;
    private final Container storageContainer;
    private final Player player;
    private Runnable saveCallback;
    private int scrollOffset;
    private int clientVisibleRows;
    private int lastOccupiedRow;
    private boolean lastOccupiedDirty;
    private int lastSentLastOccupiedRow;
    private int recomputeCallCount;

    public BackpackContainer(int id, Inventory playerInventory, Container container, int scrollOffset) {
        super(MenuType.GENERIC_9x6, id);
        this.saveCallback = () -> {};
        this.clientVisibleRows = 12;
        this.lastOccupiedDirty = true;
        this.recomputeCallCount = 0;
        this.player = playerInventory.player;
        this.storageContainer = container;
        this.scrollOffset = scrollOffset;
        this.setupSlots(playerInventory);
        this.lastSentLastOccupiedRow = this.lastOccupiedRow = this.recomputeLastOccupiedRow();
        this.lastOccupiedDirty = false;
    }

    public BackpackContainer(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(MenuType.GENERIC_9x6, id);
        this.saveCallback = () -> {};
        this.clientVisibleRows = 12;
        this.lastOccupiedDirty = true;
        this.recomputeCallCount = 0;
        this.player = playerInventory.player;
        int containerSize = buf.readInt();
        this.storageContainer = new SimpleContainer(containerSize);
        this.scrollOffset = buf.readInt();
        this.lastOccupiedRow = buf.readInt();
        this.setupSlots(playerInventory);
    }

    private void setupSlots(Inventory playerInventory) {
        int col;
        for (int row = 0; row < 12; ++row) {
            for (int col2 = 0; col2 < 12; ++col2) {
                int displayIndex = row * 12 + col2;
                this.addSlot(new DynamicScrollSlot(this.storageContainer, displayIndex, 12, this::getScrollOffset, 8 + col2 * 18, 18 + row * 18));
            }
        }
        int playerInvX = 38;
        int playerInvY = 248;
        for (int row = 0; row < 3; ++row) {
            for (col = 0; col < 9; ++col) {
                this.addSlot(new Slot((Container)playerInventory, 9 + col + row * 9, playerInvX + col * 18, playerInvY + row * 18));
            }
        }
        int hotbarY = playerInvY + 54 + 4;
        for (col = 0; col < 9; ++col) {
            this.addSlot(new Slot((Container)playerInventory, col, playerInvX + col * 18, hotbarY));
        }
    }

    public int getScrollOffset() {
        return this.scrollOffset;
    }

    public int getLastOccupiedRow() {
        return this.lastOccupiedRow;
    }

    public int getContainerSize() {
        return this.storageContainer.getContainerSize();
    }

    public void setLastOccupiedRow(int row) {
        this.lastOccupiedRow = row;
    }

    public int recomputeLastOccupiedRow() {
        int containerSize = this.storageContainer.getContainerSize();
        for (int i = containerSize - 1; i >= 0; --i) {
            if (this.storageContainer.getItem(i).isEmpty()) continue;
            this.lastOccupiedRow = i / 12;
            return this.lastOccupiedRow;
        }
        this.lastOccupiedRow = -1;
        return -1;
    }

    public void setClientScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, Math.min(offset, this.getClientMaxScrollOffset()));
    }

    public void setClientVisibleRows(int rows) {
        this.clientVisibleRows = Math.max(1, Math.min(rows, 12));
    }

    private int getClientMaxScrollOffset() {
        int totalRows = (this.storageContainer.getContainerSize() + 12 - 1) / 12;
        return Math.max(0, totalRows - this.clientVisibleRows);
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = this.clampScrollOffset(offset);
        this.lastOccupiedDirty = true;
        this.broadcastChanges();
    }

    public void onScroll(int delta) {
        this.setScrollOffset(this.scrollOffset + delta);
    }

    public int getMaxScrollOffset() {
        int totalRows = (this.storageContainer.getContainerSize() + 12 - 1) / 12;
        return Math.max(0, totalRows - this.clientVisibleRows);
    }

    private int clampScrollOffset(int offset) {
        return Math.max(0, Math.min(offset, this.getMaxScrollOffset()));
    }

    public void sort(SortType type) {
        this.lastOccupiedDirty = true;
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        for (int i = 0; i < this.storageContainer.getContainerSize(); ++i) {
            items.add(this.storageContainer.getItem(i));
        }
        ArrayList<ItemStack> nonEmpty = new ArrayList<ItemStack>();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            nonEmpty.add(stack);
        }
        Comparator<ItemStack> comparator = BackpackContainer.buildSortComparator(type);
        nonEmpty.sort(comparator);
        int idx = 0;
        for (ItemStack stack : nonEmpty) {
            this.storageContainer.setItem(idx++, stack.copy());
        }
        while (idx < this.storageContainer.getContainerSize()) {
            this.storageContainer.setItem(idx, ItemStack.EMPTY);
            ++idx;
        }
        this.setScrollOffset(0);
        int newRow = this.recomputeLastOccupiedRow();
        TimeReward.LOGGER.info("[BackpackContainer] recompute result post-sort lastOccupiedRow={}", (Object)newRow);
        if (newRow != this.lastSentLastOccupiedRow) {
            this.lastSentLastOccupiedRow = newRow;
            this.sendBackpackStateToPlayer(newRow);
        }
        this.lastOccupiedDirty = false;
    }

    private static Comparator<ItemStack> buildSortComparator(SortType type) {
        switch (type) {
            case NAME: {
                return Comparator.comparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            }
            case COUNT: {
                return Comparator.comparingInt(ItemStack::getCount).reversed().thenComparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            }
            case MOD: {
                return Comparator.comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()).thenComparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            }
        }
        return (a, b) -> 0;
    }

    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = (Slot)this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();
        if (slotIndex < 144 ? !this.moveItemStackTo(stackInSlot, 144, 180, true) : !this.moveItemStackTo(stackInSlot, 0, 144, false)) {
            return ItemStack.EMPTY;
        }
        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        this.lastOccupiedDirty = true;
        return result;
    }

    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.lastOccupiedDirty) {
            int row = this.recomputeLastOccupiedRow();
            ++this.recomputeCallCount;
            TimeReward.LOGGER.info("[BackpackContainer] recompute-call-count n={} row={}", (Object)this.recomputeCallCount, (Object)row);
            if (row != this.lastSentLastOccupiedRow) {
                this.lastSentLastOccupiedRow = row;
                this.sendBackpackStateToPlayer(row);
            }
            this.lastOccupiedDirty = false;
        }
    }

    private void sendBackpackStateToPlayer(int row) {
        Player player = this.player;
        if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            ModNetwork.sendToPlayer(sp, new BackpackStatePayload(this.containerId, row));
        }
    }

    public void removed(Player player) {
        super.removed(player);
        this.saveCallback.run();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }

    public Container getStorageContainer() {
        return this.storageContainer;
    }

    public static enum SortType {
        NAME,
        COUNT,
        MOD;

    }
}
