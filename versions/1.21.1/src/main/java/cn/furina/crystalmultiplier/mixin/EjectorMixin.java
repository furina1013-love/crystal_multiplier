package cn.furina.crystalmultiplier.mixin;

import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
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
 * <p>Mekanism 10.7's {@link TileComponentEjector} still keeps the item eject interval in a private
 * {@code tickDelay} counter: {@code tickServer()} drops it by one each tick and only runs
 * {@code outputItems()} when it hits zero, after which the counter is re-armed with 10. There is no
 * public hook to change that interval (the rate suppliers in the constructors only size the amount
 * per ejection for chemicals/fluids/energy), so a mixin is required for the "every tick" feature.
 * Zeroing the counter at the head of the tick makes the machine eject on every single tick.</p>
 *
 * <p>Which machines are affected depends on the owner tile: our own Crystal Multiplier follows
 * {@code machine.ejectEveryTick} (on by default), every other Mekanism machine follows
 * {@code machine.globalEjectEveryTick} (off by default). Non item transmission types never used the
 * counter in the first place, so they are untouched either way. Crafting speed is not changed.</p>
 *
 * <p>1.21.1 note: this needs a mixin config; the build has to register it (the mods.toml
 * {@code [[mixins]]} block plus the moddev/mixin gradle wiring) - see the accompanying
 * {@code crystal_multiplier.mixins.json}.</p>
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
