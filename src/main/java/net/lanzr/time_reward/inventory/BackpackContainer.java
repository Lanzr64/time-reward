package net.lanzr.time_reward.inventory;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.init.ModMenuTypes;
import net.lanzr.time_reward.network.BackpackStatePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Server-side container for the backpack UI.
 * <p>
 * Displays a scrollable grid of {@link DynamicScrollSlot}s (12 columns × 4 visible rows)
 * backed by a {@link Container}, plus the player's inventory and hotbar.
 * Supports scrolling, sorting (NAME / COUNT / MOD), and auto-save on close.
 * </p>
 *
 * <h3>Construction</h3>
 * <ul>
 *   <li><b>Server</b>: {@link #BackpackContainer(int, Inventory, Container, int)} —
 *       accepts the real storage container and initial scroll offset.</li>
 *   <li><b>Client</b>: {@link #BackpackContainer(int, Inventory, FriendlyByteBuf)} —
 *       used by {@link net.neoforged.neoforge.common.extensions.IMenuTypeExtension}
 *       (registered in {@link ModMenuTypes}). Reads scroll offset and container size
 *       from the network buffer, creates a dummy container.</li>
 * </ul>
 *
 * <h3>Slot Layout</h3>
 * <pre>
 *   Rows 0-3:  DynamicScrollSlots (48 slots, indices 0-47)
 *   Row 4-6:   Player inventory   (27 slots, indices 48-74)
 *   Row 7:     Hotbar             ( 9 slots, indices 75-83)
 * </pre>
 *
 * <p><b>Scroll offset sync strategy</b>: the offset is a plain {@code int} field.
 * The client screen manages its local offset (initially from the buffer, then
 * updated on scroll input). When the server processes a scroll (via C2S packet),
 * it calls {@link #setScrollOffset(int)} which updates the field and triggers
 * {@link #broadcastChanges()} so all display-slot items are re-sent at the new
 * offset — the client already has the correct offset when items arrive, avoiding
 * a data-vs-slot sync ordering hazard.</p>
 */
public class BackpackContainer extends AbstractContainerMenu {

    // ========== Layout Constants ==========

    /** Number of columns in the scrollable grid. */
    public static final int COLS = 12;
    /** Number of visible rows in the scrollable grid. */
    public static final int MAX_VISIBLE_ROWS = 12;
    /** Total display slots (12 × MAX_VISIBLE_ROWS). */
    public static final int TOTAL_DISPLAY_SLOTS = COLS * MAX_VISIBLE_ROWS;
    /** First player-inventory slot index. */
    public static final int PLAYER_INV_START = TOTAL_DISPLAY_SLOTS;        // 144
    /** First hotbar slot index. */
    public static final int HOTBAR_START = PLAYER_INV_START + 27;          // 171
    /** Total number of slots in this menu. */
    public static final int TOTAL_SLOTS = HOTBAR_START + 9;                // 180

    // ========== Fields ==========

    private final Container storageContainer;
    private final Player player;
    private Runnable saveCallback = () -> { };

    /** Current scroll offset in rows. Managed locally on each side. */
    private int scrollOffset;

    /** Index of the last row (0-based) that contains items, set by server when opening. */
    private int lastOccupiedRow;

    /**
     * Dirty flag set whenever the storage container's occupied-row boundary may
     * have changed (item added/removed/sorted). The {@link #broadcastChanges()}
     * override consults this flag to decide whether an O(n) re-scan of the
     * container is warranted before sending updates to the client. This
     * dirty-gating ensures the recompute does NOT fire every tick &mdash; only
     * after a known mutation path (sort, scroll-set, shift-click move).
     */
    private boolean lastOccupiedDirty = true;

    /**
     * Cached value of {@link #lastOccupiedRow} that was last shipped to the
     * viewer via {@link BackpackStatePayload}. Used for delta-detection inside
     * {@link #broadcastChanges()} so the S2C packet only fires when the value
     * actually changes, never on every dirty tick.
     */
    private int lastSentLastOccupiedRow;

    /**
     * Per-instance counter incremented each time {@link #broadcastChanges()}
     * invokes {@link #recomputeLastOccupiedRow()}. Logged for QA verification
     * of dirty-gating cadence &mdash; should NOT increase once per server tick,
     * only on mutation-triggered broadcasts.
     */
    private int recomputeCallCount = 0;

    // ========== Constructors ==========

    /**
     * Server-side constructor.
     *
     * @param id             container id
     * @param playerInventory the opening player's inventory
     * @param container      the real storage container to read/write items
     * @param scrollOffset   initial scroll offset (rows)
     */
    public BackpackContainer(int id, Inventory playerInventory, Container container, int scrollOffset) {
        super(ModMenuTypes.get(), id);
        this.player = playerInventory.player;
        this.storageContainer = container;
        this.scrollOffset = scrollOffset;
        setupSlots(playerInventory);
        // Seed the server-side lastOccupiedRow field from a fresh scan so the
        // first broadcastChanges() has no spurious delta to send (the open-time
        // payload written into the client buffer by ServerPayloadHandler already
        // carried this same value to the client).
        this.lastOccupiedRow = recomputeLastOccupiedRow();
        this.lastSentLastOccupiedRow = this.lastOccupiedRow;
        this.lastOccupiedDirty = false;
    }

    /**
     * Client-side constructor, invoked by {@code IMenuTypeExtension.create()} from
     * the network buffer.
     *
     * @param id             container id
     * @param playerInventory the client player's inventory
     * @param buf            network buffer containing {@code containerSize} then {@code scrollOffset}
     */
    public BackpackContainer(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ModMenuTypes.get(), id);
        this.player = playerInventory.player;
        int containerSize = buf.readInt();
        this.storageContainer = new SimpleContainer(containerSize);
        this.scrollOffset = buf.readInt();
        this.lastOccupiedRow = buf.readInt();
        setupSlots(playerInventory);
    }

    // ========== Slot Layout ==========

    /**
     * Creates and adds all slots:
     * <ol>
     *   <li>{@code TOTAL_DISPLAY_SLOTS} {@link DynamicScrollSlot}s (visible grid)</li>
     *   <li>3 rows of player inventory (27 slots)</li>
     *   <li>1 row of hotbar (9 slots)</li>
     * </ol>
     */
    private void setupSlots(Inventory playerInventory) {
        // ---- Scrollable display grid ----
        for (int row = 0; row < MAX_VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int displayIndex = row * COLS + col;
                addSlot(new DynamicScrollSlot(
                        storageContainer, displayIndex, COLS,
                        this::getScrollOffset,
                        8 + col * 18, 18 + row * 18));
            }
        }

        // ---- Player inventory (3 rows × 9 cols) ----
        int playerInvX = 8 + 30; // 8 + PLAYER_INV_X_OFFSET for WIDER_12_SLOT layout
        int playerInvY = 18 + MAX_VISIBLE_ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + col + row * 9,
                        playerInvX + col * 18, playerInvY + row * 18));
            }
        }

        // ---- Hotbar (1 row × 9 cols) ----
        int hotbarY = playerInvY + 3 * 18 + 4;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    playerInvX + col * 18, hotbarY));
        }
    }

    // ========== Scroll Offset Management ==========

    /**
     * @return the current scroll offset in rows
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getLastOccupiedRow() {
        return lastOccupiedRow;
    }

    /**
     * @return the total number of slots in the backing storage container.
     * Delegates to {@link Container#getContainerSize()}.
     */
    public int getContainerSize() {
        return storageContainer.getContainerSize();
    }

    /**
     * Directly overwrites the {@code lastOccupiedRow} field <b>without</b> any
     * broadcast side effects. Intended for client-side re-clamp paths
     * (e.g. {@code ClientPayloadHandler}) where the caller drives re-clamp
     * separately after updating this field.
     *
     * @param row the new last-occupied-row value (use {@code -1} to denote
     *            "container fully empty")
     */
    public void setLastOccupiedRow(int row) {
        this.lastOccupiedRow = row;
    }

    /**
     * Server-side helper that scans {@code storageContainer} from the last
     * index down to {@code 0}, finds the highest occupied row, updates the
     * {@link #lastOccupiedRow} field in place, and returns the recomputed
     * value.
     * <p>
     * Semantics for an empty container: {@code -1} (NOT {@code 0}).
     *
     * @return the recomputed last-occupied-row index, or {@code -1} if the
     *         container is completely empty
     */
    public int recomputeLastOccupiedRow() {
        int containerSize = storageContainer.getContainerSize();
        for (int i = containerSize - 1; i >= 0; i--) {
            if (!storageContainer.getItem(i).isEmpty()) {
                this.lastOccupiedRow = i / COLS;
                return this.lastOccupiedRow;
            }
        }
        this.lastOccupiedRow = -1;
        return -1;
    }

    /**
     * Directly sets the scroll offset on the client side (called by the screen on scroll).
     * This is fast — just a field update — so the client can render immediately before
     * the server acknowledges the scroll.
     */
    public void setClientScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, Math.min(offset, getMaxScrollOffset()));
    }

    /**
     * Sets the scroll offset on the server, clamps to the valid range, and immediately
     * triggers {@link #broadcastChanges()} so that display-slot items are re-sent at
     * the new offset.
     */
    public void setScrollOffset(int offset) {
        this.scrollOffset = clampScrollOffset(offset);
        // Scroll itself doesn't mutate items, but the subsequent broadcastChanges
        // may need to ship the current lastOccupiedRow alongside the slot
        // refresh. Mark dirty cheaply; the re-scan only fires if not already
        // gated stale (after the scan dirty is cleared, so subsequent ticks
        // without further mutations do not re-scan).
        this.lastOccupiedDirty = true;
        broadcastChanges();
    }

    /**
     * Adjusts the scroll offset by {@code delta} rows (positive = scroll down)
     * and broadcasts the resulting item changes.
     */
    public void onScroll(int delta) {
        setScrollOffset(this.scrollOffset + delta);
    }

    /**
     * @return the maximum scroll offset (rows), or 0 if all rows fit on screen
     */
    public int getMaxScrollOffset() {
        int totalRows = (storageContainer.getContainerSize() + COLS - 1) / COLS;
        return Math.max(0, totalRows - MAX_VISIBLE_ROWS);
    }

    private int clampScrollOffset(int offset) {
        return Math.max(0, Math.min(offset, getMaxScrollOffset()));
    }

    // ========== Sorting ==========

    /** Sort criteria for the backpack contents. */
    public enum SortType {
        NAME,
        COUNT,
        MOD
    }

    /**
     * Sorts the items in the storage container by the given criterion,
     * then resets the scroll position to the top and broadcasts changes.
     */
    public void sort(SortType type) {
        // Mark dirty at start so the broadcastChanges call inside
        // setScrollOffset(0) below will pick up the freshly-sorted layout.
        this.lastOccupiedDirty = true;

        // Gather all items from the container
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < storageContainer.getContainerSize(); i++) {
            items.add(storageContainer.getItem(i));
        }

        // Separate non-empty items for sorting
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                nonEmpty.add(stack);
            }
        }

        // Build comparator
        Comparator<ItemStack> comparator = buildSortComparator(type);
        nonEmpty.sort(comparator);

        // Write sorted items back to the container
        int idx = 0;
        for (ItemStack stack : nonEmpty) {
            storageContainer.setItem(idx++, stack.copy());
        }
        // Fill remaining slots with empty stacks
        for (; idx < storageContainer.getContainerSize(); idx++) {
            storageContainer.setItem(idx, ItemStack.EMPTY);
        }

        // Reset scroll to top (triggers broadcastChanges internally — that call
        // will recompute lastOccupiedRow once because we marked dirty above and
        // send a BackpackStatePayload to the player if delta detected).
        setScrollOffset(0);

        // Final authoritative recompute + delta-send at sort end. This is
        // redundant with the broadcast inside setScrollOffset(0) but is the
        // canonical "sort result" anchor — the delta-detection against
        // lastSentLastOccupiedRow guarantees we only ship one BackpackStatePayload
        // for the whole sort operation.
        int newRow = recomputeLastOccupiedRow();
        TimeReward.LOGGER.info(
                "[BackpackContainer] recompute result post-sort lastOccupiedRow={}",
                newRow);
        if (newRow != lastSentLastOccupiedRow) {
            lastSentLastOccupiedRow = newRow;
            sendBackpackStateToPlayer(newRow);
        }
        this.lastOccupiedDirty = false;
    }

    private static Comparator<ItemStack> buildSortComparator(SortType type) {
        switch (type) {
            case NAME:
                return Comparator.comparing(
                        stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            case COUNT:
                return Comparator.<ItemStack>comparingInt(ItemStack::getCount)
                        .reversed()
                        .thenComparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            case MOD:
                return Comparator.<ItemStack, String>comparing(
                                stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                        .thenComparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            default:
                return (a, b) -> 0;
        }
    }

    // ========== Shift-Click (Quick Move) ==========

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();

        if (slotIndex < PLAYER_INV_START) {
            // Display grid → player inventory
            if (!moveItemStackTo(stackInSlot, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory → display grid
            if (!moveItemStackTo(stackInSlot, 0, TOTAL_DISPLAY_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        // Either direction mutates the backing storageContainer's occupied-row
        // boundary (item removed when source is in displayGrid; item added when
        // destination is in displayGrid). Mark dirty so the next
        // broadcastChanges() (invoked by the vanilla container-sync tick after
        // this click) re-scans and pushes BackpackStatePayload if changed.
        this.lastOccupiedDirty = true;

        return result;
    }

    // ========== Broadcast / Sync ==========

    /**
     * Vanilla container-sync hook. Delegates to {@code super} first so all
     * standard slot/state sync takes place, then consults the
     * {@link #lastOccupiedDirty} flag to decide whether an O(n) re-scan of the
     * storage container is warranted. When the recomputed lastOccupiedRow
     * differs from the last value shipped to the viewer
     * ({@link #lastSentLastOccupiedRow}), a {@link BackpackStatePayload} is
     * sent.
     *
     * <p>Dirty-gating ensures this scan does NOT run every tick &mdash; only on
     * ticks immediately following a known mutation path
     * ({@link #sort(SortType)}, {@link #setScrollOffset(int)},
     * {@link #quickMoveStack(Player, int)}).</p>
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.lastOccupiedDirty) {
            int row = recomputeLastOccupiedRow();
            recomputeCallCount++;
            TimeReward.LOGGER.info(
                    "[BackpackContainer] recompute-call-count n={} row={}",
                    recomputeCallCount, row);
            if (row != lastSentLastOccupiedRow) {
                lastSentLastOccupiedRow = row;
                sendBackpackStateToPlayer(row);
            }
            this.lastOccupiedDirty = false;
        }
    }

    /**
     * Ships a {@link BackpackStatePayload} carrying the new lastOccupiedRow to
     * this container's viewer. No-op when the viewer is not a server player
     * (e.g. the client-side dummy instance of this menu). Per spec, only the
     * single owning viewer is notified &mdash; do not broadcast to other players.
     */
    private void sendBackpackStateToPlayer(int row) {
        if (this.player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new BackpackStatePayload(containerId, row));
        }
    }

    // ========== Lifecycle ==========

    @Override
    public void removed(Player player) {
        super.removed(player);
        saveCallback.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ========== Callbacks & Accessors ==========

    /**
     * Registers a callback invoked when the container is closed.
     * Typically used to persist the container to disk via {@link net.lanzr.time_reward.save.PlayerRewardManager}.
     */
    public void setSaveCallback(Runnable callback) {
        this.saveCallback = callback;
    }

    /** Returns the backing storage container. */
    public Container getStorageContainer() {
        return storageContainer;
    }
}
