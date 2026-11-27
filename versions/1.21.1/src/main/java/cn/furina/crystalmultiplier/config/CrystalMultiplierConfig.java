package cn.furina.crystalmultiplier.config;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common (server side) config of the Crystal Multiplier.
 * Everything the user asked to be tunable lives here: how many crystals form one
 * parallel lane, how big the seed slot is and how fast the machines run.
 * There are two "eject every tick" switches: one for this machine (on by default) and
 * one that applies the same rule to every Mekanism machine (off by default). Neither of
 * them changes crafting speed.
 * <p>
 * 1.21.1 port: this is NeoForge's {@link ModConfigSpec} (same builder API as Forge's
 * {@code ForgeConfigSpec}). The spec is registered by the {@code @Mod} class through
 * {@code ModContainer#registerConfig(ModConfig.Type.COMMON, SPEC)}.
 */
public final class CrystalMultiplierConfig {

    /**
     * Base translation key prefix shared with the language files. Config screen mods
     * (Configured, NeoForge's own config GUI, ...) read {@code ValueSpec.getTranslationKey()},
     * so every option advertises {@code <prefix>.<section>.<key>} and a matching
     * {@code ...tooltip} key for the description text.
     */
    public static final String LANG_PREFIX = "crystal_multiplier.configuration.machine";

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue CRYSTALS_PER_PARALLEL;
    public static final ModConfigSpec.IntValue SEED_SLOT_CAP_CEILING;
    public static final ModConfigSpec.IntValue MAX_PARALLEL;
    public static final ModConfigSpec.DoubleValue BASE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BASE_ENERGY_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_UPGRADE_MULTIPLIER;
    public static final ModConfigSpec.IntValue UPGRADE_CARDS_PER_STEP;
    public static final ModConfigSpec.BooleanValue EJECT_EVERY_TICK;
    public static final ModConfigSpec.BooleanValue GLOBAL_EJECT_EVERY_TICK;
    /** Numbers the auto generated (code side) recipes use when nothing overrides them. */
    public static final ModConfigSpec.IntValue DEFAULT_CRYSTAL_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue DEFAULT_CRYSTAL_TICKS;
    /** Per item overrides of those numbers, "modid:item=energyPerTick:ticks". */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMPAT_RECIPE_TYPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMPAT_SKIP_KEYWORDS;
    public static final ModConfigSpec.IntValue COMPAT_TICKS_PER_CYCLE;
    /** Master switch for the whole auto generation (the hand written json recipes stay either way). */
    public static final ModConfigSpec.BooleanValue DISABLE_AUTO_RECIPES;
    /** Format version of this file, reserved for future migrations. */
    public static final ModConfigSpec.IntValue CONFIG_VERSION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        // TODO(1.21.1): the multi-line comment("a", "b", ...) form is the Forge varargs overload; only the
        //  single-argument comment(String) shows up in the 1.21.1 bytecode I could inspect offline. If the
        //  first compile rejects the varargs calls, join the lines into one string instead.
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

        SEED_SLOT_CAP_CEILING = builder
                .comment("Hard upper bound of the seed slot capacity.",
                        "Each tier already brings its own base capacity (64 / 128 / 256 / 512),",
                        "plus the bonus of every upgrade card inserted.",
                        "The machine stores anything above the vanilla stack limit in a hidden overflow counter,",
                        "so large values are safe here.")
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

        UPGRADE_CARDS_PER_STEP = builder
                .comment("How many upgrade cards of one kind make up one full multiplier step.",
                        "Vanilla Mekanism caps a machine at 8 cards per type, so the default 8 means",
                        "8 speed cards = maxUpgradeMultiplier x more parallel lanes. Addons that raise",
                        "the card cap keep multiplying beyond that: 16 cards = base^2, 24 = base^3, ...",
                        "Lower this value to make every single card worth more.")
                .translation(LANG_PREFIX + ".upgradeCardsPerStep")
                .defineInRange("upgradeCardsPerStep", 8, 1, 512);

        // ---------------------------------------------------------------- recipe defaults
        DEFAULT_CRYSTAL_ENERGY_PER_TICK = builder
                .comment("Energy per tick of a recipe that this mod generates in code (the addon crystals and",
                        "the ones found by scanning the item registry). Changes the FE/t of those recipes only;",
                        "the three hand written json recipes keep their own numbers.",
                        "The real cost per operation still runs through baseEnergyMultiplier and the heat of",
                        "the tier, exactly like before. Default 320.")
                .translation(LANG_PREFIX + ".defaultCrystalEnergyPerTick")
                .defineInRange("defaultCrystalEnergyPerTick", 320, 1, 1_000_000);

        DEFAULT_CRYSTAL_TICKS = builder
                .comment("Ticks of a recipe generated in code at 1x tier speed (before the tier speed and",
                        "baseSpeedMultiplier are applied). Default 280.")
                .translation(LANG_PREFIX + ".defaultCrystalTicks")
                .defineInRange("defaultCrystalTicks", 280, 1, 1_000_000);

        RECIPE_OVERRIDES = builder
                .comment("Per item override of the generated recipe numbers, one entry per line:",
                        "  \"modid:item=energyPerTick:ticks\"",
                        "Example: \"appflux:redstone_crystal=400:200\" - that item then runs on 400 FE/t",
                        "for 200 ticks instead of the defaults above. Overrides win over the defaults AND",
                        "over the numbers this mod ships for a specific addon crystal.",
                        "An entry that cannot be parsed is ignored, one warning line is printed to the log,",
                        "and everything else keeps working. Default: empty.")
                .translation(LANG_PREFIX + ".recipeOverrides")
                .defineListAllowEmpty("recipeOverrides", List.<String>of(), entry -> entry instanceof String);

        DISABLE_AUTO_RECIPES = builder
                .comment("Set to true to switch the code side recipe generation off completely: no addon",
                        "crystals and no registry scan recipes are injected on world load / /reload.",
                        "The three hand written json recipes (certus quartz, charged certus quartz, fluix)",
                        "are data pack content and stay available. Default false = generate as usual.")
                .translation(LANG_PREFIX + ".disableAutoRecipes")
                .define("disableAutoRecipes", false);

        CONFIG_VERSION = builder
                .comment("Version of this config file. It exists so a future release can migrate an old file",
                        "(read this number, rewrite the file, bump it) instead of silently changing values.",
                        "Nothing in this release migrates yet; leave it alone unless a changelog says so.",
                        "Default 1.")
                .translation(LANG_PREFIX + ".configVersion")
                .defineInRange("configVersion", 1, 1, 1_000);

        COMPAT_RECIPE_TYPES = builder
                .comment("Recipe types from other mods (or data packs) that are mirrored into this machine.",
                        "Every json recipe of such a type is read at reload time: its output item becomes",
                        "the seed, energyPerCycle becomes the FE cost and its own time field (falling back to",
                        "compatTicksPerCycle) the duration, so the cost matches the source machine.",
                        "Example: ae2lt:crystal_catalyzer (AE2 Lightning Tech). Point this at any other",
                        "mod's crystal recipe type and its crystals work without a code change; a data pack",
                        "that re-defines the same type is picked up the same way. Empty list turns it off.")
                .translation(LANG_PREFIX + ".compatRecipeTypes")
                .defineListAllowEmpty("compatRecipeTypes", List.of("ae2lt:crystal_catalyzer"),
                        entry -> entry instanceof String);

        COMPAT_SKIP_KEYWORDS = builder
                .comment("External recipes whose file path contains one of these words are skipped.",
                        "The default skips the dust folder of AE2 Lightning Tech: those recipes make",
                        "crystal dust, not crystals.")
                .translation(LANG_PREFIX + ".compatSkipKeywords")
                .defineListAllowEmpty("compatSkipKeywords", List.of("dust"),
                        entry -> entry instanceof String);

        COMPAT_TICKS_PER_CYCLE = builder
                .comment("Ticks one mirrored external recipe needs when the source recipe does not state",
                        "its own processing time.")
                .translation(LANG_PREFIX + ".compatTicksPerCycle")
                .defineInRange("compatTicksPerCycle", 20, 1, 1_000_000);

        builder.pop();
        SPEC = builder.build();
    }

    private CrystalMultiplierConfig() {
    }

    private static int intOr(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static boolean boolOr(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static double doubleOr(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static int crystalsPerParallel() {
        return intOr(CRYSTALS_PER_PARALLEL, 16);
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

    public static int upgradeCardsPerStep() {
        return Math.max(1, intOr(UPGRADE_CARDS_PER_STEP, 8));
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

    /** FE/t of a recipe generated in code. Read once per recipe reload. */
    public static int defaultCrystalEnergyPerTick() {
        return Math.max(1, intOr(DEFAULT_CRYSTAL_ENERGY_PER_TICK, 320));
    }

    /** Ticks of a recipe generated in code at 1x tier speed. Read once per recipe reload. */
    public static int defaultCrystalTicks() {
        return Math.max(1, intOr(DEFAULT_CRYSTAL_TICKS, 280));
    }

    /** False (the default) means the auto recipes are generated, true means they are not. */
    public static boolean disableAutoRecipes() {
        return boolOr(DISABLE_AUTO_RECIPES, false);
    }

    /** Format version of the config file; see the comment of the option itself. */
    public static int configVersion() {
        return intOr(CONFIG_VERSION, 1);
    }

    /** Recipe types mirrored from other mods / data packs (never null). */
    public static List<String> compatRecipeTypes() {
        return strings(COMPAT_RECIPE_TYPES);
    }

    /** Path keywords that exclude an external recipe (default: "dust"). */
    public static List<String> compatSkipKeywords() {
        return strings(COMPAT_SKIP_KEYWORDS);
    }

    /** Ticks per mirrored recipe when the source recipe carries no time of its own. */
    public static int compatTicksPerCycle() {
        return intOr(COMPAT_TICKS_PER_CYCLE, 20);
    }

    private static List<String> strings(ModConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            List<String> out = new ArrayList<>();
            for (String entry : value.get()) {
                if (entry != null && !entry.isBlank()) {
                    out.add(entry.trim());
                }
            }
            return out;
        } catch (IllegalStateException e) {
            return List.of();
        }
    }

    /**
     * Raw {@code recipeOverrides} entries ("modid:item=energyPerTick:ticks"), already stripped of blank
     * lines. Parsing happens in the recipe generator so a broken entry costs one log line instead of a
     * broken config file.
     */
    public static List<String> recipeOverrides() {
        try {
            List<String> entries = new ArrayList<>();
            for (String entry : RECIPE_OVERRIDES.get()) {
                if (entry != null && !entry.isBlank()) {
                    entries.add(entry.trim());
                }
            }
            return entries;
        } catch (IllegalStateException e) {
            return List.of();
        }
    }
}
