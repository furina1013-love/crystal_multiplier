package cn.furina.crystalmultiplier.compat.jade;

import cn.furina.crystalmultiplier.content.CrystalMultiplierBlock;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade entry point.
 *
 * <p>Jade is an optional dependency: this class is never referenced from the mod itself. Jade
 * discovers it through the {@link WailaPlugin} annotation while scanning the mod file (Forge
 * {@code ModFileScanData} annotations, see Jade's {@code CommonProxy#loadComplete}), checks that
 * the mod id given in the annotation is loaded, and only then instantiates the class. When Jade
 * is absent this class is never loaded, so its Jade types are never resolved.</p>
 */
@WailaPlugin("crystal_multiplier")
public class CrystalMultiplierJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // Lets the server side send the machine state to the client, so the tooltip also works
        // in multiplayer where the client block entity does not know these values.
        registration.registerBlockDataProvider(CrystalMultiplierJadeProvider.INSTANCE,
                CrystalMultiplierBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CrystalMultiplierJadeProvider.INSTANCE,
                CrystalMultiplierBlock.class);
    }
}
