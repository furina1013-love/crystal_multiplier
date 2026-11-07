package cn.woxin.crystalmultiplier.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cn.woxin.crystalmultiplier.content.CrystalTier;
import cn.woxin.crystalmultiplier.menu.CrystalMultiplierMenu;

@OnlyIn(Dist.CLIENT)
public class CrystalMultiplierScreen extends AbstractContainerScreen<CrystalMultiplierMenu> {

    private static final int PANEL = 0xFF2A2A2A;
    private static final int SLOT_DARK = 0xFF171717;
    private static final int SLOT_LIGHT = 0xFF565656;
    private static final int BORDER_DARK = 0xFF0B0B0B;
    private static final int ENERGY_EMPTY = 0xFF2B1A1A;
    private static final int ENERGY_FULL = 0xFFD94F4F;
    private static final int PROGRESS_EMPTY = 0xFF1E1E1E;
    private static final int PROGRESS_FULL = 0xFF5FD9A0;
    private static final int TEXT = 0xFFD7D7D7;

    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_W = 12;
    private static final int ENERGY_H = 54;
    private static final int BAR_X = 64;
    private static final int BAR_Y = 33;
    private static final int BAR_W = 26;
    private static final int BAR_H = 16;
    private static final int STATUS_X = 26;
    private static final int STATUS_Y = 57;
    private static final int STATUS_ERROR = 0xFFFF5555;

    private final CrystalTier tier;

    public CrystalMultiplierScreen(CrystalMultiplierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CrystalMultiplierMenu.GUI_WIDTH;
        this.imageHeight = CrystalMultiplierMenu.GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
        this.tier = menu.getTier();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.drawOverlay(graphics, mouseX, mouseY);
    }

    private void drawOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        Component tierName = this.tier.getDisplayName();
        graphics.drawString(this.font, tierName, x + this.imageWidth - 8 - this.font.width(tierName), y + 5,
                this.tier.getTintColor(), false);

        int per = this.menu.getCrystalsPerParallel();
        if (this.menu.isSeedValid()) {
            graphics.drawString(this.font, Component.translatable("gui.crystal_multiplier.parallel",
                    this.menu.getParallelism()), x + STATUS_X, y + STATUS_Y, TEXT, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.crystal_multiplier.seed_invalid", per),
                    x + STATUS_X, y + STATUS_Y, STATUS_ERROR, false);
        }

        Slot seedSlot = this.menu.slots.get(0);
        if (this.isHovering(seedSlot.x, seedSlot.y, 16, 16, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.crystal_multiplier.per_parallel", per));
            lines.add(Component.translatable("gui.crystal_multiplier.parallel", this.menu.getParallelism()));
            lines.add(Component.translatable("gui.crystal_multiplier.seed_tip"));
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
        }

        if (this.isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.crystal_multiplier.energy",
                    this.menu.getEnergy(), this.menu.getEnergyCapacity()));
            lines.add(Component.translatable("gui.crystal_multiplier.cost", this.menu.getEnergyPerTick()));
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
        }

        if (this.isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.crystal_multiplier.progress", this.menu.getProgressPercent()));
            lines.add(Component.translatable("gui.crystal_multiplier.time",
                    String.format(java.util.Locale.ROOT, "%.1f", this.menu.getTicksRequired() / 20.0D)));
            lines.add(Component.translatable("gui.crystal_multiplier.parallel", this.menu.getParallelism()));
            lines.add(Component.translatable("gui.crystal_multiplier.batch", this.menu.getItemsPerBatch()));
            if (this.menu.isActive()) {
                lines.add(Component.translatable("gui.crystal_multiplier.active"));
            } else {
                lines.add(Component.translatable("gui.crystal_multiplier.idle"));
            }
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER_DARK);
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, this.tier.getTintColor());
        graphics.fill(x + 1, y + 1, x + 3, y + h - 1, this.tier.getTintColor());

        for (Slot slot : this.menu.slots) {
            int slotX = x + slot.x - 1;
            int slotY = y + slot.y - 1;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, SLOT_LIGHT);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, SLOT_DARK);
        }

        int energyX = x + ENERGY_X;
        int energyY = y + ENERGY_Y;
        graphics.fill(energyX - 1, energyY - 1, energyX + ENERGY_W + 1, energyY + ENERGY_H + 1, BORDER_DARK);
        graphics.fill(energyX, energyY, energyX + ENERGY_W, energyY + ENERGY_H, ENERGY_EMPTY);
        int capacity = this.menu.getEnergyCapacity();
        float ratio = capacity <= 0 ? 0.0F : Math.min(1.0F, (float) this.menu.getEnergy() / (float) capacity);
        int filled = (int) Math.floor(ratio * ENERGY_H);
        if (filled > 0) {
            graphics.fill(energyX, energyY + ENERGY_H - filled, energyX + ENERGY_W, energyY + ENERGY_H, ENERGY_FULL);
        }

        int barX = x + BAR_X;
        int barY = y + BAR_Y;
        graphics.fill(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, BORDER_DARK);
        graphics.fill(barX, barY, barX + BAR_W, barY + BAR_H, PROGRESS_EMPTY);
        int progress = (int) Math.round(BAR_W * this.menu.getProgressPercent() / 100.0D);
        if (progress > 0) {
            graphics.fill(barX, barY, barX + Math.min(BAR_W, progress), barY + BAR_H, PROGRESS_FULL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
    }
}
