package net.lanzr.time_reward.client;

import net.minecraft.world.SimpleContainer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side manager for tracking which open containers are custom backpack containers
 * (as opposed to vanilla chests). Provides thread-safe access to per-container state
 * including the storage container, last occupied row, and scroll offset.
 *
 * <p>All state is stored in a {@link ConcurrentHashMap} keyed by the container ID,
 * making all read and write operations safe for concurrent access from Netty event-loop
 * threads and the client render thread.</p>
 */
public class ContainerStateManager {

    private static final ContainerStateManager INSTANCE = new ContainerStateManager();

    private final Map<Integer, BackpackState> backpacks = new ConcurrentHashMap<>();

    // ======================== Singleton ========================

    private ContainerStateManager() {
    }

    /**
     * @return the singleton instance
     */
    public static ContainerStateManager getInstance() {
        return INSTANCE;
    }

    // ======================== Registration ========================

    /**
     * Registers a container as a backpack with the given storage.
     * The last occupied row and scroll offset are initialised to 0.
     *
     * @param containerId the container ID
     * @param storage     the storage backing the container
     */
    public void registerBackpack(int containerId, SimpleContainer storage) {
        backpacks.put(containerId, new BackpackState(storage, 0, 0));
    }

    /**
     * Removes the tracking for the given container ID.
     *
     * @param containerId the container ID
     */
    public void unregisterBackpack(int containerId) {
        backpacks.remove(containerId);
    }

    // ======================== Queries ========================

    /**
     * @param containerId the container ID
     * @return {@code true} if the container is tracked as a backpack
     */
    public boolean isBackpackContainer(int containerId) {
        return backpacks.containsKey(containerId);
    }

    /**
     * @param containerId the container ID
     * @return the storage container, or {@code null} if not tracked
     */
    public SimpleContainer getStorageContainer(int containerId) {
        BackpackState state = backpacks.get(containerId);
        return state != null ? state.storage : null;
    }

    // ======================== Last Occupied Row ========================

    /**
     * @param containerId the container ID
     * @return the last occupied row, or {@code 0} if not tracked
     */
    public int getLastOccupiedRow(int containerId) {
        BackpackState state = backpacks.get(containerId);
        return state != null ? state.lastOccupiedRow : 0;
    }

    /**
     * Sets the last occupied row for the given container.
     *
     * @param containerId the container ID
     * @param row         the new last occupied row
     */
    public void setLastOccupiedRow(int containerId, int row) {
        backpacks.computeIfPresent(containerId, (id, state) ->
                new BackpackState(state.storage, row, state.scrollOffset));
    }

    // ======================== Scroll Offset ========================

    /**
     * @param containerId the container ID
     * @return the current scroll offset, or {@code 0} if not tracked
     */
    public int getScrollOffset(int containerId) {
        BackpackState state = backpacks.get(containerId);
        return state != null ? state.scrollOffset : 0;
    }

    /**
     * Sets the scroll offset for the given container.
     *
     * @param containerId the container ID
     * @param offset      the new scroll offset
     */
    public void setScrollOffset(int containerId, int offset) {
        backpacks.computeIfPresent(containerId, (id, state) ->
                new BackpackState(state.storage, state.lastOccupiedRow, offset));
    }

    // ======================== Inner Record ========================

    private record BackpackState(SimpleContainer storage, int lastOccupiedRow, int scrollOffset) {
    }
}
