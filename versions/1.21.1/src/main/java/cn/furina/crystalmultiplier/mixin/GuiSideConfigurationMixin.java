package cn.furina.crystalmultiplier.mixin;

import cn.furina.crystalmultiplier.client.gui.AeOutputButton;
import cn.furina.crystalmultiplier.client.gui.CrystalChildAdder;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.GuiSideConfiguration;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.base.TileEntityMekanism;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "AE" output switch to the side configuration window of the crystal multiplier, next to
 * Mekanism's own auto-eject button. Other machines are left untouched (the tile check below).
 */
@Mixin(value = GuiSideConfiguration.class, remap = false)
public abstract class GuiSideConfigurationMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("crystal_multiplier/side_config");

    @Shadow(remap = false)
    @Final
    private TileEntityMekanism tile;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void crystal$addAeOutputButton(IGuiWrapper gui, int x, int y, TileEntityMekanism tile, SelectedWindowData windowData, CallbackInfo ci) {
        if (!(tile instanceof CrystalMultiplierBlockEntity machine)) {
            return;
        }
        try {
            // Mekanism's auto-eject button sits at (136, 6); put ours right below it.
            ((CrystalChildAdder) (Object) this).crystal$addChild(new AeOutputButton(gui, 136, 24, machine));
        } catch (Throwable t) {
            LOGGER.warn("crystal_multiplier: could not add the AE output button to the side configuration window", t);
        }
    }
}
