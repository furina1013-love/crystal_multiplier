package cn.woxin.crystalmultiplier.mixin;

import java.util.function.IntSupplier;
import mekanism.api.recipes.cache.CachedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes every Mekanism machine that runs on {@link CachedRecipe} finish one operation per tick.
 *
 * <p>Mekanism drives almost all of its production machines (the enricher/crusher/smelting style
 * single machines, the whole factory family and any addon that reuses the same cache) through
 * {@code CachedRecipe.process()}. At the end of the tick it bumps the private {@code operatingTicks}
 * counter and, once that counter reaches {@code requiredTicks}, consumes the inputs and pushes the
 * products out. Lifting the counter to {@code requiredTicks - 1} before the tick runs makes the
 * increment below always cross the threshold, so the machine finishes a batch every tick.</p>
 *
 * <p>A normally finishing tick calls {@code finishProcessing} and skips {@code useResources}, which
 * is where the chemical based recipes drain their gas. Simply forcing the counter would therefore
 * produce gas machines for free, so the forced path also runs {@code useResources} with the operation
 * count of the tick. That keeps every recipe paying exactly what it paid before: the per operation
 * gas figure of {@code TileEntityItemStackGasToItemStackFactory} for example divides the remaining
 * gas over the remaining ticks, and with one remaining tick it hands over the whole operation's
 * gas at once.</p>
 *
 * <p>Recipes that already take a single tick are untouched, and machines that never call
 * {@code CachedRecipe.process} are not covered by this hook.</p>
 */
@Mixin(CachedRecipe.class)
public abstract class CachedRecipeMixin {

    @Shadow
    private int operatingTicks;

    @Shadow
    private IntSupplier requiredTicks;

    /** Operations this tick computed by the cache; captured from the energy call. */
    @Unique
    private int crystal$operations;

    /** True when this tick is a forced one tick finish. */
    @Unique
    private boolean crystal$forcedFinish;

    @Shadow
    protected abstract void useResources(int operations);

    @Shadow
    protected abstract void useEnergy(int operations);

    @Inject(method = "process", at = @At("HEAD"), remap = false)
    private void crystal$oneOperationPerTick(CallbackInfo ci) {
        this.crystal$forcedFinish = false;
        int required = this.requiredTicks.getAsInt();
        if (required > 1) {
            this.operatingTicks = required - 1;
            this.crystal$forcedFinish = true;
        }
    }

    @Redirect(method = "process", at = @At(value = "INVOKE",
            target = "Lmekanism/api/recipes/cache/CachedRecipe;useEnergy(I)V"), remap = false)
    private void crystal$captureOperations(CachedRecipe<?> self, int operations) {
        this.crystal$operations = operations;
        this.useEnergy(operations);
    }

    @Inject(method = "process", at = @At(value = "INVOKE",
            target = "Lmekanism/api/recipes/cache/CachedRecipe;finishProcessing(I)V",
            shift = At.Shift.BEFORE), remap = false)
    private void crystal$consumeResources(CallbackInfo ci) {
        if (this.crystal$forcedFinish) {
            this.crystal$forcedFinish = false;
            this.useResources(this.crystal$operations);
        }
    }
}

