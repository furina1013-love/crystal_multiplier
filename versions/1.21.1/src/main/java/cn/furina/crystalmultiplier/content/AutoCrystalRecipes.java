package cn.furina.crystalmultiplier.content;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Generates the crystal multiplication recipes for crystals that live in optional addon mods.
 *
 * <p>The three vanilla-style crystals (certus quartz, charged certus quartz and fluix) keep their
 * hand written data pack files. Everything else used to be a {@code forge:conditional} json, which
 * the recipe manager refuses to load ("serializer returned null") because the inner recipe is
 * parsed before the condition is evaluated. Instead this reload listener appends the recipe in code:
 * if the addon mod is loaded and the item actually exists in the registry, a recipe with the same
 * numbers as the old json is injected into the {@link RecipeManager} right after the data pack ones,
 * so no json file and no condition is involved.</p>
 *
 * <p>1.21.1 notes: {@link RecipeManager} stores {@link RecipeHolder}s now, so the appended list is
 * {@code RecipeHolder<?>} and {@code replaceRecipes(Iterable<RecipeHolder<?>>)} keeps the ids.
 * {@code ForgeRegistries} is gone, items are looked up through {@link BuiltInRegistries}.
 * Wiring (game bus, verified against Mekanism 10.7 which does the same):
 * {@code NeoForge.EVENT_BUS.addListener(AutoCrystalRecipes::onAddReloadListeners)} or, equivalently,
 * {@code NeoForge.EVENT_BUS.addListener(event -> event.addListener(new AutoCrystalRecipes(
 * event.getServerResources().getRecipeManager())))}. Nothing needs the mod bus.</p>
 */
public final class AutoCrystalRecipes extends SimplePreparableReloadListener<Void> {

    /** Only used for the (rare) "this config line is broken" report, so the game never crashes on it. */
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("crystal_multiplier/auto_recipes");

    /** One addon crystal: mod id, item path and the numbers the old json used. */
    private record Candidate(String modId, String itemPath, int energyPerTick, int ticks) {
        ResourceLocation itemId() {
            return ResourceLocation.fromNamespaceAndPath(this.modId, this.itemPath);
        }
    }

    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate("extendedae_plus", "lattra_crystal", 340, 300),
            new Candidate("appflux", "redstone_crystal", 320, 280));

    /**
     * The two numbers of a generated recipe: FE/t and ticks at 1x tier speed.
     * Kept as a record so the override map and the two "where does a recipe come from" branches share
     * one type.
     */
    private record Numbers(int energyPerTick, int ticks) {
    }

    /** Mods whose "crystal" items are ore processing intermediates, not AE2 style crystals. */
    private static final Set<String> BLACKLISTED_NAMESPACES = Set.of("minecraft", "mekanism",
            CrystalMultiplierMod.MODID);


    /** 通用工具标签（"c:tools"，Forge/NeoForge 生态通用），覆盖未被 vanilla 工具标签收录的模组工具。 */
    private static final TagKey<Item> C_TOOLS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools"));

    /** 名字里带 seed 的是种子而不是水晶本体。 */
    private static boolean isSeedLike(String path) {
        return path.contains("seed");
    }

    /** Same shape as the data pack recipes: 2 crystals out. */
    private static final int RESULT_COUNT = 2;

    private final RecipeManager recipeManager;

    public AutoCrystalRecipes(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    /**
     * 1.21.1 replacement for the Forge 1.20.1 {@code AddReloadListenerEvent} listener:
     * the event still exists in {@code net.neoforged.neoforge.event} and still exposes the
     * {@link RecipeManager} through {@code getServerResources().getRecipeManager()}.
     * Listeners added here run <em>after</em> the vanilla ones, so the recipes are already loaded
     * when {@link #apply(Void, ResourceManager, ProfilerFiller)} runs.
     */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AutoCrystalRecipes(event.getServerResources().getRecipeManager()));
    }

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler) {
        // Master switch: with the option on nothing is generated at all. The three hand written json
        // recipes live in the data pack, so they are loaded by the vanilla reload and stay available.
        if (CrystalMultiplierConfig.disableAutoRecipes()) {
            LOGGER.info("Auto recipe generation is disabled by the config (disableAutoRecipes = true)");
            return;
        }
        // Resolved once per reload, so editing the config + /reload is enough to see a new value.
        Map<ResourceLocation, Numbers> overrides = parseOverrides();
        Numbers defaults = new Numbers(CrystalMultiplierConfig.defaultCrystalEnergyPerTick(),
                CrystalMultiplierConfig.defaultCrystalTicks());
        int crystalsPerParallel = Math.max(1, CrystalMultiplierConfig.crystalsPerParallel());
        Collection<RecipeHolder<?>> existing = this.recipeManager.getRecipes();
        List<RecipeHolder<?>> all = new ArrayList<>(existing.size() + CANDIDATES.size());
        all.addAll(existing);
        // 已经有配方的种子不再重复生成
        Set<Item> covered = new HashSet<>();
        for (RecipeHolder<?> holder : existing) {
            if (holder.value() instanceof CrystalMultiplicationRecipe multi) {
                for (ItemStack stack : multi.getSeed().getItems()) {
                    covered.add(stack.getItem());
                }
            }
        }
        int added = 0;
        for (Candidate candidate : CANDIDATES) {
            if (!ModList.get().isLoaded(candidate.modId())) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(candidate.itemId()).filter(found -> found != Items.AIR).orElse(null);
            if (item == null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CrystalMultiplierMod.MODID,
                    "crystal_multiplication/auto_" + candidate.itemPath());
            if (this.recipeManager.byKey(id).isPresent()) {
                continue;
            }
            // A config override for this exact item wins over the numbers shipped for the addon.
            Numbers numbers = overrides.getOrDefault(candidate.itemId(),
                    new Numbers(candidate.energyPerTick(), candidate.ticks()));
            all.add(new RecipeHolder<>(id, new CrystalMultiplicationRecipe(Ingredient.of(item),
                    new ItemStack(item, RESULT_COUNT), numbers.energyPerTick(), numbers.ticks(),
                    crystalsPerParallel)));
            covered.add(item);
            added++;
        }
        // 兜底：扫描注册表，任何模组里 id 带 crystal 的物品都补一条配方，
        // 这样 AE2 附属（appflux / extendedae / 其它）里的水晶不需要一个个手写。
        for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation itemId = entry.getKey().location();
            if (!itemId.getPath().contains("crystal")) {
                continue;
            }
            // 水晶种子之类（如水晶科技的水晶种子）不是水晶本体，跳过
            if (isSeedLike(itemId.getPath())) {
                continue;
            }
            String namespace = itemId.getNamespace();
            if (BLACKLISTED_NAMESPACES.contains(namespace) || !ModList.get().isLoaded(namespace)) {
                continue;
            }
            // 只兼容 AE 生态（模组 id 带 ae / app / applied / mega 这些特征）
            if (!isAeAddon(namespace)) {
                continue;
            }
            Item item = entry.getValue();
            // 可放置的方块物品不要（水晶应该是纯物品）
            if (item instanceof BlockItem) {
                continue;
            }
            // 工具类也不要
            if (isToolLike(item)) {
                continue;
            }
            if (!covered.add(item)) {
                continue;
            }
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(CrystalMultiplierMod.MODID,
                    "crystal_multiplication/auto_" + namespace + "_" + itemId.getPath());
            if (this.recipeManager.byKey(recipeId).isPresent()) {
                continue;
            }
            Numbers numbers = overrides.getOrDefault(itemId, defaults);
            all.add(new RecipeHolder<>(recipeId, new CrystalMultiplicationRecipe(Ingredient.of(item),
                    new ItemStack(item, RESULT_COUNT), numbers.energyPerTick(), numbers.ticks(),
                    crystalsPerParallel)));
            added++;
        }
        if (added > 0) {
            this.recipeManager.replaceRecipes(all);
        }
    }

    /** One parsed {@code recipeOverrides} line: which item and the two numbers it should use. */
    private record OverrideEntry(ResourceLocation itemId, Numbers numbers) {
    }

    /**
     * Parses the {@code recipeOverrides} config entries ({@code "modid:item=energyPerTick:ticks"}).
     * A broken entry never aborts the reload: it is skipped and reported with a single warning line.
     */
    private static Map<ResourceLocation, Numbers> parseOverrides() {
        List<String> entries = CrystalMultiplierConfig.recipeOverrides();
        if (entries.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, Numbers> parsed = new HashMap<>();
        for (String entry : entries) {
            OverrideEntry override = parseOverride(entry);
            if (override == null) {
                LOGGER.warn("Ignoring broken recipeOverrides entry \"{}\" (expected \"modid:item=energyPerTick:ticks\")",
                        entry);
                continue;
            }
            parsed.put(override.itemId(), override.numbers());
        }
        return parsed;
    }

    /** {@code null} when the line is not exactly {@code modid:item=energyPerTick:ticks} with two numbers > 0. */
    @Nullable
    private static OverrideEntry parseOverride(String entry) {
        int equal = entry.indexOf('=');
        if (equal <= 0) {
            return null;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(entry.substring(0, equal).trim());
        if (itemId == null) {
            return null;
        }
        String tail = entry.substring(equal + 1);
        int colon = tail.indexOf(':');
        if (colon < 0) {
            return null;
        }
        try {
            int energy = Integer.parseInt(tail.substring(0, colon).trim());
            int ticks = Integer.parseInt(tail.substring(colon + 1).trim());
            if (energy <= 0 || ticks <= 0) {
                return null;
            }
            return new OverrideEntry(itemId, new Numbers(energy, ticks));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getName() {
        return CrystalMultiplierMod.MODID + ":auto_crystal_recipes";
    }

    /** AE 生态判定：模组 id 里带 ae / app / applied / mega 之类特征才算水晶来源。 */
    private static boolean isAeAddon(String namespace) {
        return namespace.contains("ae") || namespace.startsWith("app")
                || namespace.contains("applied") || namespace.contains("mega");
    }

    /** 工具类物品（镐斧锹锄剑/剪刀之类）不算水晶。 */
    private static boolean isToolLike(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.HOES) || stack.is(ItemTags.SWORDS) || stack.is(C_TOOLS);
    }
}
