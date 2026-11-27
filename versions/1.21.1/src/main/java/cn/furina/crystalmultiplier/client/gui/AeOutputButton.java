package cn.furina.crystalmultiplier.client.gui;

import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.menu.CrystalMultiplierMenu;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The small "AE" switch in the machine's side configuration window: with it on, a finished batch is
 * inserted into an adjacent ME network and never enters the output slots.
 *
 * <p>It talks to the server through {@link net.minecraft.world.inventory.AbstractContainerMenu#clickMenuButton},
 * which needs no packet class of its own; the state itself reaches the client through Mekanism's
 * container tracker for the block entity.</p>
 *
 * <p>1.21.1 note: Mekanism 10.7 replaced the old hover callback with {@code setTooltip(ILangEntry)},
 * so the state is shown by swapping the tooltip key in {@link #tick()}.</p>
 */
public class AeOutputButton extends MekanismButton {

    private final CrystalMultiplierBlockEntity tile;
    private boolean lastState;

    public AeOutputButton(IGuiWrapper gui, int x, int y, CrystalMultiplierBlockEntity tile) {
        super(gui, x, y, 14, 14, Component.literal("AE"), (element, mouseX, mouseY) -> {
            sendToggle();
            return true;
        });
        this.tile = tile;
        this.lastState = tile.isAeOutputEnabled();
        this.setTooltip(this.stateKey());
        this.setMessage(Component.literal(this.lastState ? "AE+" : "AE"));
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

    private mekanism.api.text.ILangEntry stateKey() {
        return this.lastState ? () -> "crystal_multiplier.gui.ae_output.on"
                : () -> "crystal_multiplier.gui.ae_output.off";
    }

    @Override
    public void tick() {
        super.tick();
        boolean state = this.tile != null && this.tile.isAeOutputEnabled();
        if (state != this.lastState) {
            this.lastState = state;
            this.setMessage(Component.literal(state ? "AE+" : "AE"));
            this.setTooltip(this.stateKey());
        }
    }
}
