//package net.lanzr.time_reward.inventory;
//
//import net.minecraft.world.Container;
//import net.minecraft.world.SimpleContainer;
//import net.minecraft.world.entity.player.Inventory;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.inventory.AbstractContainerMenu;
//import net.minecraft.world.inventory.MenuType;
//import net.minecraft.world.inventory.Slot;
//import net.minecraft.world.item.ItemStack;
//import org.jetbrains.annotations.Nullable;
//
//public class RewardMenu_depreacted extends AbstractContainerMenu {
//    private final Container container;
//    private final ContainerTypes containerType;
//
//    public static RewardMenu_depreacted createTSTContainer(int containerID, Inventory playerInventory, Container container) {
//        return new RewardMenu_depreacted(RewardContainerTypes.REWARD_MENU.get(), containerID, playerInventory, container,ContainerTypes.REWARD_CHEST);
//    }
//
//    public static RewardMenu_depreacted createTSTContainer(int containerId, Inventory playerInventory) {
//        return new RewardMenu_depreacted(RewardContainerTypes.REWARD_MENU.get(), containerId, playerInventory, new SimpleContainer(ContainerTypes.REWARD_CHEST.size), ContainerTypes.REWARD_CHEST);
//    }
//
//    protected RewardMenu_depreacted(@Nullable MenuType<?> menuType, int containerID, Inventory playerInventory, Container container, ContainerTypes containerType) {
//        super(menuType, containerID);
//        checkContainerSize(container, containerType.size);
//
//        this.container  = container;
//        this.containerType = containerType;
//
//        this.addSlot(new RewardSlot(container, 0, 0, 0));
//
//        int leftCol = (containerType.xSize - 162) / 2 + 1;
//
//        for (int playerInvRow = 0; playerInvRow < 3; playerInvRow++) {
//            for (int playerInvCol = 0; playerInvCol < 9; playerInvCol++) {
//                this.addSlot(new Slot(playerInventory, playerInvCol + playerInvRow * 9 + 9, leftCol + playerInvCol * 18, containerType.ySize - (4 - playerInvRow) * 18 - 10));
//            }
//
//        }
//        for (int hotHarSlot = 0; hotHarSlot < 9; hotHarSlot++) {
//            this.addSlot(new Slot(playerInventory, hotHarSlot, leftCol + hotHarSlot * 18, 42 - 24));
//        }
//
//    }
//
//    @Override
//    public ItemStack quickMoveStack(Player player, int index) {
//        ItemStack itemStack = ItemStack.EMPTY;
//        Slot slot = this.slots.get(index);
//
//        if (slot != null && slot.hasItem()) {
//            ItemStack itemstack1 = slot.getItem();
//            itemStack = itemstack1.copy();
//
//            if (index == 0) {
//                if (!this.moveItemStackTo(itemstack1, 10, 46, true)) {
//                    return ItemStack.EMPTY;
//                }
//            } else if (!this.moveItemStackTo(itemstack1, 1, 9, false)) {
//                return ItemStack.EMPTY;
//            }
//
//            if (itemstack1.isEmpty()) {
//                slot.set(ItemStack.EMPTY);
//            } else {
//                slot.setChanged();
//            }
//        }
//        return null;
//    }
//
//    @Override
//    public boolean stillValid(Player player) {
//        return this.container.stillValid(player);
//    }
//    public int getRowCount() {
//        return this.containerType.rowSize;
//    }
//    public Container getContainer() {
//        return this.container;
//    }
//}
