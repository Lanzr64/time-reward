package net.lanzr.time_reward.container;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.Set;
import net.lanzr.time_reward.container.FixedSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;

public class PaginationContainer
extends AbstractContainerMenu {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int ROWS = 6;
    public static final int COLS = 9;
    public static final int DISPLAY_SLOTS = 54;
    public static final int ITEM_SLOTS_PER_PAGE = 45;
    public static final int NAV_PREV_CONTAINER_SLOT = 45;
    public static final int NAV_NEXT_CONTAINER_SLOT = 53;
    private static final int PLAYER_INV_START = 54;
    private static final int TOTAL_SLOTS = 90;
    private final Container container;
    private final boolean readOnly;
    private final Inventory playerInventory;
    private final int containerSize;
    private final int maxPages;
    private int currentPage = 1;
    private int quickcraftType = -1;
    private int quickcraftStatus;
    private final Set<Slot> quickcraftSlots = Sets.newHashSet();
    private ItemStack navItemPrev = new ItemStack((ItemLike)Items.ARROW);
    private ItemStack navItemNext = new ItemStack((ItemLike)Items.ARROW);
    private ItemStack blockItem = new ItemStack((ItemLike)Items.GLASS_PANE);
    private ItemStack indexItem;
    private final SimpleContainer slotContainer;
    private Runnable saveCallback;

    public PaginationContainer(int id, Inventory playerInventory, Container container) {
        this(id, playerInventory, container, false);
    }

    public PaginationContainer(int id, Inventory playerInventory, Container container, boolean readOnly) {
        super(MenuType.GENERIC_9x6, id);
        this.readOnly = readOnly;
        this.blockItem.setHoverName((Component)Component.literal((String)"Empty"));
        this.indexItem = new ItemStack((ItemLike)Items.MUSIC_DISC_BLOCKS);
        this.slotContainer = new SimpleContainer(90);
        this.saveCallback = null;
        this.container = container;
        this.playerInventory = playerInventory;
        this.containerSize = container.getContainerSize();
        this.maxPages = Math.max(1, (this.containerSize + 45 - 1) / 45);
        this.currentPage = 1;
        this.createDisplaySlots();
        this.loadCurrentPage();
    }

    private int getPageStartIndex() {
        return (this.currentPage - 1) * 45;
    }

    private int getPageEndIndex() {
        return Math.min(this.currentPage * 45, this.containerSize);
    }

    private boolean canMoveToPage(int page) {
        return page >= 1 && page <= this.maxPages;
    }

    private void loadCurrentPage() {
        if (this.currentPage == 1) {
            this.navItemPrev.setHoverName((Component)Component.literal((String)"\u00a77\u25c0 \u5df2\u5230\u6700\u524d"));
        } else {
            this.navItemPrev.setHoverName((Component)Component.literal((String)"\u00a7a\u25c0 \u4e0a\u4e00\u9875"));
        }
        if (this.currentPage == this.maxPages) {
            this.navItemNext.setHoverName((Component)Component.literal((String)"\u00a77\u5df2\u5230\u6700\u540e \u25b6"));
        } else {
            this.navItemNext.setHoverName((Component)Component.literal((String)"\u00a7a\u4e0b\u4e00\u9875 \u25b6"));
        }
        this.indexItem.setHoverName((Component)Component.literal((String)String.format("%d / %d", this.currentPage, this.maxPages)));
    }

    private void createDisplaySlots() {
        int col;
        int col2;
        int row;
        int navPosY = 108;
        for (row = 0; row < 5; ++row) {
            for (col2 = 0; col2 < 9; ++col2) {
                int displayIndex = row * 9 + col2;
                this.addSlot(new DynamicPageSlot(this.slotContainer, displayIndex, 8 + col2 * 18, 18 + row * 18));
            }
        }
        this.addSlot(new NavigationSlot(true, 8, 108));
        for (col = 1; col < 4; ++col) {
            this.addSlot(new BlockSlot(8 + col * 18, 108));
        }
        this.addSlot(new IndexSlot(80, 108));
        for (col = 5; col < 8; ++col) {
            this.addSlot(new BlockSlot(8 + col * 18, 108));
        }
        this.addSlot(new NavigationSlot(false, 152, 108));
        for (row = 0; row < 3; ++row) {
            for (col2 = 0; col2 < 9; ++col2) {
                this.addSlot(new Slot((Container)this.playerInventory, 9 + col2 + row * 9, 8 + col2 * 18, 140 + row * 18));
            }
        }
        for (col = 0; col < 9; ++col) {
            this.addSlot(new Slot((Container)this.playerInventory, col, 8 + col * 18, 198));
        }
    }

    public void removed(Player player) {
        super.removed(player);
        if (this.saveCallback != null) {
            this.saveCallback.run();
        }
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = (Slot)this.slots.get(slotId);
            if (slot instanceof NavigationSlot) {
                NavigationSlot navSlot = (NavigationSlot)slot;
                if (navSlot.isPrev && this.canMoveToPage(this.currentPage - 1)) {
                    this.prevPage();
                } else if (!navSlot.isPrev && this.canMoveToPage(this.currentPage + 1)) {
                    this.nextPage();
                }
                return;
            }
            if (this.readOnly) {
                return;
            }
            this.doClick(slotId, dragType, clickType, player);
            if (this.saveCallback != null && slotId < 54) {
                this.saveCallback.run();
            }
        } else if (slotId == -999 && !this.readOnly) {
            this.doClick(slotId, dragType, clickType, player);
        }
    }

    protected void resetQuickCraft() {
        this.quickcraftStatus = 0;
        this.quickcraftSlots.clear();
    }

    private void doClick(int pSlotId, int pButton, ClickType pClickType, Player pPlayer) {
        block38: {
            block49: {
                block45: {
                    ItemStack itemstack6;
                    ItemStack itemstack3;
                    Slot dynSlot;
                    Inventory inventory;
                    block48: {
                        block47: {
                            block46: {
                                block43: {
                                    ClickAction clickaction;
                                    Slot dynSlot2;
                                    block44: {
                                        block42: {
                                            block36: {
                                                block41: {
                                                    block40: {
                                                        block39: {
                                                            block37: {
                                                                inventory = pPlayer.getInventory();
                                                                if (pClickType != ClickType.QUICK_CRAFT) break block36;
                                                                int i = this.quickcraftStatus;
                                                                this.quickcraftStatus = PaginationContainer.getQuickcraftHeader((int)pButton);
                                                                if (i == 1 && this.quickcraftStatus == 2 || i == this.quickcraftStatus) break block37;
                                                                this.resetQuickCraft();
                                                                break block38;
                                                            }
                                                            if (!this.getCarried().isEmpty()) break block39;
                                                            this.resetQuickCraft();
                                                            break block38;
                                                        }
                                                        if (this.quickcraftStatus != 0) break block40;
                                                        this.quickcraftType = PaginationContainer.getQuickcraftType((int)pButton);
                                                        if (PaginationContainer.isValidQuickcraftType((int)this.quickcraftType, (Player)pPlayer)) {
                                                            this.quickcraftStatus = 1;
                                                            this.quickcraftSlots.clear();
                                                        } else {
                                                            this.resetQuickCraft();
                                                        }
                                                        break block38;
                                                    }
                                                    if (this.quickcraftStatus != 1) break block41;
                                                    ItemStack itemstack = this.getCarried();
                                                    Slot dynSlot3 = (Slot)this.slots.get(pSlotId);
                                                    if (!PaginationContainer.canItemQuickReplace((Slot)dynSlot3, (ItemStack)itemstack, (boolean)true) || !dynSlot3.mayPlace(itemstack) || this.quickcraftType != 2 && itemstack.getCount() <= this.quickcraftSlots.size() || !this.canDragTo(dynSlot3)) break block38;
                                                    this.quickcraftSlots.add(dynSlot3);
                                                    break block38;
                                                }
                                                if (this.quickcraftStatus == 2) {
                                                    if (!this.quickcraftSlots.isEmpty()) {
                                                        if (this.quickcraftSlots.size() == 1) {
                                                            int i1 = this.quickcraftSlots.iterator().next().index;
                                                            this.resetQuickCraft();
                                                            this.doClick(i1, this.quickcraftType, ClickType.PICKUP, pPlayer);
                                                            return;
                                                        }
                                                        ItemStack itemstack2 = this.getCarried().copy();
                                                        if (itemstack2.isEmpty()) {
                                                            this.resetQuickCraft();
                                                            return;
                                                        }
                                                        int k1 = this.getCarried().getCount();
                                                        for (Slot slot1 : this.quickcraftSlots) {
                                                            ItemStack itemstack1 = this.getCarried();
                                                            if (slot1 == null || !PaginationContainer.canItemQuickReplace((Slot)slot1, (ItemStack)itemstack1, (boolean)true) || !slot1.mayPlace(itemstack1) || this.quickcraftType != 2 && itemstack1.getCount() < this.quickcraftSlots.size() || !this.canDragTo(slot1)) continue;
                                                            int j = slot1.hasItem() ? slot1.getItem().getCount() : 0;
                                                            int k = Math.min(itemstack2.getMaxStackSize(), slot1.getMaxStackSize(itemstack2));
                                                            int l = Math.min(PaginationContainer.getQuickCraftPlaceCount(this.quickcraftSlots, (int)this.quickcraftType, (ItemStack)itemstack2) + j, k);
                                                            k1 -= l - j;
                                                            slot1.setByPlayer(itemstack2.copyWithCount(l));
                                                        }
                                                        itemstack2.setCount(k1);
                                                        this.setCarried(itemstack2);
                                                    }
                                                    this.resetQuickCraft();
                                                } else {
                                                    this.resetQuickCraft();
                                                }
                                                break block38;
                                            }
                                            if (this.quickcraftStatus == 0) break block42;
                                            this.resetQuickCraft();
                                            break block38;
                                        }
                                        if (pClickType != ClickType.PICKUP && pClickType != ClickType.QUICK_MOVE || pButton != 0 && pButton != 1) break block43;
                                        dynSlot2 = (Slot)this.slots.get(pSlotId);
                                        ClickAction clickAction = clickaction = pButton == 0 ? ClickAction.PRIMARY : ClickAction.SECONDARY;
                                        if (pSlotId != -999) break block44;
                                        if (this.getCarried().isEmpty()) break block38;
                                        if (clickaction == ClickAction.PRIMARY) {
                                            pPlayer.drop(this.getCarried(), true);
                                            this.setCarried(ItemStack.EMPTY);
                                        } else {
                                            pPlayer.drop(this.getCarried().split(1), true);
                                        }
                                        break block38;
                                    }
                                    if (pClickType == ClickType.QUICK_MOVE) {
                                        if (pSlotId < 0) {
                                            return;
                                        }
                                        if (!dynSlot2.mayPickup(pPlayer)) {
                                            return;
                                        }
                                        ItemStack itemstack8 = this.quickMoveStack(pPlayer, pSlotId);
                                        while (!itemstack8.isEmpty() && ItemStack.isSameItem((ItemStack)dynSlot2.getItem(), (ItemStack)itemstack8)) {
                                            itemstack8 = this.quickMoveStack(pPlayer, pSlotId);
                                        }
                                    } else {
                                        if (pSlotId < 0) {
                                            return;
                                        }
                                        ItemStack dynstack = dynSlot2.getItem();
                                        ItemStack carrystack = this.getCarried();
                                        pPlayer.updateTutorialInventoryAction(carrystack, dynSlot2.getItem(), clickaction);
                                        if (dynstack.isEmpty()) {
                                            if (!carrystack.isEmpty()) {
                                                int i3 = clickaction == ClickAction.PRIMARY ? carrystack.getCount() : 1;
                                                this.setCarried(dynSlot2.safeInsert(carrystack, i3));
                                            }
                                        } else if (dynSlot2.mayPickup(pPlayer)) {
                                            if (carrystack.isEmpty()) {
                                                int j3 = clickaction == ClickAction.PRIMARY ? dynstack.getCount() : (dynstack.getCount() + 1) / 2;
                                                Optional<ItemStack> optional1 = dynSlot2.tryRemove(j3, Integer.MAX_VALUE, pPlayer);
                                                optional1.ifPresent(p_150421_ -> {
                                                    this.setCarried((ItemStack)p_150421_);
                                                    dynSlot2.onTake(pPlayer, p_150421_);
                                                });
                                            } else if (dynSlot2.mayPlace(carrystack)) {
                                                if (ItemStack.isSameItemSameTags((ItemStack)dynstack, (ItemStack)carrystack)) {
                                                    int k3 = clickaction == ClickAction.PRIMARY ? carrystack.getCount() : 1;
                                                    this.setCarried(dynSlot2.safeInsert(carrystack, k3));
                                                } else if (carrystack.getCount() <= dynSlot2.getMaxStackSize(carrystack)) {
                                                    this.setCarried(dynstack);
                                                    dynSlot2.setByPlayer(carrystack);
                                                }
                                            } else if (ItemStack.isSameItemSameTags((ItemStack)dynstack, (ItemStack)carrystack)) {
                                                Optional<ItemStack> optional = dynSlot2.tryRemove(dynstack.getCount(), carrystack.getMaxStackSize() - carrystack.getCount(), pPlayer);
                                                optional.ifPresent(p_150428_ -> {
                                                    carrystack.grow(p_150428_.getCount());
                                                    dynSlot2.onTake(pPlayer, p_150428_);
                                                });
                                            }
                                        }
                                        dynSlot2.setChanged();
                                    }
                                    break block38;
                                }
                                if (pClickType != ClickType.SWAP) break block45;
                                dynSlot = (Slot)this.slots.get(pSlotId);
                                itemstack3 = inventory.getItem(pButton);
                                itemstack6 = dynSlot.getItem();
                                if (itemstack3.isEmpty() && itemstack6.isEmpty()) break block38;
                                if (!itemstack3.isEmpty()) break block46;
                                if (!dynSlot.mayPickup(pPlayer)) break block38;
                                inventory.setItem(pButton, itemstack6);
                                dynSlot.setByPlayer(ItemStack.EMPTY);
                                dynSlot.onTake(pPlayer, itemstack6);
                                break block38;
                            }
                            if (!itemstack6.isEmpty()) break block47;
                            if (!dynSlot.mayPlace(itemstack3)) break block38;
                            int i2 = dynSlot.getMaxStackSize(itemstack3);
                            if (itemstack3.getCount() > i2) {
                                dynSlot.setByPlayer(itemstack3.split(i2));
                            } else {
                                inventory.setItem(pButton, ItemStack.EMPTY);
                                dynSlot.setByPlayer(itemstack3);
                            }
                            break block38;
                        }
                        if (!dynSlot.mayPickup(pPlayer) || !dynSlot.mayPlace(itemstack3)) break block38;
                        int j2 = dynSlot.getMaxStackSize(itemstack3);
                        if (itemstack3.getCount() <= j2) break block48;
                        dynSlot.setByPlayer(itemstack3.split(j2));
                        dynSlot.onTake(pPlayer, itemstack6);
                        if (inventory.add(itemstack6)) break block38;
                        pPlayer.drop(itemstack6, true);
                        break block38;
                    }
                    inventory.setItem(pButton, itemstack6);
                    dynSlot.setByPlayer(itemstack3);
                    dynSlot.onTake(pPlayer, itemstack6);
                    break block38;
                }
                if (pClickType != ClickType.CLONE || !pPlayer.getAbilities().instabuild || !this.getCarried().isEmpty() || pSlotId < 0) break block49;
                Slot dynSlot = (Slot)this.slots.get(pSlotId);
                if (!dynSlot.hasItem()) break block38;
                ItemStack itemstack5 = dynSlot.getItem();
                this.setCarried(itemstack5.copyWithCount(itemstack5.getMaxStackSize()));
                break block38;
            }
            if (pClickType == ClickType.THROW && this.getCarried().isEmpty() && pSlotId >= 0) {
                Slot dynSlot = (Slot)this.slots.get(pSlotId);
                int j1 = pButton == 0 ? 1 : dynSlot.getItem().getCount();
                ItemStack itemstack7 = dynSlot.safeTake(j1, Integer.MAX_VALUE, pPlayer);
                pPlayer.drop(itemstack7, true);
            } else if (pClickType == ClickType.PICKUP_ALL && pSlotId >= 0) {
                ItemStack itemstack4 = this.getCarried();
                Slot dynSlot = (Slot)this.slots.get(pSlotId);
                if (!(itemstack4.isEmpty() || dynSlot.hasItem() && dynSlot.mayPickup(pPlayer))) {
                    int l1 = pButton == 0 ? 0 : this.slots.size() - 1;
                    int k2 = pButton == 0 ? 1 : -1;
                    for (int l2 = 0; l2 < 2; ++l2) {
                        for (int l3 = l1; l3 >= 0 && l3 < this.slots.size() && itemstack4.getCount() < itemstack4.getMaxStackSize(); l3 += k2) {
                            Slot slot8 = (Slot)this.slots.get(l3);
                            if (!slot8.hasItem() || !PaginationContainer.canItemQuickReplace((Slot)slot8, (ItemStack)itemstack4, (boolean)true) || !slot8.mayPickup(pPlayer) || !this.canTakeItemForPickAll(itemstack4, slot8)) continue;
                            ItemStack itemstack11 = slot8.getItem();
                            if (l2 == 0 && itemstack11.getCount() == itemstack11.getMaxStackSize()) continue;
                            ItemStack itemstack12 = slot8.safeTake(itemstack11.getCount(), itemstack4.getMaxStackSize() - itemstack4.getCount(), pPlayer);
                            itemstack4.grow(itemstack12.getCount());
                        }
                    }
                }
            }
        }
    }

    private SlotAccess createCarriedSlotAccess() {
        return new SlotAccess(){

            public ItemStack get() {
                return PaginationContainer.this.getCarried();
            }

            public boolean set(ItemStack stack) {
                PaginationContainer.this.setCarried(stack);
                return true;
            }
        };
    }

    private void prevPage() {
        --this.currentPage;
        this.loadCurrentPage();
        this.broadcastChanges();
    }

    private void nextPage() {
        ++this.currentPage;
        this.loadCurrentPage();
        this.broadcastChanges();
    }

    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (this.readOnly) {
            return ItemStack.EMPTY;
        }
        if (slotIndex == 45 || slotIndex == 53) {
            return ItemStack.EMPTY;
        }
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot)this.slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (slotIndex < 54 ? !this.moveItemStackTo(itemstack1, 54, 90, true) : !this.moveItemStackTo(itemstack1, 0, 53, false)) {
                return ItemStack.EMPTY;
            }
            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public int getTotalPages() {
        return this.maxPages;
    }

    private class DynamicPageSlot
    extends Slot {
        private final int displayIndex;

        DynamicPageSlot(SimpleContainer slotContainer, int displayIndex, int x, int y) {
            super((Container)slotContainer, 0, x, y);
            this.displayIndex = displayIndex;
        }

        private int getActualIndex() {
            return PaginationContainer.this.getPageStartIndex() + this.displayIndex;
        }

        private boolean isInPageRange() {
            int idx = this.getActualIndex();
            return idx < PaginationContainer.this.getPageEndIndex() && idx < PaginationContainer.this.containerSize;
        }

        public ItemStack getItem() {
            if (this.isInPageRange()) {
                return PaginationContainer.this.container.getItem(this.getActualIndex());
            }
            return PaginationContainer.this.blockItem.copy();
        }

        public void set(ItemStack stack) {
            if (this.isInPageRange()) {
                PaginationContainer.this.container.setItem(this.getActualIndex(), stack);
            }
            super.set(stack);
        }

        public ItemStack remove(int pAmount) {
            return PaginationContainer.this.container.removeItem(this.getActualIndex(), pAmount);
        }

        public void setChanged() {
            super.setChanged();
        }

        public boolean hasItem() {
            if (this.isInPageRange()) {
                return !PaginationContainer.this.container.getItem(this.getActualIndex()).isEmpty();
            }
            return true;
        }

        public boolean mayPlace(ItemStack stack) {
            return this.isInPageRange();
        }

        public boolean mayPickup(Player player) {
            return this.isInPageRange();
        }

        public int getMaxStackSize() {
            if (this.isInPageRange()) {
                return PaginationContainer.this.container.getMaxStackSize();
            }
            return 1;
        }

        public Optional<ItemStack> tryRemove(int pCount, int pDecrement, Player pPlayer) {
            if (!this.mayPickup(pPlayer)) {
                return Optional.empty();
            }
            if (!this.allowModification(pPlayer) && pDecrement < this.getItem().getCount()) {
                return Optional.empty();
            }
            ItemStack itemstack = this.remove(pCount = Math.min(pCount, pDecrement));
            if (itemstack.isEmpty()) {
                return Optional.empty();
            }
            if (this.getItem().isEmpty()) {
                this.setByPlayer(ItemStack.EMPTY);
            }
            return Optional.of(itemstack);
        }
    }

    public class NavigationSlot
    extends FixedSlot {
        private final boolean isPrev;

        NavigationSlot(boolean isPrev, int x, int y) {
            super(x, y);
            this.isPrev = isPrev;
            this.setItem();
        }

        private void setItem() {
            if (this.isPrev) {
                this.set(PaginationContainer.this.navItemPrev);
            } else {
                this.set(PaginationContainer.this.navItemNext);
            }
        }
    }

    public class BlockSlot
    extends FixedSlot {
        BlockSlot(int x, int y) {
            super(x, y);
            this.setItem();
        }

        public void setItem() {
            this.set(PaginationContainer.this.blockItem);
        }
    }

    public class IndexSlot
    extends FixedSlot {
        IndexSlot(int x, int y) {
            super(x, y);
            this.setItem();
        }

        public void setItem() {
            this.set(PaginationContainer.this.indexItem);
        }
    }
}
