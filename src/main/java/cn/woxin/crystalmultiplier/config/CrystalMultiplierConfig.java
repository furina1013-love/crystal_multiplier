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
    public static final ForgeConfigSpec.IntValue BASE_SEED_CAPACITY;
    public static final ForgeConfigSpec.IntValue SEED_CAPACITY_PER_UPGRADE;
    public static final ForgeConfigSpec.IntValue MAX_SEED_CAPACITY;
    public static final ForgeConfigSpec.IntValue MAX_PARALLEL;
    public static final ForgeConfigSpec.DoubleValue BASE_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ONE_TICK_OUTPUT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Crystal Multiplier: parallelism, seed slot, speed and output settings").push("machine");

        CRYSTALS_PER_PARALLEL = builder
                .comment("How many crystals of one kind must sit in the seed slot to form one parallel lane.",
                        "The seed slot content must always be a multiple of this value, otherwise the machine idles.",
                        "A single recipe may override it with its own \"crystals_per_parallel\" field.")
                .defineInRange("crystalsPerParallel", 16, 1, 64);

        BASE_SEED_CAPACITY = builder
                .comment("Seed slot capacity of a machine without any upgrade card.")
                .defineInRange("baseSeedCapacity", 64, 1, 127);

        SEED_CAPACITY_PER_UPGRADE = builder
                .comment("Extra seed slot capacity granted by every upgrade card inserted into an upgrade slot.")
                .defineInRange("seedCapacityPerUpgrade", 16, 0, 127);

        MAX_SEED_CAPACITY = builder
                .comment("Hard upper bound of the seed slot capacity.",
                        "64 is the vanilla safe maximum, 1.20.1 keeps the stack count in a signed byte so 127 is the absolute limit.")
                .defineInRange("maxSeedCapacity", 64, 1, 127);

        MAX_PARALLEL = builder
                .comment("Upper bound of parallel lanes a single machine may run.")
                .defineInRange("maxParallel", 64, 1, 4096);

        BASE_SPEED_MULTIPLIER = builder
                .comment("Global base speed multiplier applied to every recipe (higher = faster).",
                        "Energy per tick is scaled by the same factor, so the total FE per operation stays constant.")
                .defineInRange("baseSpeedMultiplier", 4.0D, 0.25D, 64.0D);

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

    public static int baseSeedCapacity() {
        return intOr(BASE_SEED_CAPACITY, 64);
    }

    public static int seedCapacityPerUpgrade() {
        return intOr(SEED_CAPACITY_PER_UPGRADE, 16);
    }

    public static int maxSeedCapacity() {
        return intOr(MAX_SEED_CAPACITY, 64);
    }

    public static int maxParallel() {
        return intOr(MAX_PARALLEL, 64);
    }

    public static double baseSpeedMultiplier() {
        return doubleOr(BASE_SPEED_MULTIPLIER, 4.0D);
    }

    public static boolean oneTickOutput() {
        return boolOr(ONE_TICK_OUTPUT, false);
    }
}
