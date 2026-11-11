package net.lanzr.time_reward.client.gui;

import com.mojang.blaze3d.vertex.Tesselator;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.inventory.BackpackContainer;
import net.lanzr.time_reward.network.BackpackClosePayload;
import net.lanzr.time_reward.network.ModNetwork;
import net.lanzr.time_reward.network.ScrollChangePayload;
import net.lanzr.time_reward.network.SortPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.widget.ScrollPanel;

public class BackpackScreen
extends AbstractContainerScreen<BackpackContainer> {
    private static final ResourceLocation BG_TEXTURE = new ResourceLocation("time_reward", "textures/gui/storage_background_12_wider.png");
    private static final ResourceLocation SLOTS_BG = new ResourceLocation("time_reward", "textures/gui/slots_background.png");
    private static final int SLOTS_X_OFFSET = 7;
    private static final int SLOTS_Y_OFFSET = 17;
    private static final int SLOT_SIZE = 18;
    private static final int TEXTURE_SIZE = 256;
    private static final int HEIGHT_WITHOUT_STORAGE_SLOTS = 114;
    private static final int PLAYER_INV_X_OFFSET = 30;
    private static final int COLS = 12;
    private static final int TOTAL_DISPLAY_SLOTS = 144;
    private static final int DISABLED_SLOT_X = -2000;
    private static final int HIDDEN_SLOT_Y = -2000;
    private BackpackScrollPanel scrollPanel;
    private EditBox searchBox;
    private SortBy currentSort = SortBy.NAME;
    private Predicate<ItemStack> stackFilter = stack -> true;
    private int visibleSlotsCount;
    private int visibleRows;

    private static int rowsForHeight(int height) {
        return Math.max(1, Math.min(12, (height - HEIGHT_WITHOUT_STORAGE_SLOTS) / SLOT_SIZE));
    }

    public BackpackScreen(BackpackContainer menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        this.visibleRows = rowsForHeight(screenHeight);
        this.imageWidth = 236;
        this.imageHeight = 114 + this.visibleRows * 18;
        this.titleLabelX = 7;
        this.titleLabelY = 6;
        this.inventoryLabelX = 7;
        this.inventoryLabelY = this.imageHeight - 94;
        this.visibleSlotsCount = 0;
    }

    protected void init() {
        super.init();
        ((BackpackContainer)this.menu).setClientVisibleRows(this.visibleRows);
        this.initScrollPanel();
        this.initSearchBox();
        this.initSortButton();
        this.updateSlotsPosition();
    }

    private void initScrollPanel() {
        if (this.scrollPanel != null) {
            this.removeWidget((GuiEventListener)this.scrollPanel);
        }
        int panelWidth = 222;
        int panelHeight = this.visibleRows * 18;
        int panelTop = this.topPos + 17;
        int panelLeft = this.leftPos + 7;
        int contentRows = (((BackpackContainer)this.menu).getContainerSize() + COLS - 1) / COLS;
        if (contentRows * 18 <= panelHeight) {
            this.scrollPanel = null;
            return;
        }
        this.scrollPanel = new BackpackScrollPanel(Minecraft.getInstance(), panelWidth, panelHeight, panelTop, panelLeft);
        this.scrollPanel.setScrollDistance(((BackpackContainer)this.menu).getScrollOffset() * SLOT_SIZE);
        this.scrollPanel.reclamp();
        this.addRenderableWidget(this.scrollPanel);
    }

    private void initSearchBox() {
        int boxWidth = Math.max(80, this.imageWidth - 110);
        int boxHeight = 14;
        int boxX = this.leftPos + this.imageWidth - boxWidth - 8;
        int boxY = this.topPos + 4;
        this.searchBox = new EditBox(this.font, boxX, boxY, boxWidth, boxHeight, (Component)Component.literal((String)"\u641c\u7d22..."));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setResponder(this::onSearchTextChanged);
        this.addWidget(this.searchBox);
    }

    private void initSortButton() {
        int btnSize = 14;
        int btnX = (this.searchBox != null ? this.searchBox.getX() : this.leftPos + this.imageWidth - 30) - btnSize - 3;
        int btnY = this.topPos + 4;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)this.getSortButtonLabel()), btn -> this.cycleSort()).bounds(btnX, btnY, btnSize, btnSize).build());
    }

    private void cycleSort() {
        this.currentSort = this.currentSort.next();
        this.updateSortButtonLabel();
        this.onSortChanged();
    }

    private void updateSortButtonLabel() {
        Button btn = this.findSortButton();
        if (btn != null) {
            btn.setMessage((Component)Component.literal((String)this.getSortButtonLabel()));
        }
    }

    private String getSortButtonLabel() {
        return switch (this.currentSort) {
            default -> throw new IncompatibleClassChangeError();
            case NAME -> "N";
            case COUNT -> "C";
            case MOD -> "M";
        };
    }

    private Button findSortButton() {
        for (Renderable widget : this.renderables) {
            if (!(widget instanceof Button)) continue;
            Button btn = (Button)widget;
            return btn;
        }
        return null;
    }

    private void onSortChanged() {
        this.resetScrollToTop();
        ModNetwork.sendToServer(new SortPayload(((BackpackContainer)this.menu).containerId, this.currentSort.ordinal()));
    }

    private void onSearchTextChanged(String text) {
        this.updateStackFilter(text);
        this.resetScrollToTop();
    }

    private void resetScrollToTop() {
        if (this.scrollPanel != null) {
            this.scrollPanel.resetScrollDistance();
        }
        this.setScrollRowOffset(0);
    }

    private void setScrollRowOffset(int row) {
        BackpackContainer backpack = (BackpackContainer)this.menu;
        int previous = backpack.getScrollOffset();
        backpack.setClientScrollOffset(row);
        if (this.scrollPanel != null) {
            this.scrollPanel.setScrollDistance(backpack.getScrollOffset() * SLOT_SIZE);
        }
        this.updateSlotsPosition();
        if (previous != backpack.getScrollOffset()) {
            ModNetwork.sendToServer(new ScrollChangePayload(backpack.containerId, backpack.getScrollOffset(), this.visibleRows));
        }
    }

    private void updateStackFilter(String searchPhrase) {
        String trimmed = searchPhrase.trim();
        if (trimmed.isEmpty()) {
            this.stackFilter = stack -> true;
            return;
        }
        String[] terms = trimmed.split("\\s+");
        List<Predicate<ItemStack>> predicates = Arrays.stream(terms).map(this::buildSingleTermPredicate).collect(Collectors.toList());
        this.stackFilter = stack -> !stack.isEmpty() && predicates.stream().allMatch(p -> p.test(stack));
    }

    private Predicate<ItemStack> buildSingleTermPredicate(String term) {
        if (term.startsWith("@")) {
            String modId = term.substring(1).toLowerCase();
            return stack -> modId.isEmpty() || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(modId);
        }
        if (term.startsWith("#")) {
            String keyword = term.substring(1).toLowerCase();
            return stack -> {
                List<Component> tooltip = BackpackScreen.getTooltipFromItem((Minecraft)this.minecraft, (ItemStack)stack);
                return tooltip.stream().anyMatch(line -> line.getString().toLowerCase().contains(keyword));
            };
        }
        String lower = term.toLowerCase();
        return stack -> stack.getHoverName().getString().toLowerCase().contains(lower);
    }

    public void updateSlotsPosition() {
        this.visibleSlotsCount = 0;
        for (int i = 0; i < TOTAL_DISPLAY_SLOTS; ++i) {
            Slot slot = ((BackpackContainer)this.menu).getSlot(i);
            int row = i / COLS;
            if (row >= this.visibleRows || !this.stackFilter.test(slot.getItem())) {
                slot.x = DISABLED_SLOT_X;
                slot.y = HIDDEN_SLOT_Y;
                continue;
            }
            slot.x = SLOTS_X_OFFSET + i % COLS * SLOT_SIZE;
            slot.y = SLOTS_Y_OFFSET + row * SLOT_SIZE;
            ++this.visibleSlotsCount;
        }
        int playerInvTopY = SLOTS_Y_OFFSET + this.visibleRows * SLOT_SIZE + 14;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                Slot slot = ((BackpackContainer)this.menu).getSlot(144 + row * 9 + col);
                slot.x = 38 + col * SLOT_SIZE;
                slot.y = playerInvTopY + row * SLOT_SIZE;
            }
        }
        for (int col = 0; col < 9; ++col) {
            Slot slot = ((BackpackContainer)this.menu).getSlot(171 + col);
            slot.x = 38 + col * SLOT_SIZE;
            slot.y = playerInvTopY + 58;
        }
    }

    public void onLastOccupiedRowChanged() {
        // The storage capacity has not changed; rebuilding the panel would reset its scroll position.
        this.updateSlotsPosition();
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int slotsHeight = this.imageHeight - 114;
        int slotsTopBottomHeight = Math.min(slotsHeight / 2, 150);
        guiGraphics.blit(BG_TEXTURE, x, y, 0.0f, 0.0f, this.imageWidth, 17 + slotsTopBottomHeight, 256, 256);
        int yOffset = 0;
        int playerInvHeight = 97;
        guiGraphics.blit(BG_TEXTURE, x, y + yOffset + 17 + slotsTopBottomHeight, 0.0f, (float)(256 - (playerInvHeight + slotsTopBottomHeight)), this.imageWidth, playerInvHeight + slotsTopBottomHeight, 256, 256);
        this.renderSlotCellBackgrounds(guiGraphics);
    }

    private void renderSlotCellBackgrounds(GuiGraphics guiGraphics) {
        int chunkRows;
        int slotRows = this.visibleRows;
        int MAX_ROWS_PER_BLIT = 12;
        for (int renderedY = 0; renderedY < slotRows; renderedY += chunkRows) {
            chunkRows = Math.min(12, slotRows - renderedY);
            int chunkHeight = chunkRows * 18;
            int chunkWidth = 216;
            guiGraphics.blit(SLOTS_BG, this.leftPos + 7, this.topPos + 17 + renderedY * 18, 0.0f, 0.0f, chunkWidth, chunkHeight, 256, 256);
        }
        int containerSize = ((BackpackContainer)this.menu).getContainerSize();
        int scrollRowOffset = this.scrollPanel != null ? this.scrollPanel.getScrollRowOffset() : 0;
        for (int viewportRow = 0; viewportRow < this.visibleRows; ++viewportRow) {
            int firstOutOfRangeCol;
            int absRow = scrollRowOffset + viewportRow;
            int absRowStart = absRow * 12;
            if (absRowStart >= containerSize) {
                int cellX = this.leftPos + 7;
                int cellY = this.topPos + 17 + viewportRow * 18;
                int rowWidth = 216;
                guiGraphics.fill(cellX, cellY, cellX + rowWidth, cellY + 18, Integer.MIN_VALUE);
                continue;
            }
            int absRowEnd = absRowStart + 12;
            if (absRowEnd <= containerSize) continue;
            for (int col = firstOutOfRangeCol = containerSize - absRowStart; col < 12; ++col) {
                int cellX = this.leftPos + 7 + col * 18;
                int cellY = this.topPos + 17 + viewportRow * 18;
                guiGraphics.fill(cellX, cellY, cellX + 18, cellY + 18, Integer.MIN_VALUE);
            }
        }
    }

    public void resize(Minecraft minecraft, int width, int height) {
        this.visibleRows = rowsForHeight(height);
        this.imageHeight = HEIGHT_WITHOUT_STORAGE_SLOTS + this.visibleRows * SLOT_SIZE;
        this.inventoryLabelY = this.imageHeight - 94;
        ((BackpackContainer)this.menu).setClientVisibleRows(this.visibleRows);
        ((BackpackContainer)this.menu).setClientScrollOffset(((BackpackContainer)this.menu).getScrollOffset());
        super.resize(minecraft, width, height);
        ModNetwork.sendToServer(new ScrollChangePayload(((BackpackContainer)this.menu).containerId, ((BackpackContainer)this.menu).getScrollOffset(), this.visibleRows));
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        if (this.searchBox != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0f, 0.0f, 200.0f);
            this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == 256) {
                this.searchBox.setFocused(false);
                return true;
            }
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.searchBox != null && keyCode == 70 && BackpackScreen.hasControlDown()) {
            this.searchBox.setFocused(true);
            this.searchBox.setCursorPosition(0);
            this.searchBox.setHighlightPos(this.searchBox.getValue().length());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (this.scrollPanel != null && this.scrollPanel.mouseScrolled(mouseX, mouseY, scrollDelta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrollPanel != null && this.scrollPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.scrollPanel != null && this.scrollPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void onClose() {
        ModNetwork.sendToServer(new BackpackClosePayload(((BackpackContainer)this.menu).containerId));
        super.onClose();
    }

    public static enum SortBy {
        NAME,
        COUNT,
        MOD;


        public SortBy next() {
            return switch (this) {
                default -> throw new IncompatibleClassChangeError();
                case NAME -> COUNT;
                case COUNT -> MOD;
                case MOD -> NAME;
            };
        }
    }

    private class BackpackScrollPanel
    extends ScrollPanel {
        BackpackScrollPanel(Minecraft client, int width, int height, int top, int left) {
            super(client, width, height, top, left, 0);
        }

        public NarratableEntry.NarrationPriority narrationPriority() {
            return NarratableEntry.NarrationPriority.NONE;
        }

        public void updateNarration(NarrationElementOutput output) {
        }

        protected int getScrollAmount() {
            return 18;
        }

        protected int getContentHeight() {
            int totalRows = (((BackpackContainer)BackpackScreen.this.menu).getContainerSize() + 12 - 1) / 12;
            return totalRows * 18;
        }

        protected void drawBackground(GuiGraphics guiGraphics, Tesselator tess, float partialTick) {
        }

        protected void drawPanel(GuiGraphics guiGraphics, int entryRight, int relativeY, Tesselator tess, int mouseX, int mouseY) {
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
            boolean handled = super.mouseScrolled(mouseX, mouseY, scrollDelta);
            if (handled) {
                BackpackScreen.this.setScrollRowOffset(Math.round(this.scrollDistance / SLOT_SIZE));
            }
            return handled;
        }

        void reclamp() {
            int maxScroll = this.getContentHeight() - (this.height - this.border);
            float oldDist = this.scrollDistance;
            this.scrollDistance = Math.max(0.0f, Math.min(this.scrollDistance, (float)maxScroll));
            if ((int)oldDist != (int)this.scrollDistance) {
                TimeReward.LOGGER.info("[BackpackScreen-Diag] scroll clamped from {} to {} (new max={})", new Object[]{Float.valueOf(oldDist), Float.valueOf(this.scrollDistance), maxScroll});
            }
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            boolean handled = super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            if (handled) {
                BackpackScreen.this.setScrollRowOffset(Math.round(this.scrollDistance / SLOT_SIZE));
            }
            return handled;
        }

        void resetScrollDistance() {
            this.scrollDistance = 0.0f;
        }

        float getScrollDistance() {
            return this.scrollDistance;
        }

        void setScrollDistance(float dist) {
            this.scrollDistance = dist;
        }

        int getScrollRowOffset() {
            return ((BackpackContainer)BackpackScreen.this.menu).getScrollOffset();
        }

    }
}
