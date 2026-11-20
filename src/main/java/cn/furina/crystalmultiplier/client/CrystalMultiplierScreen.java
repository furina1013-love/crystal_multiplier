package cn.furina.crystalmultiplier.client;

import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.content.CrystalSlot;
import cn.furina.crystalmultiplier.menu.CrystalMultiplierMenu;
import java.util.List;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Mekanism's own machine GUI: the background, upgrade window, side configuration, energy tab and
 * redstone tab all come from {@link GuiConfigurableTile}. Only the progress arrow, the power bar
 * and the real stored counts are added here.
 */
public class CrystalMultiplierScreen extends GuiConfigurableTile<CrystalMultiplierBlockEntity, CrystalMultiplierMenu> {

    public CrystalMultiplierScreen(CrystalMultiplierMenu container, Inventory inventory, Component title) {
        super(container, inventory, title);
        this.dynamicSlots = true;
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelY = 4;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        super.m_7286_(guiGraphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        this.addRenderableWidget(new GuiVerticalPowerBar(this, this.tile.getEnergyContainer(), 164, 15));
        this.addRenderableWidget(new GuiEnergyTab(this, this.tile.getEnergyContainer(), this.tile::getActive));
        this.addRenderableWidget(new GuiProgress(this.tile::getScaledProgress, ProgressType.RIGHT, this, 52, 45));
    }

    @Override
    protected void drawForegroundText(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // renderTitleText already draws the centered title; drawing this.title again would duplicate it.
        this.renderTitleText(guiGraphics);
        this.drawString(guiGraphics, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, this.titleTextColor());
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        List<CrystalSlot> machineSlots = this.menu.getMachineSlots();
        int start = this.menu.getMachineSlotStart();
        for (int i = 0; i < machineSlots.size(); i++) {
            CrystalSlot machineSlot = machineSlots.get(i);
            int stored = machineSlot.getStoredCount();
            int visible = machineSlot.getStack().getCount();
            if (stored > visible && visible > 0) {
                String text = Integer.toString(stored);
                Slot slot = this.menu.slots.get(start + i);
                guiGraphics.drawString(this.font, text, slot.x + 17 - this.font.width(text), slot.y + 9, 0xFFFFFF, true);
            }
        }
    }

    @Override
    protected java.util.List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = super.getTooltipFromContainerItem(stack);
        Slot hovered = this.hoveredSlot;
        if (hovered != null) {
            int machineIndex = this.menu.slots.indexOf(hovered) - this.menu.getMachineSlotStart();
            if (machineIndex >= 0 && machineIndex < this.menu.getMachineSlots().size()) {
                int stored = this.menu.getStoredCount(machineIndex);
                int capacity = this.menu.getSlotCapacity(machineIndex);
                String key = machineIndex == 0 ? "gui.crystal_multiplier.seed_stored" : "gui.crystal_multiplier.stored";
                tooltip.add(Component.translatable(key, stored, capacity).withStyle(ChatFormatting.GRAY));
                // 机制说明（同一份文本也用在空槽提示里，见 renderEmptySlotHints）。
                tooltip.addAll(this.slotHintLines(machineIndex));
            }
        }
        return tooltip;
    }

    /* ------------------------------------------------------------------ hints */

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderEmptySlotHints(guiGraphics, mouseX, mouseY);
    }

    /**
     * Vanilla only builds a slot tooltip when the slot holds an item - and the seed slot is empty
     * exactly when a new player stares at it. Empty machine slots therefore get our explanation
     * drawn by hand; filled slots keep the normal tooltip path above, so nothing is drawn twice.
     */
    private void renderEmptySlotHints(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int machineIndex = this.hoveredMachineSlot(mouseX, mouseY);
        if (machineIndex < 0) {
            return;
        }
        int start = this.menu.getMachineSlotStart();
        if (this.menu.slots.get(start + machineIndex).hasItem()) {
            return;
        }
        guiGraphics.renderComponentTooltip(this.font, this.slotHintLines(machineIndex), mouseX, mouseY);
    }

    /** Index of the machine slot under the cursor, or -1 when the cursor is somewhere else. */
    private int hoveredMachineSlot(int mouseX, int mouseY) {
        int start = this.menu.getMachineSlotStart();
        int count = this.menu.getMachineSlots().size();
        for (int i = 0; i < count; i++) {
            Slot slot = this.menu.slots.get(start + i);
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The short "how does this machine work" lines for one machine slot. Slot 0 is the seed slot,
     * every other slot is an output slot. All text lives in the language files, so a pack author can
     * reword it (or add a lang file of their own) without touching code.
     */
    private List<Component> slotHintLines(int machineIndex) {
        if (machineIndex == 0) {
            // 种子槽：种子不消耗 + 并行数公式（16 是配置里的 crystalsPerParallel，可能被改过）。
            return List.of(
                    Component.translatable("gui.crystal_multiplier.tip_seed").withStyle(ChatFormatting.DARK_GRAY),
                    Component.translatable("gui.crystal_multiplier.tip_parallel",
                            CrystalMultiplierConfig.crystalsPerParallel()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of(Component.translatable("gui.crystal_multiplier.tip_eject").withStyle(ChatFormatting.DARK_GRAY));
    }
}
