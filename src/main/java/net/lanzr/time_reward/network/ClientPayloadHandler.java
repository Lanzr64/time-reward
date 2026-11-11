package net.lanzr.time_reward.network;

import java.util.List;
import java.util.function.Supplier;
import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.client.ContainerStateManager;
import net.lanzr.time_reward.client.gui.BackpackScreen;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.BackpackCarriedUpdatePayload;
import net.lanzr.time_reward.network.BackpackSlotSyncPayload;
import net.lanzr.time_reward.network.BackpackStatePayload;
import net.lanzr.time_reward.network.OpenBackpackScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleOpenBackpackScreen(OpenBackpackScreenPayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            SimpleContainer storage = new SimpleContainer(data.containerSize());
            ContainerStateManager.getInstance().registerBackpack(data.containerId(), storage);
            BackpackContainer bc = new BackpackContainer(data.containerId(), mc.player.getInventory(), (Container)storage, data.scrollOffset());
            bc.setLastOccupiedRow(data.lastOccupiedRow());
            mc.player.containerMenu = bc;
            mc.setScreen((Screen)new BackpackScreen(bc, mc.player.getInventory(), data.title()));
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleBackpackState(BackpackStatePayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AbstractContainerMenu patt2539$temp;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && (patt2539$temp = mc.player.containerMenu) instanceof BackpackContainer) {
                BackpackContainer bc = (BackpackContainer)patt2539$temp;
                if (bc.containerId == data.containerId()) {
                    int oldRow = bc.getLastOccupiedRow();
                    bc.setLastOccupiedRow(data.lastOccupiedRow());
                    TimeReward.LOGGER.info("[BackpackState-Diag] received lastOccupiedRow: old={} new={} (containerId={})", new Object[]{oldRow, data.lastOccupiedRow(), data.containerId()});
                    Screen patt3010$temp = mc.screen;
                    if (patt3010$temp instanceof BackpackScreen) {
                        BackpackScreen bs = (BackpackScreen)patt3010$temp;
                        bs.onLastOccupiedRowChanged();
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleBackpackSlotSync(BackpackSlotSyncPayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AbstractContainerMenu patt3549$temp;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && (patt3549$temp = mc.player.containerMenu) instanceof BackpackContainer) {
                BackpackContainer bc = (BackpackContainer)patt3549$temp;
                if (bc.containerId == data.containerId()) {
                    List<ItemStack> items = data.items();
                    Container storage = bc.getStorageContainer();
                    int start = data.startRow() * BackpackContainer.COLS;
                    int count = Math.min(items.size(), storage.getContainerSize() - start);
                    for (int i = 0; i < count; i++) {
                        storage.setItem(start + i, items.get(i));
                    }
                    if (mc.screen instanceof BackpackScreen screen) {
                        screen.updateSlotsPosition();
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleStorageSlot(BackpackStorageSlotPayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(mc.player.containerMenu instanceof BackpackContainer backpack)
                    || backpack.containerId != data.containerId()) {
                return;
            }
            Container storage = backpack.getStorageContainer();
            if (data.storageIndex() < 0 || data.storageIndex() >= storage.getContainerSize()) {
                return;
            }
            storage.setItem(data.storageIndex(), data.item());
            if (mc.screen instanceof BackpackScreen screen) {
                screen.updateSlotsPosition();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleCarriedUpdate(BackpackCarriedUpdatePayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AbstractContainerMenu patt5379$temp;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && (patt5379$temp = mc.player.containerMenu) instanceof BackpackContainer) {
                BackpackContainer bc = (BackpackContainer)patt5379$temp;
                if (bc.containerId == data.containerId()) {
                    bc.setCarried(data.carried());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
