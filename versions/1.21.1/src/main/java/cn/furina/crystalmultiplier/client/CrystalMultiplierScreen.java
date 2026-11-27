package cn.furina.crystalmultiplier.client;

import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.content.CrystalSlot;
import cn.furina.crystalmultiplier.menu.CrystalMultiplierMenu;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Upgrade;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.UpgradeInventorySlot;
import mekanism.common.item.ItemUpgrade;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism's own machine GUI: the background, upgrade window, side configuration, energy tab and
 * redstone tab all come from {@link GuiConfigurableTile}. Only the progress arrow, the power bar
 * and the real stored counts are added here.
 */
public class CrystalMultiplierScreen extends GuiConfigurableTile<CrystalMultiplierBlockEntity, CrystalMultiplierMenu> {

    public CrystalMultiplierScreen(CrystalMultiplierMenu container, Inventory inventory, Component title) {
        super(container, inventory, title);
        // Slots are placed by the tile (the output row depends on the tier), so keep Mekanism's
        // dynamic slot placement instead of the fixed slot table.
        this.dynamicSlots = true;
        // Same geometry as the 1.20.1 build: Mekanism's default GUI texture is 176x166 and the
        // title sits at y=4. Without these the background and the tab strip are drawn off.
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        this.addRenderableWidget(new GuiVerticalPowerBar(this, this.tile.getEnergyContainer(), 164, 15));
        this.addRenderableWidget(new GuiEnergyTab(this, this.tile.getEnergyContainer(), this.tile::getActive));
        this.addRenderableWidget(new GuiProgress(this.tile::getScaledProgress, ProgressType.RIGHT, this, 52, 45));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title and "Inventory" label exactly like Mekanism's own machine GUIs do it.
        this.renderTitleText(guiGraphics);
        this.renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        // The visible stack only carries up to one stack worth of items, so the real amount is
        // printed into the corner of every occupied slot.
        List<CrystalSlot> machineSlots = this.menu.getMachineSlots();
        int start = this.menu.getMachineSlotStart();
        if (start < 0) {
            return;
        }
        for (int i = 0; i < machineSlots.size(); i++) {
            CrystalSlot machineSlot = machineSlots.get(i);
            int stored = machineSlot.getStoredCount();
            int visible = machineSlot.getStack().getCount();
            if (stored > visible && visible > 0) {
                String text = Integer.toString(stored);
                Slot slot = this.menu.slots.get(start + i);
                // TODO(1.21.1): GuiGraphics#drawString(Font, String, int, int, int, boolean) could not be
                //  javap-checked offline (no 1.21.1 Minecraft artifact on this machine; Mek/AE2 only use the
                //  FormattedCharSequence overload). Long standing vanilla signature - confirm on first compile.
                //  Slot#x / Slot#y are verified (AE2/Mek read them in 1.21.1).
                guiGraphics.drawString(this.font, text, slot.x + 17 - this.font.width(text), slot.y + 9, 0xFFFFFF, true);
            }
        }
    }

    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        // Copied before appending: the vanilla list is not guaranteed to be mutable.
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        Slot hovered = this.hoveredSlot;
        if (hovered == null) {
            return tooltip;
        }
        int machineIndex = this.menu.slots.indexOf(hovered) - this.menu.getMachineSlotStart();
        boolean machineSlot = machineIndex >= 0 && machineIndex < this.menu.getMachineSlots().size();
        if (machineSlot) {
            int stored = this.menu.getStoredCount(machineIndex);
            int capacity = this.menu.getSlotCapacity(machineIndex);
            String key = machineIndex == 0 ? "gui.crystal_multiplier.seed_stored" : "gui.crystal_multiplier.stored";
            tooltip.add(Component.translatable(key, stored, capacity).withStyle(ChatFormatting.GRAY));
            if (machineIndex == 0) {
                // Seed slot: nothing is consumed, the stack size decides how many lanes run, and the
                // products leave the machine on their own.
                tooltip.addAll(tips("tip_seed", "tip_parallel", "tip_eject"));
            } else {
                // Output slots: every tick the products are pushed towards the configured sides.
                tooltip.addAll(tips("tip_eject"));
            }
        }
        // Upgrade slots (Mekanism's own card window) and any upgrade card the player hovers: these
        // two card types have completely different meanings in this machine, so spell it out.
        if (isUpgradeSlot(hovered)) {
            if (isCard(stack, Upgrade.SPEED)) {
                tooltip.addAll(tips("tip_speed_card"));
            } else if (isCard(stack, Upgrade.ENERGY)) {
                tooltip.addAll(tips("tip_energy_card"));
            } else {
                tooltip.addAll(tips("tip_speed_card", "tip_energy_card"));
            }
        }
        return tooltip;
    }

    /** Builds the grey "how this machine works" lines from the {@code gui.crystal_multiplier.<suffix>} keys. */
    private static List<Component> tips(String... suffixes) {
        List<Component> lines = new ArrayList<>(suffixes.length);
        for (String suffix : suffixes) {
            lines.add(Component.translatable("gui.crystal_multiplier." + suffix).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** True for Mekanism's card slots (the visible upgrade window slots are virtual container slots). */
    private static boolean isUpgradeSlot(@NotNull Slot slot) {
        return slot instanceof InventoryContainerSlot containerSlot
                && containerSlot.getInventorySlot() instanceof UpgradeInventorySlot;
    }

    /** True when the hovered stack is the given kind of Mekanism upgrade card. */
    private static boolean isCard(@NotNull ItemStack stack, @NotNull Upgrade type) {
        return stack.getItem() instanceof ItemUpgrade upgrade && upgrade.getUpgradeType(stack) == type;
    }
}
