package net.lanzr.time_reward.client.gui;

import com.mojang.blaze3d.vertex.Tesselator;
import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.ScrollChangePayload;
import net.lanzr.time_reward.network.SortPayload;
import net.lanzr.time_reward.network.BackpackClosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.widget.ScrollPanel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Client-side backpack GUI screen with scrollable grid, search box, and sort buttons.
 *
 * <p>Renders a 12-column scrollable grid of inventory slots backed by
 * {@link net.lanzr.time_reward.inventory.DynamicScrollSlot}s. The background uses
 * a custom texture in three sections: header, tileable slot area, and player inventory
 * footer. Scrolling is handled by a NeoForge {@link ScrollPanel} that adjusts slot
 * Y-positions. A search box and sort button sit in the header.</p>
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackContainer> {

    // ======================== Constants ========================

    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("time_reward", "textures/gui/storage_background_12_wider.png");
    private static final ResourceLocation SLOTS_BG =
            ResourceLocation.fromNamespaceAndPath("time_reward", "textures/gui/slots_background.png");

    private static final int SLOTS_X_OFFSET               = 7;
    private static final int SLOTS_Y_OFFSET               = 17;
    private static final int SLOT_SIZE                    = 18;
    private static final int TEXTURE_SIZE                 = 256;
    private static final int HEIGHT_WITHOUT_STORAGE_SLOTS = 114;
    private static final int PLAYER_INV_X_OFFSET          = 30;

    private static final int COLS         = BackpackContainer.COLS;

    private static final int TOTAL_DISPLAY_SLOTS = BackpackContainer.TOTAL_DISPLAY_SLOTS;

    /** X value assigned to slots that fail the search filter (hidden off-screen left). */
    private static final int DISABLED_SLOT_X = -2000;
    /** Y value assigned to slots scrolled out of the viewport (hidden off-screen upward). */
    private static final int HIDDEN_SLOT_Y   = -2000;

    // ======================== Sort Mode ========================

    public enum SortBy {
        NAME, COUNT, MOD;

        public SortBy next() {
            return switch (this) {
                case NAME  -> COUNT;
                case COUNT -> MOD;
                case MOD   -> NAME;
            };
        }
    }

    // ======================== Fields ========================

    private BackpackScrollPanel scrollPanel;
    private EditBox searchBox;

    private SortBy currentSort = SortBy.NAME;

    /** Composite predicate built from the search phrase. */
    private Predicate<ItemStack> stackFilter = stack -> true;

    /** Number of storage slots matching both viewport and filter. */
    private int visibleSlotsCount;

    /** Number of currently visible rows, calculated from screen height. */
    private int visibleRows;

    // ======================== Constructor ========================

    public BackpackScreen(BackpackContainer menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        this.visibleRows = Math.max(4, Math.min(BackpackContainer.MAX_VISIBLE_ROWS, (screenHeight - HEIGHT_WITHOUT_STORAGE_SLOTS) / 18));

        this.imageWidth  = COLS * SLOT_SIZE + 2 * SLOTS_X_OFFSET + 6;  // 12*18+14+6 = 236
        this.imageHeight = HEIGHT_WITHOUT_STORAGE_SLOTS + visibleRows * SLOT_SIZE;

        this.titleLabelX      = SLOTS_X_OFFSET;
        this.titleLabelY      = 6;
        this.inventoryLabelX  = SLOTS_X_OFFSET;
        this.inventoryLabelY  = imageHeight - 94;

        this.visibleSlotsCount = 0;
    }

    // ======================== Initialisation ========================

    @Override
    protected void init() {
        super.init();
        initScrollPanel();
        initSearchBox();
        initSortButton();
        updateSlotsPosition();
        menu.setClientVisibleRows(this.visibleRows);
    }

    private void initScrollPanel() {
        if (scrollPanel != null) {
            removeWidget(scrollPanel);
        }

        int panelWidth  = COLS * SLOT_SIZE + 6;
        int panelHeight = visibleRows * SLOT_SIZE;
        int panelTop    = topPos + SLOTS_Y_OFFSET;
        int panelLeft   = leftPos + SLOTS_X_OFFSET;

        // Always create scroll panel for full container capacity
        int contentRows = menu.getContainerSize() / BackpackContainer.COLS;
        if (contentRows * SLOT_SIZE <= panelHeight) {
            scrollPanel = null;
            return;
        }

        scrollPanel = new BackpackScrollPanel(
                Minecraft.getInstance(), panelWidth, panelHeight, panelTop, panelLeft
        );
        addRenderableWidget(scrollPanel);
    }

    /** Search box positioned at the top-right of the header area. */
    private void initSearchBox() {
        int boxWidth  = Math.max(80, imageWidth - 110);
        int boxHeight = 14;
        int boxX      = leftPos + imageWidth - boxWidth - 8;
        int boxY      = topPos + 4;

        searchBox = new EditBox(font, boxX, boxY, boxWidth, boxHeight,
                Component.literal("搜索..."));
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setCanLoseFocus(true);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setResponder(this::onSearchTextChanged);
        addWidget(searchBox);
    }

    /** Sort button to the left of the search box. */
    private void initSortButton() {
        int btnSize = 14;
        int btnX    = (searchBox != null ? searchBox.getX() : leftPos + imageWidth - 30) - btnSize - 3;
        int btnY    = topPos + 4;

        addRenderableWidget(Button.builder(
                Component.literal(getSortButtonLabel()),
                btn -> cycleSort()
        ).bounds(btnX, btnY, btnSize, btnSize).build());
    }

    // ======================== Sort ========================

    private void cycleSort() {
        currentSort = currentSort.next();
        updateSortButtonLabel();
        onSortChanged();
    }

    private void updateSortButtonLabel() {
        Button btn = findSortButton();
        if (btn != null) {
            btn.setMessage(Component.literal(getSortButtonLabel()));
        }
    }

    private String getSortButtonLabel() {
        return switch (currentSort) {
            case NAME  -> "N";
            case COUNT -> "C";
            case MOD   -> "M";
        };
    }

    private Button findSortButton() {
        for (var widget : renderables) {
            if (widget instanceof Button btn) {
                return btn;
            }
        }
        return null;
    }

    /** Called when sort mode changes — resets scroll and notifies the server. */
    private void onSortChanged() {
        if (scrollPanel != null) {
            scrollPanel.resetScrollDistance();
            updateSlotsPosition();
        }
        PacketDistributor.sendToServer(new SortPayload(menu.containerId, currentSort.ordinal()));
    }

    // ======================== Search / Filter ========================

    private void onSearchTextChanged(String text) {
        updateStackFilter(text);
        if (scrollPanel != null) {
            scrollPanel.resetScrollDistance();
            updateSlotsPosition();
        }
    }

    /**
     * Builds a composite filter from a multi-term search phrase.
     *
     * <ul>
     *   <li>{@code "hello"} → match item display name (case-insensitive)</li>
     *   <li>{@code "@modid"} → match item registry namespace</li>
     *   <li>{@code "#keyword"} → match tooltip content</li>
     *   <li>Multiple terms are combined with AND logic</li>
     *   <li>Empty phrase shows all items</li>
     * </ul>
     */
    private void updateStackFilter(String searchPhrase) {
        String trimmed = searchPhrase.trim();
        if (trimmed.isEmpty()) {
            stackFilter = stack -> true;
            return;
        }

        String[] terms = trimmed.split("\\s+");
        List<Predicate<ItemStack>> predicates = Arrays.stream(terms)
                .map(this::buildSingleTermPredicate)
                .collect(Collectors.toList());

        stackFilter = stack -> !stack.isEmpty()
                && predicates.stream().allMatch(p -> p.test(stack));
    }

    private Predicate<ItemStack> buildSingleTermPredicate(String term) {
        if (term.startsWith("@")) {
            String modId = term.substring(1).toLowerCase();
            return stack -> modId.isEmpty()
                    || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(modId);
        } else if (term.startsWith("#")) {
            String keyword = term.substring(1).toLowerCase();
            return stack -> {
                List<Component> tooltip = getTooltipFromItem(minecraft, stack);
                return tooltip.stream().anyMatch(line ->
                        line.getString().toLowerCase().contains(keyword));
            };
        } else {
            String lower = term.toLowerCase();
            return stack -> stack.getHoverName().getString().toLowerCase().contains(lower);
        }
    }

    // ======================== Slot Position Management ========================

    /**
     * Recalculates screen positions of all display slots based on the current
     * scroll distance and active search filter.
     *
     * <p>Delegates to the inner scroll panel which has direct access to the
     * protected {@link ScrollPanel#scrollDistance} field.</p>
     */
    public void updateSlotsPosition() {
        if (scrollPanel != null) {
            scrollPanel.repositionSlots();
        }
    }

    /**
     * Called when the authoritative {@code lastOccupiedRow} of the bound container
     * changes (typically via the S2C {@code BackpackStatePayload}).
     *
     * <p>Re-evaluates whether a scroll panel should exist (it may have been created
     * when the container had many items, and now must be destroyed if the container
     * shrinks below the visible area), re-clamps the scroll distance against the new
     * content height, and finally repositions all display slots. The scroll distance
     * itself is NOT back-synced to the server — the client remains the source-of-truth
     * for the current scroll row position; only its bounds are tightened here.</p>
     */
    public void onLastOccupiedRowChanged() {
        TimeReward.LOGGER.info("[BackpackScreen-Diag] onLastOccupiedRowChanged() called, scrollPanel={}",
                scrollPanel != null ? "non-null" : "null");
        initScrollPanel();
        if (scrollPanel != null) {
            scrollPanel.reclamp();
        }
        updateSlotsPosition();
    }

    // ======================== Background Rendering ========================

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int slotsHeight = imageHeight - HEIGHT_WITHOUT_STORAGE_SLOTS; // 72 = 4 * 18
        int slotsTopBottomHeight = Math.min(slotsHeight / 2, 150); // 36

        // 1. Top section: header + part of slot area
        guiGraphics.blit(BG_TEXTURE, x, y, 0, 0, imageWidth, SLOTS_Y_OFFSET + slotsTopBottomHeight, TEXTURE_SIZE, TEXTURE_SIZE);

        int yOffset = 0;
        // Middle section not needed for 4 visible rows (slotsHeight/2=36 < 150)

        // 2. Bottom section: player inventory + remaining slot area
        int playerInvHeight = 97;
        guiGraphics.blit(BG_TEXTURE, x, y + yOffset + SLOTS_Y_OFFSET + slotsTopBottomHeight, 0,
            TEXTURE_SIZE - (playerInvHeight + slotsTopBottomHeight), imageWidth, playerInvHeight + slotsTopBottomHeight, TEXTURE_SIZE, TEXTURE_SIZE);

        // 3. Render slot cell backgrounds using SC tiling
        renderSlotCellBackgrounds(guiGraphics);
    }

    /**
     * Renders 18×18 slot cell backgrounds tiled from the SC slots_background texture.
     * Uses the same chunked blit pattern as SophisticatedCore's GuiHelper.renderSlotsBackground.
     */
    private void renderSlotCellBackgrounds(GuiGraphics guiGraphics) {
        int slotRows = visibleRows;
        int renderedY = 0;
        final int MAX_ROWS_PER_BLIT = 12;

        // Render slot backgrounds for all visible rows
        while (renderedY < slotRows) {
            int chunkRows = Math.min(MAX_ROWS_PER_BLIT, slotRows - renderedY);
            int chunkHeight = chunkRows * SLOT_SIZE;
            int chunkWidth = COLS * SLOT_SIZE;

            guiGraphics.blit(SLOTS_BG,
                leftPos + SLOTS_X_OFFSET, topPos + SLOTS_Y_OFFSET + renderedY * SLOT_SIZE,
                0, 0, chunkWidth, chunkHeight, TEXTURE_SIZE, TEXTURE_SIZE);
            renderedY += chunkRows;
        }

        // Cell-level mask: darken each cell whose actualIndex >= containerSize.
        // Mask coordinates are viewport-relative (move with cells, not with window).
        int containerSize = menu.getContainerSize();
        int scrollRowOffset = (scrollPanel != null) ? scrollPanel.getScrollRowOffset() : 0;
        for (int viewportRow = 0; viewportRow < visibleRows; viewportRow++) {
            int absRow = scrollRowOffset + viewportRow;
            int absRowStart = absRow * COLS;
            // fast path: whole row out-of-range → single fill rect
            if (absRowStart >= containerSize) {
                int cellX = leftPos + SLOTS_X_OFFSET;
                int cellY = topPos + SLOTS_Y_OFFSET + viewportRow * SLOT_SIZE;
                int rowWidth = COLS * SLOT_SIZE;
                guiGraphics.fill(cellX, cellY, cellX + rowWidth, cellY + SLOT_SIZE, 0x80000000);
                continue;
            }
            // partial row → per-cell fill for out-of-range cells in this row
            int absRowEnd = absRowStart + COLS;
            if (absRowEnd > containerSize) {
                int firstOutOfRangeCol = containerSize - absRowStart;
                for (int col = firstOutOfRangeCol; col < COLS; col++) {
                    int cellX = leftPos + SLOTS_X_OFFSET + col * SLOT_SIZE;
                    int cellY = topPos + SLOTS_Y_OFFSET + viewportRow * SLOT_SIZE;
                    guiGraphics.fill(cellX, cellY, cellX + SLOT_SIZE, cellY + SLOT_SIZE, 0x80000000);
                }
            }
            // else: entire row in-range — no mask
        }
    }

    // ======================== Resize Handling ========================

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        int newVisibleRows = Math.max(4, Math.min(BackpackContainer.MAX_VISIBLE_ROWS, (height - HEIGHT_WITHOUT_STORAGE_SLOTS) / 18));
        this.visibleRows = newVisibleRows;
        // 保存当前的滚动距离
        float savedScrollDistance = (scrollPanel != null) ? scrollPanel.getScrollDistance() : 0;
        menu.setClientVisibleRows(this.visibleRows);
        this.imageHeight = HEIGHT_WITHOUT_STORAGE_SLOTS + visibleRows * SLOT_SIZE;
        this.inventoryLabelY = imageHeight - 94;
        super.resize(minecraft, width, height);
        initScrollPanel();
        if (scrollPanel != null) {
            // 先恢复滚动距离再钳位，最后重排槽位
            scrollPanel.setScrollDistance(savedScrollDistance);
            scrollPanel.reclamp();
        }
        updateSlotsPosition();
        // 通知服务端当前滚动位置和可见行数，触发重新同步
        PacketDistributor.sendToServer(new net.lanzr.time_reward.network.ScrollChangePayload(
                menu.containerId, menu.getScrollOffset(), visibleRows));
    }

    // ======================== Main Render ========================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // NeoForge's patched AbstractContainerScreen.render() omits renderTooltip(),
        // so we must call it here for slot hover tooltips to show.
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        // Render the search box on top (high z-level to avoid being clipped)
        if (searchBox != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }
    }

    // ======================== Label Rendering ========================

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        guiGraphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    // ======================== Input Handling ========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256) { // Escape — unfocus the search box
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        // Ctrl+F to focus the search box
        if (searchBox != null && keyCode == 70 && hasControlDown()) {
            searchBox.setFocused(true);
            searchBox.setCursorPosition(0);
            searchBox.setHighlightPos(searchBox.getValue().length());
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Let vanilla AbstractContainerScreen handle the click - it sends ServerboundContainerClickPacket
        // which the server's BackpackContainer processes correctly via the real synchronizer
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollPanel != null && scrollPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollPanel != null && scrollPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollPanel != null && scrollPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ======================== Misc ========================

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new BackpackClosePayload(menu.containerId));
        super.onClose();
    }

    // ======================== Inner Class: BackpackScrollPanel ========================

    /**
     * NeoForge {@link ScrollPanel} that drives the scrollable storage-slot grid.
     *
     * <p>The panel provides the scrollbar UI and manages the {@code scrollDistance}
     * field. Whenever the distance changes (via mouse wheel or drag), it calls
     * {@link BackpackScreen#updateSlotsPosition()} to recalculate slot locations
     * and notifies the server of the new row offset.</p>
     */
    private class BackpackScrollPanel extends ScrollPanel {

        BackpackScrollPanel(Minecraft client, int width, int height, int top, int left) {
            super(client, width, height, top, left, 0);
        }

        @Override
        public net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority narrationPriority() {
            return net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            // no-op - scroll panel has no narration content
        }

        @Override
        protected int getScrollAmount() {
            return SLOT_SIZE; // one row per scroll notch
        }

        @Override
        protected int getContentHeight() {
            int totalRows = menu.getContainerSize() / BackpackContainer.COLS;
            return totalRows * SLOT_SIZE;
        }

        @Override
        protected void drawBackground(GuiGraphics guiGraphics, Tesselator tess, float partialTick) {
            // Slot backgrounds are rendered as part of the custom texture in renderBg()
        }

        @Override
        protected void drawPanel(GuiGraphics guiGraphics, int entryRight, int relativeY,
                                 Tesselator tess, int mouseX, int mouseY) {
            // Slot positions are managed by repositionSlots().
            // Minecraft's default slot rendering loop handles drawing.
            // Hidden slots use y=-2000 which is safely off-screen.
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            boolean handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            if (handled) {
                // Sync the client-side scrollOffset FIRST so
                // DynamicScrollSlot.getActualIndex() uses the correct offset
                // before repositioning slots and notifying the server.
                int rowOffset = (int) scrollDistance / SLOT_SIZE;
                menu.setClientScrollOffset(rowOffset);
                // Notify the server about the new scroll offset and visible rows
                PacketDistributor.sendToServer(
                        new ScrollChangePayload(menu.containerId, menu.getScrollOffset(), visibleRows));
                updateSlotsPosition();
            }
            return handled;
        }

        /**
         * Forces {@code scrollDistance} back within the valid [0, maxScroll] range
         * after the panel's content height has changed (e.g. when the server pushes
         * a new {@code lastOccupiedRow} or when the window is resized and
         * {@code visibleRows} shifts the panel height).
         *
         * <p>Mirrors NeoForge's private {@code ScrollPanel.getMaxScroll()} via the
         * accessible {@code getContentHeight()} override and the protected
         * {@code height}/{@code border} fields. Logs when the clamp actually moves
         * the distance to aid diagnosis. Does NOT reposition slots — the caller is
         * responsible for invoking {@link #repositionSlots()} afterwards so the
         * position update and any downstream server-notification stay coherent.</p>
         */
        void reclamp() {
            int maxScroll = getContentHeight() - (height - border);
            float oldDist = scrollDistance;
            scrollDistance = Math.max(0, Math.min(scrollDistance, maxScroll));
            if ((int) oldDist != (int) scrollDistance) {
                TimeReward.LOGGER.info(
                        "[BackpackScreen-Diag] scroll clamped from {} to {} (new max={})",
                        oldDist, scrollDistance, maxScroll);
            }
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            boolean handled = super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            if (handled) {
                updateSlotsPosition();
                // Sync the client-side scrollOffset immediately so
                // DynamicScrollSlot.getActualIndex() uses the correct offset
                // before the server acknowledges the scroll.
                int rowOffset = (int) scrollDistance / SLOT_SIZE;
                menu.setClientScrollOffset(rowOffset);
                PacketDistributor.sendToServer(
                        new ScrollChangePayload(menu.containerId, menu.getScrollOffset(), visibleRows));
            }
            return handled;
        }

        void resetScrollDistance() {
            scrollDistance = 0;
        }

        float getScrollDistance() {
            return scrollDistance;
        }

        void setScrollDistance(float dist) {
            scrollDistance = dist;
        }

        /** Returns the current row offset derived from the scroll distance. */
        int getScrollRowOffset() {
            return (int) scrollDistance / SLOT_SIZE;
        }

        /**
         * Iterates over every display slot and assigns screen coordinates based on
         * the active {@link #stackFilter}.
         *
         * <p>Each display slot has a fixed column ({@code index % COLS}) and a fixed
         * Y position based on its displayRow (viewport-relative). Scrolling is
         * handled by {@link net.lanzr.time_reward.inventory.DynamicScrollSlot}
         * shifting which actual storage row maps to each displayRow, so slot
         * positions remain static and only the content mapping changes. Slots
         * outside the viewport are hidden; items that fail the filter are moved
         * far off-screen to the left.</p>
         */
        void repositionSlots() {
            visibleSlotsCount = 0;

            for (int i = 0; i < TOTAL_DISPLAY_SLOTS; i++) {
                Slot slot = menu.getSlot(i);
                ItemStack stack = slot.getItem();

                boolean matchesFilter = stackFilter.test(stack);
                int col = i % COLS;
                int displayRow = i / COLS;
                int newY = SLOTS_Y_OFFSET + displayRow * SLOT_SIZE;

                if (!matchesFilter) {
                    // Hide filtered-out items far off-screen to the left
                    slot.x = DISABLED_SLOT_X;
                    slot.y = HIDDEN_SLOT_Y;
                } else if (newY < SLOTS_Y_OFFSET || newY >= SLOTS_Y_OFFSET + visibleRows * SLOT_SIZE) {
                    // Scrolled out of the visible viewport — hide vertically
                    slot.y = HIDDEN_SLOT_Y;
                    slot.x = SLOTS_X_OFFSET + col * SLOT_SIZE;
                } else {
                    slot.y = newY;
                    slot.x = SLOTS_X_OFFSET + col * SLOT_SIZE;
                    visibleSlotsCount++;
                }
            }

            // Reposition player inventory to sit right below the visible storage grid
            int playerInvTopY = SLOTS_Y_OFFSET + visibleRows * SLOT_SIZE + 14;
            int playerInvX = 8 + PLAYER_INV_X_OFFSET;

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    Slot slot = menu.getSlot(BackpackContainer.PLAYER_INV_START + row * 9 + col);
                    slot.y = playerInvTopY + row * SLOT_SIZE;
                    slot.x = playerInvX + col * SLOT_SIZE;
                }
            }
            for (int col = 0; col < 9; col++) {
                Slot slot = menu.getSlot(BackpackContainer.HOTBAR_START + col);
                slot.y = playerInvTopY + 3 * SLOT_SIZE + 4;
                slot.x = playerInvX + col * SLOT_SIZE;
            }
        }
    }
}
