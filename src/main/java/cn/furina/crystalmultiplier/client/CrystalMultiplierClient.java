package cn.furina.crystalmultiplier.client;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import mekanism.client.ClientRegistrationUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CrystalMultiplierMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrystalMultiplierClient {

    private CrystalMultiplierClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientRegistrationUtil.registerScreen(
                CrystalMultiplierMod.MULTIPLIER_CONTAINER, CrystalMultiplierScreen::new));
    }
}
