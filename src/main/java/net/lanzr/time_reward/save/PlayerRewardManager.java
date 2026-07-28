package net.lanzr.time_reward.save;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class PlayerRewardManager {
    private static final Path PLAYER_REWARDS_PATH = Paths.get("lzFiles", "player-rewards");
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_LEVEL = "Level";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_CONTAINER_SIZE = "ContainerSize";
    private static final int DATA_VERSION = 2;
    private static final Object FILE_LOCK = new Object();

    public static SimpleContainer loadOrCreate(UUID playerUuid, int expectedSize, SimpleContainer adminPool, HolderLookup.Provider lookup) {
        try {
            Files.createDirectories(PLAYER_REWARDS_PATH);
        } catch (IOException e) {
            System.err.println("[PlayerRewardManager] Cannot create directory: " + PLAYER_REWARDS_PATH);
            e.printStackTrace();
        }

        Path file = PLAYER_REWARDS_PATH.resolve(playerUuid.toString() + ".dat");

        synchronized (FILE_LOCK) {
            boolean fileExists = Files.exists(file);
            long fileSize = 0;
            if (fileExists) {
                try { fileSize = Files.size(file); } catch (IOException ignored) {}
            }
            System.out.println("[PlayerRewardManager] loadOrCreate: file=" + file + " exists=" + fileExists + " size=" + fileSize + " expectedSize=" + expectedSize);
            if (fileExists && fileSize > 0) {
                try {
                    CompoundTag data = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                    ListTag loadedList = data.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
                    int savedContainerSize = data.getInt(TAG_CONTAINER_SIZE);
                    int savedLevel = data.getInt(TAG_LEVEL);
                    System.out.println("[PlayerRewardManager] loadOrCreate: loaded " + loadedList.size() + " slot entries, savedSize=" + savedContainerSize + " expectedSize=" + expectedSize + " level=" + savedLevel);

                    if (savedContainerSize < expectedSize) {
                        // Player leveled up: create larger container, preserve old items, fill new from admin
                        int currentLevel = Math.min(expectedSize / LZSavedData.SLOTS_PER_LEVEL, 60);
                        SimpleContainer newContainer = new SimpleContainer(expectedSize);
                        for (int j = 0; j < loadedList.size(); j++) {
                            CompoundTag slotTag = loadedList.getCompound(j);
                            int slotIdx = slotTag.getInt("Slot");
                            if (slotIdx >= expectedSize) continue;
                            if (slotTag.contains("Item", Tag.TAG_COMPOUND)) {
                                newContainer.setItem(slotIdx, ItemStack.parse(lookup, slotTag.getCompound("Item")).orElse(ItemStack.EMPTY));
                            }
                        }
                        for (int i = savedContainerSize; i < expectedSize; i++) {
                            newContainer.setItem(i, adminPool.getItem(i).copy());
                        }
                        save(playerUuid, newContainer, lookup, Math.max(savedLevel, currentLevel));
                        return newContainer;
                    }

                    // Same size: deserialize all slots by index
                    SimpleContainer container = new SimpleContainer(expectedSize);
                    for (int j = 0; j < loadedList.size(); j++) {
                        CompoundTag slotTag = loadedList.getCompound(j);
                        int slotIdx = slotTag.getInt("Slot");
                        if (slotIdx >= expectedSize) continue;
                        if (slotTag.contains("Item", Tag.TAG_COMPOUND)) {
                            container.setItem(slotIdx, ItemStack.parse(lookup, slotTag.getCompound("Item")).orElse(ItemStack.EMPTY));
                        }
                    }
                    return container;
                } catch (IOException e) {
                    System.err.println("[PlayerRewardManager] WARN: Read error for " + playerUuid + ", recreating from admin pool");
                    e.printStackTrace();
                }
            }
        }

        // FIRST TIME or corrupted: create from admin pool
        SimpleContainer container = new SimpleContainer(expectedSize);
        for (int i = 0; i < expectedSize; i++) {
            container.setItem(i, adminPool.getItem(i).copy());
        }
        save(playerUuid, container, lookup, Math.min(expectedSize / LZSavedData.SLOTS_PER_LEVEL, 60));
        return container;
    }

    public static void save(UUID playerUuid, SimpleContainer container, HolderLookup.Provider lookup) {
        save(playerUuid, container, lookup, -1);
    }

    public static void save(UUID playerUuid, SimpleContainer container, HolderLookup.Provider lookup, int level) {
        synchronized (FILE_LOCK) {
            Path file = PLAYER_REWARDS_PATH.resolve(playerUuid.toString() + ".dat");
            Path tempFile = PLAYER_REWARDS_PATH.resolve(playerUuid.toString() + ".tmp");
            try {
                Files.createDirectories(PLAYER_REWARDS_PATH);

                int saveLevel = level;
                if (saveLevel < 0) {
                    saveLevel = getStoredLevel(playerUuid);
                    if (saveLevel < 0) saveLevel = 0;
                }

                CompoundTag data = new CompoundTag();
                data.putInt(TAG_VERSION, DATA_VERSION);
                data.putInt(TAG_LEVEL, saveLevel);

                // Custom serialization: save ALL slots with their indices, preserving empty slots
                ListTag fullList = new ListTag();
                for (int i = 0; i < container.getContainerSize(); i++) {
                    CompoundTag slotTag = new CompoundTag();
                    slotTag.putInt("Slot", i);
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty()) {
                        slotTag.put("Item", stack.save(lookup, new CompoundTag()));
                    }
                    fullList.add(slotTag);
                }
                data.putInt(TAG_CONTAINER_SIZE, container.getContainerSize());
                System.out.println("[PlayerRewardManager] customSave: containerSize=" + container.getContainerSize() + " savedSlots=" + fullList.size() + " level=" + saveLevel);
                data.put(TAG_ITEMS, fullList);

                NbtIo.writeCompressed(data, tempFile);
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("[PlayerRewardManager] Saved reward data for " + playerUuid + " (size=" + container.getContainerSize() + ", level=" + saveLevel + ")");
            } catch (IOException e) {
                System.err.println("[PlayerRewardManager] ERROR saving reward data for " + playerUuid);
                e.printStackTrace();
            }
        }
    }

    public static int getStoredLevel(UUID playerUuid) {
        Path file = PLAYER_REWARDS_PATH.resolve(playerUuid.toString() + ".dat");
        synchronized (FILE_LOCK) {
            try {
                if (Files.exists(file) && Files.size(file) > 0) {
                    CompoundTag data = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                    return data.getInt(TAG_LEVEL);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
            return -1;
        }
    }
}
