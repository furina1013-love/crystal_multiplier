package cn.furina.crystalmultiplier.compat.ae2;

import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
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
     * Moves everything sitting in the output slots into an adjacent ME network.
     *
     * @return how many items left the machine, 0 when AE2 or the feature is disabled
     */
    public static int drainOutput(CrystalMultiplierBlockEntity machine) {
        if (!isLoaded() || !CrystalMultiplierConfig.aeOutputEnabled()) {
            return 0;
        }
        return Ae2Output.drain(machine);
    }
}
