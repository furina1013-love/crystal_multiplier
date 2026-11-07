package cn.woxin.crystalmultiplier.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The four factory tiers of the crystal multiplier, mirroring Mekanism's machine tiers.
 * Higher tiers process faster, hold more energy, expose more output slots and accept
 * more Mekanism upgrade cards.
 */
public enum CrystalTier implements StringRepresentable {
    BASIC("basic", 1.0D, 200_000, 3, 4, 40, 0xFF3F76E4, ChatFormatting.GRAY),
    ADVANCED("advanced", 2.0D, 1_000_000, 6, 6, 70, 0xFF3FBF6A, ChatFormatting.GREEN),
    ELITE("elite", 4.0D, 4_000_000, 9, 8, 100, 0xFFB03FE4, ChatFormatting.LIGHT_PURPLE),
    ULTIMATE("ultimate", 8.0D, 20_000_000, 12, 10, 140, 0xFFE43F3F, ChatFormatting.RED);

    private final String tierName;
    private final double speedMultiplier;
    private final int energyCapacity;
    private final int outputSlots;
    private final int upgradeSlots;
    private final int baseTransfer;
    private final int tint;
    private final ChatFormatting color;

    CrystalTier(String tierName, double speedMultiplier, int energyCapacity, int outputSlots,
                int upgradeSlots, int baseTransfer, int tint, ChatFormatting color) {
        this.tierName = tierName;
        this.speedMultiplier = speedMultiplier;
        this.energyCapacity = energyCapacity;
        this.outputSlots = outputSlots;
        this.upgradeSlots = upgradeSlots;
        this.baseTransfer = baseTransfer;
        this.tint = tint;
        this.color = color;
    }

    public String getRegistryName() {
        return "crystal_multiplier" + (this == BASIC ? "" : "_" + this.tierName);
    }

    public String getTranslationKey() {
        return "tier.crystal_multiplier." + this.tierName;
    }

    @Override
    public String getSerializedName() {
        return this.tierName;
    }

    public double getSpeedMultiplier() {
        return this.speedMultiplier;
    }

    public int getEnergyCapacity() {
        return this.energyCapacity;
    }

    public int getOutputSlots() {
        return this.outputSlots;
    }

    public int getUpgradeSlots() {
        return this.upgradeSlots;
    }

    /** Maximum FE/t this tier can pull from an external energy source. */
    public int getBaseTransfer() {
        return this.baseTransfer;
    }

    public int getTintColor() {
        return this.tint;
    }

    public Component getDisplayName() {
        return Component.translatable(this.getTranslationKey());
    }

    public int getTint() {
        return this.tint;
    }

    public ChatFormatting getColor() {
        return this.color;
    }
}
