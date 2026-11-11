package net.lanzr.time_reward.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.SimpleContainer;

public class ContainerStateManager {
    private static final ContainerStateManager INSTANCE = new ContainerStateManager();
    private final Map<Integer, BackpackState> backpacks = new ConcurrentHashMap<Integer, BackpackState>();

    private ContainerStateManager() {
    }

    public static ContainerStateManager getInstance() {
        return INSTANCE;
    }

    public void registerBackpack(int containerId, SimpleContainer storage) {
        this.backpacks.put(containerId, new BackpackState(storage, 0, 0));
    }

    public void unregisterBackpack(int containerId) {
        this.backpacks.remove(containerId);
    }

    public boolean isBackpackContainer(int containerId) {
        return this.backpacks.containsKey(containerId);
    }

    public SimpleContainer getStorageContainer(int containerId) {
        BackpackState state = this.backpacks.get(containerId);
        return state != null ? state.storage : null;
    }

    public int getLastOccupiedRow(int containerId) {
        BackpackState state = this.backpacks.get(containerId);
        return state != null ? state.lastOccupiedRow : 0;
    }

    public void setLastOccupiedRow(int containerId, int row) {
        this.backpacks.computeIfPresent(containerId, (id, state) -> new BackpackState(state.storage, row, state.scrollOffset));
    }

    public int getScrollOffset(int containerId) {
        BackpackState state = this.backpacks.get(containerId);
        return state != null ? state.scrollOffset : 0;
    }

    public void setScrollOffset(int containerId, int offset) {
        this.backpacks.computeIfPresent(containerId, (id, state) -> new BackpackState(state.storage, state.lastOccupiedRow, offset));
    }

    private record BackpackState(SimpleContainer storage, int lastOccupiedRow, int scrollOffset) {
    }
}
