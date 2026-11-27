package cn.furina.crystalmultiplier.compat.ae2;

import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Entry point of the optional AE2 integration (产物回网 / direct output into the ME network).
 *
 * <p>This class must never name an {@code appeng.*} type: it is loaded for every machine tick,
 * while {@link Ae2Output} - which does name them - is only resolved once AE2 is actually there.
 * The mod already declares AE2 as a required dependency, but keeping the gate means a dev
 * environment or a broken pack cannot crash on a missing class just because a machine ticked.</p>
 */
public final class Ae2Compat {

    private static final String AE2_MODID = "ae2";

    private static Boolean loaded;

    private Ae2Compat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(AE2_MODID);
        }
        return loaded;
    }

    /**
     * Hands a finished batch to an adjacent ME network, so the crystals never enter the output slots.
     *
     * @param simulate {@code true} to only ask the network (used by the "can I start a batch" check)
     * @return how many items the network accepted
     */
    public static int insert(CrystalMultiplierBlockEntity machine, ItemStack stack, int amount, boolean simulate) {
        if (!isLoaded() || !machine.isAeOutputEnabled()) {
            return 0;
        }
        return Ae2Output.insert(machine, stack, amount, simulate);
    }

    /**
     * Moves anything still sitting in the output slots into an adjacent ME network. Only used when
     * the machine's own AE output switch is on; leftovers can appear when the network was full.
     *
     * @return how many items left the machine, 0 when AE2 or the feature is disabled
     */
    public static int drainOutput(CrystalMultiplierBlockEntity machine) {
        if (!isLoaded() || !machine.isAeOutputEnabled()) {
            return 0;
        }
        return Ae2Output.drain(machine);
    }
}
