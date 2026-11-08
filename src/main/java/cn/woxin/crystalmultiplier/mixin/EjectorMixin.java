package cn.woxin.crystalmultiplier.mixin;

import cn.woxin.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.woxin.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentEjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the ejector of a machine push its finished products out of the output slots every tick.
 *
 * <p>Mekanism's {@link TileComponentEjector} does not eject items every tick: {@code tickServer()}
 * keeps a private {@code tickDelay} counter, drops it by one each tick and only runs
 * {@code outputItems()} when it reaches zero, after which {@code outputItems()} arms it again with
 * {@code tickDelay = 10}. That is the 10 tick (0.5 s) item eject interval every Mekanism machine has.
 * Zeroing the counter at the start of the tick makes the machine eject on every single tick.</p>
 *
 * <p>Which machines are affected depends on the owner tile: our own Crystal Multiplier follows
 * {@code machine.ejectEveryTick} (on by default), every other Mekanism machine follows
 * {@code machine.globalEjectEveryTick} (off by default). Non item transmission types (gas, fluid,
 * energy) never used the counter in the first place, so they are untouched either way. Crafting
 * speed is not changed by this mixin.</p>
 */
@Mixin(TileComponentEjector.class)
public abstract class EjectorMixin {

    @Shadow
    private TileEntityMekanism tile;

    @Shadow
    private int tickDelay;

    @Inject(method = "tickServer", at = @At("HEAD"), remap = false)
    private void crystal$ejectEveryTick(CallbackInfo ci) {
        boolean perTick = this.tile instanceof CrystalMultiplierBlockEntity
                ? CrystalMultiplierConfig.ejectEveryTick()
                : CrystalMultiplierConfig.globalEjectEveryTick();
        if (perTick) {
            this.tickDelay = 0;
        }
    }
}
