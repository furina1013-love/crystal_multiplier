package cn.woxin.crystalmultiplier.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common (server side) config of the Crystal Multiplier.
 * Everything the user asked to be tunable lives here: how many crystals form one
 * parallel lane, how big the seed slot is and how fast the machines run.
 * There are two "eject every tick" switches: one for this machine (on by default) and
 * one that applies the same rule to every Mekanism machine through {@code EjectorMixin}
 * (off by default). Neither of them changes crafting speed.
 */
public final class CrystalMultiplierConfig {

    /**
     * Base translation key prefix shared with the language files. Forge config screen mods
     * (Configured, Forge Config Screens, ...) read {@code ValueSpec.getTranslationKey()},
     * so every option advertises {@code <prefix>.<section>.<key>} and a matching
     * {@code ...tooltip} key for the description text.
     */
    public static final String LANG_PREFIX = "crystal_multiplier.configuration.machine";

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue CRYSTALS_PER_PARALLEL;
    public static final ForgeConfigSpec.IntValue SEED_CAPACITY_PER_UPGRADE;
    public static final ForgeConfigSpec.IntValue SEED_SLOT_CAP_CEILING;
    public static final ForgeConfigSpec.IntValue MAX_PARALLEL;
    public static final ForgeConfigSpec.DoubleValue BASE_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BASE_ENERGY_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_UPGRADE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue EJECT_EVERY_TICK;
    public static final ForgeConfigSpec.BooleanValue GLOBAL_EJECT_EVERY_TICK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Crystal Multiplier: parallelism, seed slot, speed and output settings")
                .translation(LANG_PREFIX)
                .push("machine");

        CRYSTALS_PER_PARALLEL = builder
                .comment("How many crystals of one kind must sit in the seed slot to form one parallel lane.",
                        "The lanes are floor(count / value): leftovers that do not fill a whole lane are",
                        "simply ignored, the machine keeps running as long as one lane is filled.",
                        "A single recipe may override it with its own \"crystals_per_parallel\" field.")
                .translation(LANG_PREFIX + ".crystalsPerParallel")
                .defineInRange("crystalsPerParallel", 16, 1, 64);

        SEED_CAPACITY_PER_UPGRADE = builder
                .comment("Extra seed slot capacity granted by every speed or energy upgrade card inserted",
                        "into an upgrade slot. The tier already sets the base capacity of the single seed slot.")
                .translation(LANG_PREFIX + ".seedCapacityPerUpgrade")
                .defineInRange("seedCapacityPerUpgrade", 16, 0, 32);

        SEED_SLOT_CAP_CEILING = builder
                .comment("Hard upper bound of the seed slot capacity.",
                        "Each tier already brings its own base capacity (64 / 128 / 256 / 512),",
                        "plus the bonus of every upgrade card inserted.",
                        "The machine stores anything above 127 items in a hidden overflow counter,",
                        "so values beyond the 1.20.1 byte limit are safe here.")
                .translation(LANG_PREFIX + ".seedSlotCapCeiling")
                .defineInRange("seedSlotCapCeiling", 1024, 64, 4096);

        MAX_PARALLEL = builder
                .comment("Upper bound of parallel lanes a single machine may run.")
                .translation(LANG_PREFIX + ".maxParallel")
                .defineInRange("maxParallel", 64, 1, 4096);

        BASE_SPEED_MULTIPLIER = builder
                .comment("Global base speed multiplier applied to every recipe (higher = faster).",
                        "Energy per tick is scaled by the same factor, so the total FE per operation stays constant.")
                .translation(LANG_PREFIX + ".baseSpeedMultiplier")
                .defineInRange("baseSpeedMultiplier", 4.0D, 0.25D, 64.0D);

        BASE_ENERGY_MULTIPLIER = builder
                .comment("Global factor applied to the energy cost of every operation (1.0 = recipe value).",
                        "Default 0.5, i.e. everything runs on half the energy the recipe asks for.",
                        "Total FE per crystal = recipe energy * ticks * this factor / energy upgrades,",
                        "so lowering it is the cheapest way to make the machine lazy friendly.")
                .translation(LANG_PREFIX + ".baseEnergyMultiplier")
                .defineInRange("baseEnergyMultiplier", 0.5D, 0.01D, 64.0D);

        MAX_UPGRADE_MULTIPLIER = builder
                .comment("Same option as Mekanism's own general.maxUpgradeMultiplier: the base factor used to",
                        "work out machine performance with upgrades, applied as",
                        "multiplier ^ (upgrades installed / upgrades possible).",
                        "With 10 (the Mekanism default) eight speed cards make the machine ten times faster,",
                        "and eight energy cards cut the FE per tick to a tenth.",
                        "Speed cards also raise the FE per tick by multiplier ^ (2 * fraction), which is exactly",
                        "what a Mekanism machine does - speed is an overclock, not a free lunch.")
                .translation(LANG_PREFIX + ".maxUpgradeMultiplier")
                .defineInRange("maxUpgradeMultiplier", 10, 1, 1000);

        EJECT_EVERY_TICK = builder
                .comment("Crystal Multiplier only: push already produced items out of the output slots every",
                        "tick, instead of Mekanism's default 10 tick ejection interval.",
                        "Crafting speed is not touched by this option. Default true.")
                .translation(LANG_PREFIX + ".ejectEveryTick")
                .define("ejectEveryTick", true);

        GLOBAL_EJECT_EVERY_TICK = builder
                .comment("The same per tick ejection for every Mekanism machine (own machines, the factory",
                        "family and addon machines), instead of the default 10 tick interval.",
                        "Crafting speed is not touched by this option. Default false.")
                .translation(LANG_PREFIX + ".globalEjectEveryTick")
                .define("globalEjectEveryTick", false);

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

    public static int seedSlotCapCeiling() {
        return intOr(SEED_SLOT_CAP_CEILING, 1024);
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

    public static int maxUpgradeMultiplier() {
        return intOr(MAX_UPGRADE_MULTIPLIER, 10);
    }

    /** Push the finished products out of the output slots every tick, our machine only. On by default. */
    public static boolean ejectEveryTick() {
        return boolOr(EJECT_EVERY_TICK, true);
    }

    /** The same per tick ejection for every Mekanism machine. Off by default. */
    public static boolean globalEjectEveryTick() {
        return boolOr(GLOBAL_EJECT_EVERY_TICK, false);
    }
}
