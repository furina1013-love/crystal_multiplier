package cn.woxin.crystalmultiplier.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import cn.woxin.crystalmultiplier.content.CrystalTier;
import cn.woxin.crystalmultiplier.menu.CrystalMultiplierMenu;

/**
 * Mekanism style machine screen: the background, the recessed slots, the gauged energy bar and the
 * filling arrow all come from our own textures which follow Mekanism's palette (light steel frame,
 * dark grey body, inset slots).
 */
@OnlyIn(Dist.CLIENT)
public class CrystalMultiplierScreen extends AbstractContainerScreen<CrystalMultiplierMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("crystal_multiplier",
            "textures/gui/machine.png");
    private static final ResourceLocation SLOT = new ResourceLocation("crystal_multiplier",
            "textures/gui/slot.png");
    private static final ResourceLocation GAUGE = new ResourceLocation("crystal_multiplier",
            "textures/gui/gauge_ticks.png");
    private static final ResourceLocation ENERGY_FILL = new ResourceLocation("crystal_multiplier",
            "textures/gui/energy_fill.png");
    private static final ResourceLocation ARROW_FILL = new ResourceLocation("crystal_multiplier",
            "textures/gui/arrow_fill.png");

    private static final int TEXT = 0xFFD7D7D7;
    private static final int TEXT_DARK = 0xFF3A3A3A;
    private static final int STATUS_ERROR = 0xFFFF5555;

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
            lines.add(Component.translatable("gui.crystal_multiplier.seed_size", this.menu.getSeedCapacity()));
            lines.add(Component.translatable("gui.crystal_multiplier.seed_stored", this.menu.getStoredCount(0),
                    this.menu.getSeedCapacity()));
            lines.add(Component.translatable("gui.crystal_multiplier.seed_tip"));
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
        }

        // A slot can hold far more than the 127 items a single ItemStack can carry, and the item badge of
        // the slot only shows that visible part, so the real amount is printed on top of the slot.
        int machineSlots = this.menu.getMachineSlotCount();
        for (int index = 0; index < machineSlots; index++) {
            Slot slot = this.menu.slots.get(index);
            int stored = this.menu.getStoredCount(index);
            int visible = slot.getItem().getCount();
            if (stored > visible && stored > 0) {
                String amount = Integer.toString(stored);
                graphics.drawString(this.font, amount, x + slot.x + 17 - this.font.width(amount),
                        y + slot.y + 9, 0xFFFFF060, true);
            }
            // Slot 0 is the seed slot and has its own, richer tooltip above.
            if (index > 0 && stored > 0 && this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.crystal_multiplier.stored", stored,
                        this.menu.getSlotCapacity(index)));
                graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                return;
            }
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
                    String.format(Locale.ROOT, "%.1f", this.menu.getTicksRequired() / 20.0D)));
            lines.add(Component.translatable("gui.crystal_multiplier.parallel", this.menu.getParallelism()));
            lines.add(Component.translatable("gui.crystal_multiplier.batch", this.menu.getItemsPerBatch()));
            lines.add(Component.translatable("gui.crystal_multiplier.speed_upgrades", this.menu.getSpeedUpgrades()));
            lines.add(Component.translatable("gui.crystal_multiplier.energy_upgrades",
                    this.menu.getEnergyUpgrades()));
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

        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        for (Slot slot : this.menu.slots) {
            graphics.blit(SLOT, x + slot.x - 1, y + slot.y - 1, 0, 0, 18, 18, 18, 18);
        }

        int energyX = x + ENERGY_X;
        int energyY = y + ENERGY_Y;
        int capacity = this.menu.getEnergyCapacity();
        float ratio = capacity <= 0 ? 0.0F : Math.min(1.0F, (float) this.menu.getEnergy() / (float) capacity);
        int filled = (int) Math.floor(ratio * ENERGY_H);
        if (filled > 0) {
            graphics.blit(ENERGY_FILL, energyX, energyY + ENERGY_H - filled, 0, ENERGY_H - filled, ENERGY_W,
                    filled, ENERGY_W, ENERGY_H);
        }
        graphics.blit(GAUGE, energyX, energyY, 0, 0, ENERGY_W, ENERGY_H, ENERGY_W, ENERGY_H);

        int progress = (int) Math.round(BAR_W * this.menu.getProgressPercent() / 100.0D);
        if (progress > 0) {
            graphics.blit(ARROW_FILL, x + BAR_X, y + BAR_Y, 0, 0, Math.min(BAR_W, progress), BAR_H, BAR_W, BAR_H);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                TEXT_DARK, false);
    }
}
