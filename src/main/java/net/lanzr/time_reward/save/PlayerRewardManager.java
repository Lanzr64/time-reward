package net.lanzr.time_reward.save;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

public class PlayerRewardManager {
    private static Path playerRewardsPath = Paths.get("lzFiles", "player-rewards");
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_LEVEL = "Level";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_CONTAINER_SIZE = "ContainerSize";
    private static final int DATA_VERSION = 2;
    private static final Object FILE_LOCK = new Object();

    public static void initPath(ServerLevel level) {
        playerRewardsPath = level.getServer().getWorldPath(LevelResource.ROOT).resolve("lzFiles/player-rewards");
    }

    private static Path getPath() {
        return playerRewardsPath;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static SimpleContainer loadOrCreate(UUID playerUuid, int expectedSize, SimpleContainer adminPool) {
        try {
            Files.createDirectories(playerRewardsPath, new FileAttribute[0]);
        }
        catch (IOException e) {
            System.err.println("[PlayerRewardManager] Cannot create directory: " + String.valueOf(playerRewardsPath));
            e.printStackTrace();
        }
        Path file = playerRewardsPath.resolve(playerUuid.toString() + ".dat");
        Object object = FILE_LOCK;
        synchronized (object) {
            boolean fileExists = Files.exists(file, new LinkOption[0]);
            long fileSize = 0L;
            if (fileExists) {
                try {
                    fileSize = Files.size(file);
                }
                catch (IOException iOException) {
                    // empty catch block
                }
            }
            System.out.println("[PlayerRewardManager] loadOrCreate: file=" + String.valueOf(file) + " exists=" + fileExists + " size=" + fileSize + " expectedSize=" + expectedSize);
            if (fileExists && fileSize > 0L) {
                try {
                    CompoundTag data = NbtIo.readCompressed((File)file.toFile());
                    ListTag loadedList = data.getList(TAG_ITEMS, 10);
                    int savedContainerSize = data.getInt(TAG_CONTAINER_SIZE);
                    int savedLevel = data.getInt(TAG_LEVEL);
                    System.out.println("[PlayerRewardManager] loadOrCreate: loaded " + loadedList.size() + " slot entries, savedSize=" + savedContainerSize + " expectedSize=" + expectedSize + " level=" + savedLevel);
                    if (savedContainerSize < expectedSize) {
                        int currentLevel = Math.min(expectedSize / 10, 60);
                        SimpleContainer newContainer = new SimpleContainer(expectedSize);
                        for (int j = 0; j < loadedList.size(); ++j) {
                            CompoundTag slotTag = loadedList.getCompound(j);
                            int slotIdx = slotTag.getInt("Slot");
                            if (slotIdx >= expectedSize || !slotTag.contains("Item", 10)) continue;
                            newContainer.setItem(slotIdx, ItemStack.of((CompoundTag)slotTag.getCompound("Item")));
                        }
                        for (int i = savedContainerSize; i < expectedSize; ++i) {
                            newContainer.setItem(i, adminPool.getItem(i).copy());
                        }
                        PlayerRewardManager.save(playerUuid, newContainer, Math.max(savedLevel, currentLevel));
                        return newContainer;
                    }
                    if (savedContainerSize > expectedSize) {
                        int newLevel = Math.min(expectedSize / 10, 60);
                        SimpleContainer newContainer = new SimpleContainer(expectedSize);
                        for (int j = 0; j < loadedList.size(); ++j) {
                            CompoundTag slotTag = loadedList.getCompound(j);
                            int slotIdx = slotTag.getInt("Slot");
                            if (slotIdx >= expectedSize || !slotTag.contains("Item", 10)) continue;
                            newContainer.setItem(slotIdx, ItemStack.of((CompoundTag)slotTag.getCompound("Item")));
                        }
                        PlayerRewardManager.save(playerUuid, newContainer, Math.max(savedLevel, newLevel));
                        return newContainer;
                    }
                    SimpleContainer container = new SimpleContainer(expectedSize);
                    for (int j = 0; j < loadedList.size(); ++j) {
                        CompoundTag slotTag = loadedList.getCompound(j);
                        int slotIdx = slotTag.getInt("Slot");
                        if (slotIdx >= expectedSize || !slotTag.contains("Item", 10)) continue;
                        container.setItem(slotIdx, ItemStack.of((CompoundTag)slotTag.getCompound("Item")));
                    }
                    return container;
                }
                catch (IOException e) {
                    System.err.println("[PlayerRewardManager] WARN: Read error for " + String.valueOf(playerUuid) + ", recreating from admin pool");
                    e.printStackTrace();
                }
            }
        }
        SimpleContainer container = new SimpleContainer(expectedSize);
        for (int i = 0; i < expectedSize; ++i) {
            container.setItem(i, adminPool.getItem(i).copy());
        }
        PlayerRewardManager.save(playerUuid, container, Math.min(expectedSize / 10, 60));
        return container;
    }

    public static void save(UUID playerUuid, SimpleContainer container) {
        PlayerRewardManager.save(playerUuid, container, -1);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void save(UUID playerUuid, SimpleContainer container, int level) {
        Object object = FILE_LOCK;
        synchronized (object) {
            Path file = playerRewardsPath.resolve(playerUuid.toString() + ".dat");
            Path tempFile = playerRewardsPath.resolve(playerUuid.toString() + ".tmp");
            try {
                Files.createDirectories(playerRewardsPath, new FileAttribute[0]);
                int saveLevel = level;
                if (saveLevel < 0 && (saveLevel = PlayerRewardManager.getStoredLevel(playerUuid)) < 0) {
                    saveLevel = 0;
                }
                CompoundTag data = new CompoundTag();
                data.putInt(TAG_VERSION, 2);
                data.putInt(TAG_LEVEL, saveLevel);
                ListTag fullList = new ListTag();
                for (int i = 0; i < container.getContainerSize(); ++i) {
                    CompoundTag slotTag = new CompoundTag();
                    slotTag.putInt("Slot", i);
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty()) {
                        slotTag.put("Item", (Tag)stack.save(new CompoundTag()));
                    }
                    fullList.add(slotTag);
                }
                data.putInt(TAG_CONTAINER_SIZE, container.getContainerSize());
                System.out.println("[PlayerRewardManager] customSave: containerSize=" + container.getContainerSize() + " savedSlots=" + fullList.size() + " level=" + saveLevel);
                data.put(TAG_ITEMS, (Tag)fullList);
                NbtIo.writeCompressed((CompoundTag)data, (File)tempFile.toFile());
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[PlayerRewardManager] Saved reward data for " + String.valueOf(playerUuid) + " (size=" + container.getContainerSize() + ", level=" + saveLevel + ")");
            }
            catch (IOException e) {
                System.err.println("[PlayerRewardManager] ERROR saving reward data for " + String.valueOf(playerUuid));
                e.printStackTrace();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static int getStoredLevel(UUID playerUuid) {
        Path file = playerRewardsPath.resolve(playerUuid.toString() + ".dat");
        Object object = FILE_LOCK;
        synchronized (object) {
            try {
                if (Files.exists(file, new LinkOption[0]) && Files.size(file) > 0L) {
                    CompoundTag data = NbtIo.readCompressed((File)file.toFile());
                    return data.getInt(TAG_LEVEL);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
            return -1;
        }
    }

    /** Read an existing reward inventory without creating or modifying it. */
    public static Optional<SimpleContainer> loadExisting(UUID playerUuid) {
        synchronized (FILE_LOCK) {
            Path file = playerRewardsPath.resolve(playerUuid.toString() + ".dat");
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try {
                CompoundTag data = NbtIo.readCompressed(file.toFile());
                int size = data.getInt(TAG_CONTAINER_SIZE);
                if (size <= 0 || size > 10_000) {
                    return Optional.empty();
                }
                SimpleContainer snapshot = new SimpleContainer(size);
                ListTag items = data.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag slot = items.getCompound(i);
                    int index = slot.getInt("Slot");
                    if (index >= 0 && index < size && slot.contains("Item", Tag.TAG_COMPOUND)) {
                        snapshot.setItem(index, ItemStack.of(slot.getCompound("Item")));
                    }
                }
                return Optional.of(snapshot);
            } catch (IOException | RuntimeException e) {
                System.err.println("[PlayerRewardManager] ERROR reading reward data for " + playerUuid);
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }

    /** Remove the saved inventory so the next claim starts from the admin template. */
    public static boolean reset(UUID playerUuid) {
        synchronized (FILE_LOCK) {
            Path file = playerRewardsPath.resolve(playerUuid.toString() + ".dat");
            Path tempFile = playerRewardsPath.resolve(playerUuid.toString() + ".tmp");
            try {
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(file);
                return true;
            } catch (IOException e) {
                System.err.println("[PlayerRewardManager] ERROR resetting reward data for " + playerUuid);
                e.printStackTrace();
                return false;
            }
        }
    }
}
