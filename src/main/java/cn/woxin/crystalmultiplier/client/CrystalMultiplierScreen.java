package cn.woxin.crystalmultiplier.client;

import cn.woxin.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.woxin.crystalmultiplier.content.CrystalSlot;
import cn.woxin.crystalmultiplier.menu.CrystalMultiplierMenu;
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
            }
        }
        return tooltip;
    }
}
