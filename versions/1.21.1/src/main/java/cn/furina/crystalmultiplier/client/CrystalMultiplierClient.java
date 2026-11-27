package cn.furina.crystalmultiplier.client;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import mekanism.client.ClientRegistrationUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client side registration. 1.21.1 registers screens through the mod bus event
 * {@link RegisterMenuScreensEvent} instead of the old {@code FMLClientSetupEvent} +
 * {@code ClientRegistrationUtil.registerScreen(containerType, factory)} pair.
 */
@EventBusSubscriber(modid = CrystalMultiplierMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrystalMultiplierClient {

    private CrystalMultiplierClient() {
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        ClientRegistrationUtil.registerScreen(event, CrystalMultiplierMod.MULTIPLIER_CONTAINER, CrystalMultiplierScreen::new);
    }
}
