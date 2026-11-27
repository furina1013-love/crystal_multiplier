package cn.furina.crystalmultiplier.client.gui;

import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.menu.CrystalMultiplierMenu;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The small "AE" switch in the machine's side configuration window: with it on, a finished batch is
 * inserted into an adjacent ME network and never enters the output slots.
 *
 * <p>It talks to the server through {@link net.minecraft.world.inventory.AbstractContainerMenu#clickMenuButton},
 * which needs no packet class of its own; the state itself reaches the client through Mekanism's
 * container tracker for the block entity.</p>
 */
public class AeOutputButton extends MekanismButton {

    private final CrystalMultiplierBlockEntity tile;

    public AeOutputButton(IGuiWrapper gui, int x, int y, CrystalMultiplierBlockEntity tile) {
        super(gui, x, y, 14, 14, Component.literal("AE"), () -> sendToggle(), (element, graphics, mouseX, mouseY) ->
                element.displayTooltips(graphics, mouseX, mouseY,
                        Component.translatable("crystal_multiplier.gui.ae_output",
                                Component.translatable(tile.isAeOutputEnabled() ? "options.on" : "options.off")
                                        .withStyle(tile.isAeOutputEnabled() ? ChatFormatting.GREEN : ChatFormatting.GRAY))));
        this.tile = tile;
        this.updateLabel();
    }

    private static void sendToggle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        if (minecraft.player.containerMenu instanceof CrystalMultiplierMenu menu) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CrystalMultiplierMenu.BUTTON_TOGGLE_AE_OUTPUT);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.updateLabel();
    }

    private void updateLabel() {
        this.setMessage(Component.literal(this.tile != null && this.tile.isAeOutputEnabled() ? "AE+" : "AE"));
    }

    /** Kept so a future render pass can draw the state without re-creating the widget. */
    public void onHoverRender(GuiGraphics graphics, int mouseX, int mouseY) {
        // Unused: the hover tooltip is built in the constructor lambda.
    }
}
