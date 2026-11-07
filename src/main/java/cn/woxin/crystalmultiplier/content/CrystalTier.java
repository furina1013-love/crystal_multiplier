package cn.woxin.crystalmultiplier.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The four factory tiers of the crystal multiplier, mirroring Mekanism's machine tiers.
 * Higher tiers process faster, hold more energy, expose more output slots, accept more
 * upgrade cards and raise the stack size of the single seed slot as well as of every
 * output slot.
 * <p>
 * 1.20.1 stores item counts in a signed byte (both on disk and on the wire), so a single
 * ItemStack can never hold more than 127 items. The machine inventory hides everything above
 * that limit in an overflow counter, which is why the tiers below advertise 64/128/256/512.
 * Every value is a multiple of the default lane size (16).
 */
public enum CrystalTier implements StringRepresentable {
    BASIC("basic", 1.0D, 200_000, 3, 4, 20_000, 64, 64, 0xFF3F76E4, ChatFormatting.GRAY),
    ADVANCED("advanced", 2.0D, 800_000, 6, 6, 80_000, 128, 128, 0xFF3FBF6A, ChatFormatting.GREEN),
    ELITE("elite", 4.0D, 3_200_000, 9, 8, 320_000, 256, 256, 0xFFB03FE4, ChatFormatting.LIGHT_PURPLE),
    ULTIMATE("ultimate", 8.0D, 12_800_000, 12, 10, 1_280_000, 512, 512, 0xFFE43F3F, ChatFormatting.RED);

    private final String tierName;
    private final double speedMultiplier;
    private final int energyCapacity;
    private final int outputSlots;
    private final int upgradeSlots;
    private final int baseTransfer;
    private final int seedStackLimit;
    private final int outputStackLimit;
    private final int tint;
    private final ChatFormatting color;

    CrystalTier(String tierName, double speedMultiplier, int energyCapacity, int outputSlots,
                int upgradeSlots, int baseTransfer, int seedStackLimit, int outputStackLimit,
                int tint, ChatFormatting color) {
        this.tierName = tierName;
        this.speedMultiplier = speedMultiplier;
        this.energyCapacity = energyCapacity;
        this.outputSlots = outputSlots;
        this.upgradeSlots = upgradeSlots;
        this.baseTransfer = baseTransfer;
        this.seedStackLimit = seedStackLimit;
        this.outputStackLimit = outputStackLimit;
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

    /** How many crystals of one kind the single seed slot holds before upgrade cards enlarge it. */
    public int getSeedStackLimit() {
        return this.seedStackLimit;
    }

    /** How many items one output slot holds; higher tiers pack more into the same space. */
    public int getOutputStackLimit() {
        return this.outputStackLimit;
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
