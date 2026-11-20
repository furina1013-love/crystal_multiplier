package cn.furina.crystalmultiplier.client;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import mekanism.common.registries.MekanismItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * Explains the two Mekanism upgrade cards while this machine's GUI is open.
 *
 * <p>The upgrade cards do not live in the machine container - Mekanism renders them from its own
 * upgrade window, which builds its tooltips itself, so {@code getTooltipFromContainerItem} never
 * sees them. Hooking {@link ItemTooltipEvent} instead covers every place the card can be hovered
 * (player inventory, the Mekanism upgrade window, JEI) and stays local because the screen check
 * limits it to our GUI: everywhere else the cards keep their vanilla Mekanism description.</p>
 */
@Mod.EventBusSubscriber(modid = CrystalMultiplierMod.MODID, value = Dist.CLIENT)
public final class CrystalMultiplierUpgradeTips {

    private CrystalMultiplierUpgradeTips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(@NotNull ItemTooltipEvent event) {
        if (!(Minecraft.getInstance().screen instanceof CrystalMultiplierScreen)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        // 用物品本体比较：速度卡/能量卡就是 Mekanism 自己的那两种升级卡。
        if (item == MekanismItems.SPEED_UPGRADE.getItemStack().getItem()) {
            event.getToolTip().add(Component.translatable("gui.crystal_multiplier.tip_speed_card")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (item == MekanismItems.ENERGY_UPGRADE.getItemStack().getItem()) {
            event.getToolTip().add(Component.translatable("gui.crystal_multiplier.tip_energy_card")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
