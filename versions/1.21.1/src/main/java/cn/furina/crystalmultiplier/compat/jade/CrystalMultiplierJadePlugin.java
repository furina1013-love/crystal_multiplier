package cn.furina.crystalmultiplier.compat.jade;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade entry point (Jade 15.x for NeoForge 1.21.1).
 *
 * <p>Jade discovers plugins on NeoForge itself: it scans every mod file's {@code ModFileScanData} for
 * classes annotated with {@link WailaPlugin}, checks the annotation value against the loaded mod ids,
 * loads the class by name, instantiates it and calls {@link #register} / {@link #registerClient}. No
 * class of this mod references this one, so without Jade installed it is simply never loaded &mdash;
 * Jade therefore stays a {@code compileOnly} dependency and the mod keeps working when it is absent.</p>
 */
@WailaPlugin(CrystalMultiplierMod.MODID)
public class CrystalMultiplierJadePlugin implements IWailaPlugin {

    /** One instance serves both the server data session and the client component session. */
    private final CrystalMultiplierBlockComponentProvider provider = new CrystalMultiplierBlockComponentProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        // Server side part: pushes the live machine values into the data Jade sends to the client.
        registration.registerBlockDataProvider(this.provider, CrystalMultiplierBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(this.provider, CrystalMultiplierBlock.class);
    }
}
