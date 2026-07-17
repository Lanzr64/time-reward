package net.lanzr.time_reward.container;

import net.lanzr.time_reward.container.FixedSlot;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * 纯服务端分页容器菜单
 *
 * 使用 MenuType.GENERIC_9x6 (原版6行箱子) 作为菜单类型，
 * 客户端直接用原版 ChestScreen 渲染，无需任何客户端mod代码。
 *
 * 接受任意 {@link Container} 实现作为数据源（LargeInventory、玩家背包、箱子等），
 * 每页显示 45 个物品格 + 导航栏，支持翻页。
 *
 * 界面布局(6行x9列):
 *   Row 0-4: 物品格 (45格, 槽位 0-44)
 *   Row 5:   [上一页][装饰x3][页码][装饰x3][下一页] (槽位 45-53)
 *   玩家背包: 27格 (槽位 54-80)
 *   快捷栏: 9格 (槽位 81-89)
 *
 * 槽位总数: 90
 */
public class PaginationContainer extends AbstractContainerMenu {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int ROWS = 6;
    public static final int COLS = 9;
    public static final int DISPLAY_SLOTS = ROWS * COLS; // 54
    /** 每页物品格数 (5行x9列，去掉导航行) */
    public static final int ITEM_SLOTS_PER_PAGE = 5 * 9; // 45

    // 导航按钮的容器槽位索引
    public static final int NAV_PREV_CONTAINER_SLOT = 45;
    public static final int NAV_NEXT_CONTAINER_SLOT = 53;

    // 玩家物品栏起始槽位索引
    private static final int PLAYER_INV_START = 54;
    private static final int TOTAL_SLOTS = 90;

    /** 后端数据容器（任意 Container 实现） */
    private final Container container;
    private final Inventory playerInventory;

    private final int containerSize;
    private final int maxPages;
    private int currentPage = 1;

    private int quickcraftType = -1;
    private int quickcraftStatus;
    private final Set<Slot> quickcraftSlots = Sets.newHashSet();

    private ItemStack navItemPrev = new ItemStack(Items.ARROW);
    private ItemStack navItemNext = new ItemStack(Items.ARROW);
    private ItemStack blockItem = new ItemStack(Items.GLASS_PANE);
    {
        blockItem.set(DataComponents.CUSTOM_NAME, Component.literal("Empty"));
    }
    private ItemStack indexItem = new ItemStack(Items.MUSIC_DISC_BLOCKS);
    /** 每个 PaginationContainer 实例持有自己的 Slot 安全容器 */
    private final SimpleContainer slotContainer = new SimpleContainer(90);
    /** 关闭时回调（用于自动保存） */
    private Runnable saveCallback = null;
    /**
     * 服务端构造
     * @param container 任意容器实现（LargeInventory、玩家背包、箱子等）
     */
    public PaginationContainer(int id, Inventory playerInventory, Container container) {
        super(MenuType.GENERIC_9x6, id);
        this.container = container;
        this.playerInventory = playerInventory;
        this.containerSize = container.getContainerSize();
        // 根据容器大小计算总页数
        this.maxPages = Math.max(1, (this.containerSize + ITEM_SLOTS_PER_PAGE - 1) / ITEM_SLOTS_PER_PAGE);
        this.currentPage = 1;
        createDisplaySlots();
        loadCurrentPage();
    }
    // ========== 分页计算 ==========

    /** 当前页在容器中的起始索引 */
    private int getPageStartIndex() {
        return (currentPage - 1) * ITEM_SLOTS_PER_PAGE;
    }

    /** 当前页在容器中的结束索引（exclusive） */
    private int getPageEndIndex() {
        return Math.min(currentPage * ITEM_SLOTS_PER_PAGE, containerSize);
    }

    private boolean canMoveToPage(int page) {
        return page >= 1 && page <= maxPages;
    }

    // ========== 导航显示 ==========

    private void loadCurrentPage() {
        if (currentPage == 1) {
            navItemPrev.set(DataComponents.CUSTOM_NAME, Component.literal("§7◀ 已到最前"));
        } else {
            navItemPrev.set(DataComponents.CUSTOM_NAME, Component.literal("§a◀ 上一页"));
        }

        if (currentPage == maxPages) {
            navItemNext.set(DataComponents.CUSTOM_NAME, Component.literal("§7已到最后 ▶"));
        } else {
            navItemNext.set(DataComponents.CUSTOM_NAME, Component.literal("§a下一页 ▶"));
        }

        indexItem.set(DataComponents.CUSTOM_NAME, Component.literal(String.format("%d / %d", currentPage, maxPages)));
    }

    // ========== 槽位布局 ==========

    /**
     * 创建固定槽位布局（构造函数中只调用一次，翻页不重建）
     * Rows 0-4 全部使用 DynamicPageSlot，根据 currentPage 动态计算容器索引
     */
    private void createDisplaySlots() {
        final int navPosY = 18 + 5 * 18;

        // ========== Rows 0-4: 物品格 (45个) ==========
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                int displayIndex = row * 9 + col;
                addSlot(new DynamicPageSlot(slotContainer, displayIndex,
                        8 + col * 18, 18 + row * 18));
            }
        }

        // ========== Row 5: 导航栏 ==========
        // Col 0: 上一页
        addSlot(new NavigationSlot(true, 8, navPosY));

        // Col 1-3: 装饰块
        for (int col = 1; col < 4; col++) {
            addSlot(new BlockSlot(8 + col * 18, navPosY));
        }
        // Col 4: 页码显示
        addSlot(new IndexSlot(8 + 8 * 18, navPosY));
        // Col 5-7: 装饰块
        for (int col = 1; col < 4; col++) {
            addSlot(new BlockSlot(8 + col * 18, navPosY));
        }
        // Col 8: 下一页
        addSlot(new NavigationSlot(false, 8 + 8 * 18, navPosY));

        // ========== 玩家物品栏 (槽位 54-89) ==========
        // 背包 (27格)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + col + row * 9,
                        8 + col * 18, 140 + row * 18));
            }
        }
        // 快捷栏 (9格)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    8 + col * 18, 198));
        }
    }

    // ========== 容器生命周期 ==========

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (saveCallback != null) {
            saveCallback.run();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ========== 点击处理 ==========

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
//        System.out.printf("Clicked(SlotId=%d, dragtype=%d, pClickType=%s, pPlayer=%s)\n",
//                slotId, dragType, clickType, player);
//        在玩家物品槽位中
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (slot instanceof NavigationSlot) {
                NavigationSlot navSlot = (NavigationSlot) slot;
                if (navSlot.isPrev && canMoveToPage(currentPage - 1)) {
                    prevPage();
                } else if (!navSlot.isPrev && canMoveToPage(currentPage + 1)) {
                    nextPage();
                }
                return;
            }
            doClick(slotId, dragType, clickType, player);

            // Auto-save container after any interaction with container slots
            if (saveCallback != null && slotId < PLAYER_INV_START) {
                saveCallback.run();
            }
        } else {
           if (slotId == -999) {
               doClick(slotId, dragType, clickType, player);
           }
        }
    }
    @Override
    protected void resetQuickCraft() {
        this.quickcraftStatus = 0;
        this.quickcraftSlots.clear();
    }

    //    注释，点击键，点击类型，点击玩家，动态槽
    private void doClick(int pSlotId, int pButton, ClickType pClickType, Player pPlayer) {
//        System.out.println("doclick");
//        System.out.printf("doClick(pSlotId=%d, pButton=%d, pClickType=%s, pPlayer=%s, dynSlot=%s)\n",
//                pSlotId, pButton, pClickType, pPlayer, dynSlot);
        Inventory inventory = pPlayer.getInventory();
        if (pClickType == ClickType.QUICK_CRAFT) {
            int i = this.quickcraftStatus;
            this.quickcraftStatus = getQuickcraftHeader(pButton);
//            System.out.println("quick status " + this.quickcraftStatus + " i " + i);
            if ((i != 1 || this.quickcraftStatus != 2) && i != this.quickcraftStatus) {
//                System.out.println("reset ");
                this.resetQuickCraft();
            } else if (this.getCarried().isEmpty()) {
                this.resetQuickCraft();
            } else if (this.quickcraftStatus == 0) {
                this.quickcraftType = getQuickcraftType(pButton);
                if (isValidQuickcraftType(this.quickcraftType, pPlayer)) {
                    this.quickcraftStatus = 1;
                    this.quickcraftSlots.clear();
                } else {
                    this.resetQuickCraft();
                }
            } else if (this.quickcraftStatus == 1) {
//                Slot slot = this.slots.get(pSlotId);
                ItemStack itemstack = this.getCarried();
                Slot dynSlot = slots.get(pSlotId);
                if (canItemQuickReplace(dynSlot, itemstack, true)
                        && dynSlot.mayPlace(itemstack)
                        && (this.quickcraftType == 2 || itemstack.getCount() > this.quickcraftSlots.size())
                        && this.canDragTo(dynSlot)) {
//                    System.out.println("can quick replace");
                    this.quickcraftSlots.add(dynSlot);
                }
            } else if (this.quickcraftStatus == 2) {
                if (!this.quickcraftSlots.isEmpty()) {
                    if (this.quickcraftSlots.size() == 1) {
                        int i1 = (this.quickcraftSlots.iterator().next()).index;
                        this.resetQuickCraft();
                        this.doClick(i1, this.quickcraftType, ClickType.PICKUP, pPlayer);
//                        System.out.println("status 1");
                        return;
                    }

                    ItemStack itemstack2 = this.getCarried().copy();
                    if (itemstack2.isEmpty()) {
//                        System.out.println("status 2");
                        this.resetQuickCraft();
                        return;
                    }

                    int k1 = this.getCarried().getCount();

                    for(Slot slot1 : this.quickcraftSlots) {
                        ItemStack itemstack1 = this.getCarried();
                        if (slot1 != null && canItemQuickReplace(slot1, itemstack1, true) && slot1.mayPlace(itemstack1) && (this.quickcraftType == 2 || itemstack1.getCount() >= this.quickcraftSlots.size()) && this.canDragTo(slot1)) {
                            int j = slot1.hasItem() ? slot1.getItem().getCount() : 0;
                            int k = Math.min(itemstack2.getMaxStackSize(), slot1.getMaxStackSize(itemstack2));
                            int l = Math.min(getQuickCraftPlaceCount(this.quickcraftSlots, this.quickcraftType, itemstack2) + j, k);
                            k1 -= l - j;
                            slot1.setByPlayer(itemstack2.copyWithCount(l));
                        }
                    }

                    itemstack2.setCount(k1);
                    this.setCarried(itemstack2);
                }

//                System.out.println("status 3");
                this.resetQuickCraft();
            } else {
//                System.out.println("so is else");

                this.resetQuickCraft();
            }
        } else if (this.quickcraftStatus != 0) {
            this.resetQuickCraft();
        } else if ((pClickType == ClickType.PICKUP || pClickType == ClickType.QUICK_MOVE) && (pButton == 0 || pButton == 1)) {
//            System.out.println("IN HERE");
            Slot dynSlot = slots.get(pSlotId);
            ClickAction clickaction = pButton == 0 ? ClickAction.PRIMARY : ClickAction.SECONDARY;
//            System.out.printf("carryStack=%s, dynStack=%s\n",
//                    this.getCarried().isEmpty() ? "empty" : this.getCarried().toString(),
//                    dynSlot.getItem().isEmpty() ? "empty" : dynSlot.getItem().toString());
//            System.out.printf("maypickup:" + dynSlot.mayPickup(pPlayer) + " mayplace:" + dynSlot.mayPlace(this.getCarried()));
            if (pSlotId == -999) {
                if (!this.getCarried().isEmpty()) {
                    if (clickaction == ClickAction.PRIMARY) {
                        pPlayer.drop(this.getCarried(), true);
                        this.setCarried(ItemStack.EMPTY);
                    } else {
                        pPlayer.drop(this.getCarried().split(1), true);
                    }
                }
            } else if (pClickType == ClickType.QUICK_MOVE) {
                if (pSlotId < 0) {
                    return;
                }

                if (!dynSlot.mayPickup(pPlayer)) {
                    return;
                }

                for(ItemStack itemstack8 = this.quickMoveStack(pPlayer, pSlotId); !itemstack8.isEmpty() && ItemStack.isSameItem(dynSlot.getItem(), itemstack8); itemstack8 = this.quickMoveStack(pPlayer, pSlotId)) {
                }
            } else {
                if (pSlotId < 0) {
                    return;
                }

                ItemStack dynstack = dynSlot.getItem();
                ItemStack carrystack = this.getCarried();
                pPlayer.updateTutorialInventoryAction(carrystack, dynSlot.getItem(), clickaction);
                if (true) {
//                    System.out.println("key change");
//                    System.out.println("in key logic");
//                System.out.printf("carryStack=%s, dynStack=%s\n",
//                        carrystack.isEmpty() ? "empty" : carrystack.toString(),
//                        dynSlot.getItem().isEmpty() ? "empty" : dynSlot.getItem().toString());
//                System.out.printf("maypickup:" + dynSlot.mayPickup(pPlayer) + " mayplace:" + dynSlot.mayPlace(carrystack));
                        if (dynstack.isEmpty()) {
                            if (!carrystack.isEmpty()) {
                                int i3 = clickaction == ClickAction.PRIMARY ? carrystack.getCount() : 1;
                                this.setCarried(dynSlot.safeInsert(carrystack, i3));
                            }
                        } else if (dynSlot.mayPickup(pPlayer)) {

//                            System.out.println("carryStack empty:" + carrystack.isEmpty());
                            if (carrystack.isEmpty()) {
//                                System.out.println("try pickup");
                                int j3 = clickaction == ClickAction.PRIMARY ? dynstack.getCount() : (dynstack.getCount() + 1) / 2;
                                Optional<ItemStack> optional1 = dynSlot.tryRemove(j3, Integer.MAX_VALUE, pPlayer);
                                optional1.ifPresent((p_150421_) -> {
//                                    System.out.println("pick success");
                                    this.setCarried(p_150421_);
                                    dynSlot.onTake(pPlayer, p_150421_);
                                });
                            } else if (dynSlot.mayPlace(carrystack)) {
                                if (ItemStack.isSameItemSameComponents(dynstack, carrystack)) {
                                    int k3 = clickaction == ClickAction.PRIMARY ? carrystack.getCount() : 1;
                                    this.setCarried(dynSlot.safeInsert(carrystack, k3));
                                } else if (carrystack.getCount() <= dynSlot.getMaxStackSize(carrystack)) {
                                    this.setCarried(dynstack);
                                    dynSlot.setByPlayer(carrystack);
                                }
                            } else if (ItemStack.isSameItemSameComponents(dynstack, carrystack)) {
                                Optional<ItemStack> optional = dynSlot.tryRemove(dynstack.getCount(),
                                        carrystack.getMaxStackSize() - carrystack.getCount(), pPlayer);
                                optional.ifPresent((p_150428_) -> {
                                    carrystack.grow(p_150428_.getCount());
                                    dynSlot.onTake(pPlayer, p_150428_);
                                });
                            }
                        }
                }

                dynSlot.setChanged();
            }
        } else if (pClickType == ClickType.SWAP) {
            Slot dynSlot = slots.get(pSlotId);
            ItemStack itemstack3 = inventory.getItem(pButton);
            ItemStack itemstack6 = dynSlot.getItem();
            if (!itemstack3.isEmpty() || !itemstack6.isEmpty()) {
                if (itemstack3.isEmpty()) {
                    if (dynSlot.mayPickup(pPlayer)) {
                        inventory.setItem(pButton, itemstack6);
//                        dynslot.onSwapCraft(itemstack6.getCount());
                        dynSlot.setByPlayer(ItemStack.EMPTY);
                        dynSlot.onTake(pPlayer, itemstack6);
                    }
                } else if (itemstack6.isEmpty()) {
                    if (dynSlot.mayPlace(itemstack3)) {
                        int i2 = dynSlot.getMaxStackSize(itemstack3);
                        if (itemstack3.getCount() > i2) {
                            dynSlot.setByPlayer(itemstack3.split(i2));
                        } else {
                            inventory.setItem(pButton, ItemStack.EMPTY);
                            dynSlot.setByPlayer(itemstack3);
                        }
                    }
                } else if (dynSlot.mayPickup(pPlayer) && dynSlot.mayPlace(itemstack3)) {
                    int j2 = dynSlot.getMaxStackSize(itemstack3);
                    if (itemstack3.getCount() > j2) {
                        dynSlot.setByPlayer(itemstack3.split(j2));
                        dynSlot.onTake(pPlayer, itemstack6);
                        if (!inventory.add(itemstack6)) {
                            pPlayer.drop(itemstack6, true);
                        }
                    } else {
                        inventory.setItem(pButton, itemstack6);
                        dynSlot.setByPlayer(itemstack3);
                        dynSlot.onTake(pPlayer, itemstack6);
                    }
                }
            }
        } else if (pClickType == ClickType.CLONE && pPlayer.getAbilities().instabuild && this.getCarried().isEmpty() && pSlotId >= 0) {
            Slot dynSlot = slots.get(pSlotId);
            if (dynSlot.hasItem()) {
                ItemStack itemstack5 = dynSlot.getItem();
                this.setCarried(itemstack5.copyWithCount(itemstack5.getMaxStackSize()));
            }
        } else if (pClickType == ClickType.THROW && this.getCarried().isEmpty() && pSlotId >= 0) {
            Slot dynSlot = slots.get(pSlotId);
            int j1 = pButton == 0 ? 1 : dynSlot.getItem().getCount();
            ItemStack itemstack7 = dynSlot.safeTake(j1, Integer.MAX_VALUE, pPlayer);
            pPlayer.drop(itemstack7, true);
        } else if (pClickType == ClickType.PICKUP_ALL && pSlotId >= 0) {
            ItemStack itemstack4 = this.getCarried();
            Slot dynSlot = slots.get(pSlotId);
            if (!itemstack4.isEmpty() && (!dynSlot.hasItem() || !dynSlot.mayPickup(pPlayer))) {
                int l1 = pButton == 0 ? 0 : this.slots.size() - 1;
                int k2 = pButton == 0 ? 1 : -1;

                for(int l2 = 0; l2 < 2; ++l2) {
                    for(int l3 = l1; l3 >= 0 && l3 < this.slots.size() && itemstack4.getCount() < itemstack4.getMaxStackSize(); l3 += k2) {
                        Slot slot8 = this.slots.get(l3);
                        if (slot8.hasItem() && canItemQuickReplace(slot8, itemstack4, true) && slot8.mayPickup(pPlayer) && this.canTakeItemForPickAll(itemstack4, slot8)) {
                            ItemStack itemstack11 = slot8.getItem();
                            if (l2 != 0 || itemstack11.getCount() != itemstack11.getMaxStackSize()) {
                                ItemStack itemstack12 = slot8.safeTake(itemstack11.getCount(), itemstack4.getMaxStackSize() - itemstack4.getCount(), pPlayer);
                                itemstack4.grow(itemstack12.getCount());
                            }
                        }
                    }
                }
            }
        }
    }
    
    private SlotAccess createCarriedSlotAccess() {
        return new SlotAccess() {
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
        currentPage--;
        loadCurrentPage();
        broadcastChanges();
    }

    private void nextPage() {
        currentPage++;
        loadCurrentPage();
        broadcastChanges();
    }

    // ========== Shift+点击 ==========

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex == NAV_PREV_CONTAINER_SLOT || slotIndex == NAV_NEXT_CONTAINER_SLOT) {
            return ItemStack.EMPTY;
        }

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (slotIndex < PLAYER_INV_START) {
                // 展示区 → 玩家背包
                if (!this.moveItemStackTo(itemstack1, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, NAV_NEXT_CONTAINER_SLOT, false)) {
                // 玩家背包 → 展示区
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

    // ========== 内部类 ==========

    /**
     * 动态物品槽 - 根据 currentPage 实时计算在 LargeInventory 中的实际索引
     * 翻页时无需重建槽位，只需改变 currentPage 即可
     *
     * 基类容器传一个安全的 dummy（避免 Forge 某些路径走基类的 container.getItem 导致取错数据），
     * 所有数据读写完全由下面的重写方法控制，全部指向 pageInv 的正确索引。
     */
    private class DynamicPageSlot extends Slot {
        /** 基类使用的安全容器，每个 PaginationContainer 一个实例 */
        private final int displayIndex;

        DynamicPageSlot(SimpleContainer slotContainer, int displayIndex, int x, int y) {
            super(slotContainer, 0, x, y);
            this.displayIndex = displayIndex;
        }

        /** 当前页中此槽位对应的实际容器索引 */
        private int getActualIndex() {
            return getPageStartIndex() + displayIndex;
        }



        /** 此槽位在当前页是否位于有效物品范围 */
        private boolean isInPageRange() {
            int idx = getActualIndex();
            return idx < getPageEndIndex() && idx < containerSize;
        }

        @Override
        public ItemStack getItem() {
            if (isInPageRange()) {
                return PaginationContainer.this.container.getItem(getActualIndex());
            }
            return blockItem.copy();
        }

        @Override
        public void set(ItemStack stack) {
            if (isInPageRange()) {
                PaginationContainer.this.container.setItem(getActualIndex(), stack);
            }
            super.set(stack);
        }

        @Override
        public ItemStack remove(int pAmount) {
            return PaginationContainer.this.container.removeItem(getActualIndex(),pAmount);
        }

        @Override
        public void setChanged() {
            super.setChanged();
        }

        @Override
        public boolean hasItem() {
            if (isInPageRange()) {
                return !PaginationContainer.this.container.getItem(getActualIndex()).isEmpty();
            }
            return true;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isInPageRange();
        }

        @Override
        public boolean mayPickup(Player player) {
            return isInPageRange();
        }

        @Override
        public int getMaxStackSize() {
            if (isInPageRange()) {
                return PaginationContainer.this.container.getMaxStackSize();
            }
            return 1;
        }
        @Override
        public Optional<ItemStack> tryRemove(int pCount, int pDecrement, Player pPlayer) {
            if (!this.mayPickup(pPlayer)) {
                return Optional.empty();
            } else if (!this.allowModification(pPlayer) && pDecrement < this.getItem().getCount()) {
                return Optional.empty();
            } else {
                pCount = Math.min(pCount, pDecrement);
                ItemStack itemstack = this.remove(pCount);
                if (itemstack.isEmpty()) {
                    return Optional.empty();
                } else {
                    if (this.getItem().isEmpty()) {
                        this.setByPlayer(ItemStack.EMPTY);
                    }

                    return Optional.of(itemstack);
                }
            }
        }
    }

    public class BlockSlot extends FixedSlot {
        BlockSlot(int x, int y) {
            super(x, y);
            setItem();
        }
        public void setItem() {
            this.set(blockItem);
        }
    }

    public class IndexSlot extends FixedSlot {
        IndexSlot(int x, int y) {
            super(x, y);
            setItem();
        }
        public void setItem() {
            this.set(indexItem);
        }
    }

    public class NavigationSlot extends FixedSlot {
        private final boolean isPrev;

        NavigationSlot(boolean isPrev, int x, int y) {
            super(x, y);
            this.isPrev = isPrev;
            setItem();
        }

        private void setItem() {
            if (isPrev) {
                this.set(navItemPrev);
            } else {
                this.set(navItemNext);
            }
        }
    }


    /** 设置关闭回调 */
    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }

    /** 获取当前页码（1-indexed） */
    public int getCurrentPage() {
        return currentPage;
    }

    /** 获取总页数 */
    public int getTotalPages() {
        return maxPages;
    }
}
