package net.lanzr.time_reward.server;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.BackpackSlotSyncPayload;
import net.lanzr.time_reward.network.BackpackStorageSlotPayload;
import net.lanzr.time_reward.network.ModNetwork;
import net.lanzr.time_reward.network.OpenBackpackScreenPayload;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;

public final class BackpackContainerManager {
    private static final BackpackContainerManager INSTANCE = new BackpackContainerManager();
    private final Map<UUID, BackpackContainer> openContainers = new ConcurrentHashMap<UUID, BackpackContainer>();
    private static final ContainerSynchronizer NO_OP_SYNC = new ContainerSynchronizer(){

        public void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carriedItem, int[] data) {
        }

        public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack) {
        }

        public void sendCarriedChange(AbstractContainerMenu container, ItemStack carriedItem) {
        }

        public void sendDataChange(AbstractContainerMenu container, int id, int value) {
        }
    };

    private BackpackContainerManager() {
    }

    public static BackpackContainerManager getInstance() {
        return INSTANCE;
    }

    public BackpackContainer openContainer(final ServerPlayer player, SimpleContainer storageContainer, int scrollOffset, int lastOccupiedRow, Runnable saveCallback) {
        UUID playerUUID = player.getUUID();
        if (this.openContainers.containsKey(playerUUID)) {
            this.closeContainer(playerUUID);
        }
        int containerId = BackpackContainerManager.incrementContainerCounter(player);
        BackpackContainer container = new BackpackContainer(containerId, player.getInventory(), (Container)storageContainer, scrollOffset);
        container.setSynchronizer(new ContainerSynchronizer(){

            public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
            }

            public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
                if (slot >= 0 && slot < BackpackContainer.TOTAL_DISPLAY_SLOTS) {
                    int storageIndex = container.getScrollOffset() * BackpackContainer.COLS + slot;
                    if (storageIndex < storageContainer.getContainerSize()) {
                        ModNetwork.sendToPlayer(player, new BackpackStorageSlotPayload(menu.containerId, storageIndex, stack.copy()));
                    }
                    return;
                }
                player.connection.send((Packet)new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot, stack));
            }

            public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {
                player.connection.send((Packet)new ClientboundContainerSetSlotPacket(-1, menu.getStateId(), -1, carried));
            }

            public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
            }
        });
        container.setSaveCallback(saveCallback);
        player.containerMenu = container;
        this.openContainers.put(playerUUID, container);
        ModNetwork.sendToPlayer(player, new OpenBackpackScreenPayload(containerId, storageContainer.getContainerSize(), scrollOffset, lastOccupiedRow, (Component)Component.literal((String)"\u5956\u52b1\u80cc\u5305")));
        this.sendStorageSnapshot(player, container);
        return container;
    }

    public void sendStorageSnapshot(ServerPlayer player, BackpackContainer backpack) {
        Container storage = backpack.getStorageContainer();
        int batchSize = BackpackContainer.TOTAL_DISPLAY_SLOTS;
        for (int start = 0; start < storage.getContainerSize(); start += batchSize) {
            int end = Math.min(start + batchSize, storage.getContainerSize());
            ArrayList<ItemStack> items = new ArrayList<>(end - start);
            for (int index = start; index < end; index++) {
                items.add(storage.getItem(index).copy());
            }
            ModNetwork.sendToPlayer(player, new BackpackSlotSyncPayload(
                    backpack.containerId, start / BackpackContainer.COLS, items));
        }
    }

    public BackpackContainer openContainer(ServerPlayer player, SimpleContainer storageContainer, Runnable saveCallback) {
        int lastOccupiedRow = BackpackContainerManager.computeLastOccupiedRow(storageContainer);
        return this.openContainer(player, storageContainer, 0, lastOccupiedRow, saveCallback);
    }

    public BackpackContainer getContainer(UUID playerUUID) {
        return this.openContainers.get(playerUUID);
    }

    public BackpackContainer getContainer(ServerPlayer player) {
        return this.openContainers.get(player.getUUID());
    }

    public void closeContainer(UUID playerUUID) {
        BackpackContainer container = this.openContainers.remove(playerUUID);
        if (container != null) {
            container.removed(null);
        }
    }

    public void removePlayer(UUID playerUUID) {
        BackpackContainer container = this.openContainers.remove(playerUUID);
        if (container != null) {
            try {
                container.removed(null);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    public boolean hasOpenContainer(UUID playerUUID) {
        return this.openContainers.containsKey(playerUUID);
    }

    /** The player's menu has already been closed and saved by ServerPlayer. */
    public void forgetContainer(UUID playerUUID) {
        this.openContainers.remove(playerUUID);
    }

    private static int incrementContainerCounter(ServerPlayer player) {
        player.nextContainerCounter();
        return player.containerCounter;
    }

    private static int computeLastOccupiedRow(SimpleContainer container) {
        for (int i = container.getContainerSize() - 1; i >= 0; --i) {
            if (container.getItem(i).isEmpty()) continue;
            return i / 12;
        }
        return -1;
    }
}
