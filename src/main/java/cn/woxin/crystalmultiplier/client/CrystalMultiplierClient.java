package cn.woxin.crystalmultiplier.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import cn.woxin.crystalmultiplier.CrystalMultiplierMod;

@Mod.EventBusSubscriber(modid = CrystalMultiplierMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrystalMultiplierClient {

    private CrystalMultiplierClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(CrystalMultiplierMod.MULTIPLIER_MENU.get(),
                CrystalMultiplierScreen::new));
    }
}
