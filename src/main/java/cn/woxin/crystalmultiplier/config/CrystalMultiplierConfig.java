package cn.woxin.crystalmultiplier.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common (server side) config of the Crystal Multiplier.
 * Everything the user asked to be tunable lives here: how many crystals form one
 * parallel lane, how big the seed slot is, how fast the machines run and the
 * global "one operation per tick" switch.
 */
public final class CrystalMultiplierConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue CRYSTALS_PER_PARALLEL;
    public static final ForgeConfigSpec.IntValue SEED_CAPACITY_PER_UPGRADE;
    public static final ForgeConfigSpec.IntValue MAX_SEED_CAPACITY;
    public static final ForgeConfigSpec.IntValue MAX_PARALLEL;
    public static final ForgeConfigSpec.DoubleValue BASE_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BASE_ENERGY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SPEED_UPGRADE_SPEED_PER_CARD;
    public static final ForgeConfigSpec.DoubleValue ENERGY_UPGRADE_EFFICIENCY_FACTOR;
    public static final ForgeConfigSpec.BooleanValue ONE_TICK_OUTPUT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Crystal Multiplier: parallelism, seed slot, speed and output settings").push("machine");

        CRYSTALS_PER_PARALLEL = builder
                .comment("How many crystals of one kind must sit in the seed slot to form one parallel lane.",
                        "The seed slot content must always be a multiple of this value, otherwise the machine idles.",
                        "A single recipe may override it with its own \"crystals_per_parallel\" field.")
                .defineInRange("crystalsPerParallel", 16, 1, 64);

        SEED_CAPACITY_PER_UPGRADE = builder
                .comment("Extra seed slot capacity granted by every speed or energy upgrade card inserted",
                        "into an upgrade slot. The tier already sets the base capacity of the single seed slot.")
                .defineInRange("seedCapacityPerUpgrade", 16, 0, 32);

        MAX_SEED_CAPACITY = builder
                .comment("Hard upper bound of the seed slot capacity.",
                        "Each tier already brings its own base capacity (64 / 80 / 96 / 112),",
                        "1.20.1 keeps the stack count in a signed byte so 127 is the absolute limit.")
                .defineInRange("maxSeedCapacity", 127, 1, 127);

        MAX_PARALLEL = builder
                .comment("Upper bound of parallel lanes a single machine may run.")
                .defineInRange("maxParallel", 64, 1, 4096);

        BASE_SPEED_MULTIPLIER = builder
                .comment("Global base speed multiplier applied to every recipe (higher = faster).",
                        "Energy per tick is scaled by the same factor, so the total FE per operation stays constant.")
                .defineInRange("baseSpeedMultiplier", 4.0D, 0.25D, 64.0D);

        BASE_ENERGY_MULTIPLIER = builder
                .comment("Global factor applied to the energy cost of every operation (1.0 = recipe value).",
                        "Default 0.5, i.e. everything runs on half the energy the recipe asks for.",
                        "Total FE per crystal = recipe energy * ticks * this factor / energy upgrades,",
                        "so lowering it is the cheapest way to make the machine lazy friendly.")
                .defineInRange("baseEnergyMultiplier", 0.5D, 0.01D, 64.0D);

        SPEED_UPGRADE_SPEED_PER_CARD = builder
                .comment("Mekanism style speed card: every card multiplies the processing speed by this factor",
                        "(2.0 = each card halves the time one batch needs) and raises the FE per tick by the",
                        "same factor, so one operation always costs the same amount of energy.")
                .defineInRange("speedUpgradeSpeedPerCard", 2.0D, 1.0D, 16.0D);

        ENERGY_UPGRADE_EFFICIENCY_FACTOR = builder
                .comment("Mekanism style energy card: every card divides the energy one operation needs by this",
                        "factor (2.0 = each card halves the FE cost). Energy cards never touch any energy buffer.")
                .defineInRange("energyUpgradeEfficiencyFactor", 2.0D, 1.0D, 16.0D);

        ONE_TICK_OUTPUT = builder
                .comment("Global switch: when true every machine finishes one output batch per tick,",
                        "ignoring the recipe duration. The energy cost per operation is unchanged.",
                        "Default false.")
                .define("oneTickOutput", false);

        builder.pop();
        SPEC = builder.build();
    }

    private CrystalMultiplierConfig() {
    }

    private static int intOr(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static double doubleOr(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static int crystalsPerParallel() {
        return intOr(CRYSTALS_PER_PARALLEL, 16);
    }

    public static int seedCapacityPerUpgrade() {
        return intOr(SEED_CAPACITY_PER_UPGRADE, 16);
    }

    public static int maxSeedCapacity() {
        return intOr(MAX_SEED_CAPACITY, 127);
    }

    public static int maxParallel() {
        return intOr(MAX_PARALLEL, 64);
    }

    public static double baseSpeedMultiplier() {
        return doubleOr(BASE_SPEED_MULTIPLIER, 4.0D);
    }

    public static double baseEnergyMultiplier() {
        return doubleOr(BASE_ENERGY_MULTIPLIER, 0.5D);
    }

    public static double speedUpgradeSpeedPerCard() {
        return doubleOr(SPEED_UPGRADE_SPEED_PER_CARD, 2.0D);
    }

    public static double energyUpgradeEfficiencyFactor() {
        return doubleOr(ENERGY_UPGRADE_EFFICIENCY_FACTOR, 2.0D);
    }

    public static boolean oneTickOutput() {
        return boolOr(ONE_TICK_OUTPUT, false);
    }
}
