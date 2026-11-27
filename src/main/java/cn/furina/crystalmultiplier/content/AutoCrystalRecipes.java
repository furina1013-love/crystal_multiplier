package cn.furina.crystalmultiplier.content;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Generates the crystal multiplication recipes for crystals that live in optional addon mods.
 *
 * <p>The three vanilla-style crystals (certus quartz, charged certus quartz and fluix) keep their
 * hand written datapack files. Everything else used to be a {@code forge:conditional} json, which the
 * 1.20.1 recipe manager refuses to load ("serializer returned null") because the inner recipe is
 * parsed before the condition is evaluated. Instead this reload listener appends the recipe in code:
 * if the addon mod is loaded and the item actually exists in the registry, a recipe with the same
 * numbers as the old json is injected into the {@link RecipeManager} right after the datapack ones,
 * so no json file and no condition is involved.</p>
 */
public final class AutoCrystalRecipes extends SimplePreparableReloadListener<Void> {

    /** One addon crystal: mod id, item path and the numbers the old json used. */
    private record Candidate(String modId, String itemPath, int energyPerTick, int ticks) {
        ResourceLocation itemId() {
            return new ResourceLocation(this.modId, this.itemPath);
        }
    }

    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate("extendedae_plus", "lattra_crystal", 340, 300),
            new Candidate("appflux", "redstone_crystal", 320, 280));

    private static final Logger LOGGER = LogUtils.getLogger();

    // 生成配方的数值不再写死：能量/耗时来自 CrystalMultiplierConfig 的
    // defaultCrystalEnergyPerTick / defaultCrystalTicks，单个物品可以用 recipeOverrides
    // （"modid:item=energy:ticks"）覆盖，见 parseOverrides()。

    /** Mods whose "crystal" items are ore processing intermediates, not AE2 style crystals. */
    private static final Set<String> BLACKLISTED_NAMESPACES = Set.of("minecraft", "mekanism",
            CrystalMultiplierMod.MODID);


    /** 通用工具标签（"c:tools"，Forge/NeoForge 生态通用），覆盖未被 vanilla 工具标签收录的模组工具。 */
    private static final TagKey<Item> C_TOOLS = TagKey.create(Registries.ITEM, new ResourceLocation("c", "tools"));

    /** 名字里带 seed 的是种子而不是水晶本体。 */
    private static boolean isSeedLike(String path) {
        return path.contains("seed");
    }

    /** Mod whose crystal catalyser recipes are mirrored (AE2 Lightning Tech). */
    private static final String CATALYZER_MODID = "ae2lt";
    /** Folder those recipes live in. */
    private static final String CATALYZER_RECIPE_DIR = "recipes/crystal_catalyzer";
    /** One second per batch, the catalyser's minimum processing time. */
    private static final int CATALYZER_TICKS = 20;

    private static final int RESULT_COUNT = 2;
    private static final int CRYSTALS_PER_PARALLEL = 16;

    private final RecipeManager recipeManager;

    public AutoCrystalRecipes(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    @Override
    protected Void prepare(@NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        // 总开关：关掉之后一条自动配方都不注入（手写 json 的三条 + 数据包配方不受影响）。
        if (CrystalMultiplierConfig.disableAutoRecipes()) {
            LOGGER.debug("crystal_multiplier: disableAutoRecipes is on, skipping the generated crystal recipes");
            return;
        }
        Collection<Recipe<?>> existing = this.recipeManager.getRecipes();
        List<Recipe<?>> all = new ArrayList<>(existing.size() + CANDIDATES.size());
        all.addAll(existing);
        // 已经有配方的种子不再重复生成
        Set<Item> covered = new HashSet<>();
        for (Recipe<?> recipe : existing) {
            if (recipe instanceof CrystalMultiplicationRecipe multi) {
                for (ItemStack stack : multi.getSeed().getItems()) {
                    covered.add(stack.getItem());
                }
            }
        }
        // 配置里每个物品的临时覆盖值（"modid:item=energy:ticks"），优先级最高。
        Map<Item, int[]> overrides = parseOverrides();
        // 闪电科技（AE2LT）的水晶催化器配方：能量/时间/产量按它的数值来。
        Map<Item, int[]> catalyzer = readCatalyzerRecipes(resourceManager);
        int defaultEnergy = CrystalMultiplierConfig.defaultCrystalEnergyPerTick();
        int defaultTicks = CrystalMultiplierConfig.defaultCrystalTicks();
        int added = 0;
        for (Candidate candidate : CANDIDATES) {
            if (!ModList.get().isLoaded(candidate.modId())) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(candidate.itemId());
            if (item == null || item == Items.AIR) {
                continue;
            }
            ResourceLocation id = new ResourceLocation(CrystalMultiplierMod.MODID,
                    "crystal_multiplication/auto_" + candidate.itemPath());
            if (this.recipeManager.byKey(id).isPresent()) {
                continue;
            }
            // 优先级：recipeOverrides > 该附属自带的数值（与老 json 一致）> 全局默认值。
            int[] override = overrides.get(item);
            int energy = override != null ? override[0] : candidate.energyPerTick();
            int ticks = override != null ? override[1] : candidate.ticks();
            all.add(new CrystalMultiplicationRecipe(id, Ingredient.of(item), new ItemStack(item, RESULT_COUNT),
                    energy, ticks, CRYSTALS_PER_PARALLEL));
            covered.add(item);
            added++;
        }
        // 兜底：扫描注册表，任何模组里 id 带 crystal 的物品都补一条配方，
        // 这样 AE2 附属（appflux / extendedae / 其它）里的水晶不需要一个个手写。
        for (Map.Entry<ResourceKey<Item>, Item> entry : ForgeRegistries.ITEMS.getEntries()) {
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
            ResourceLocation recipeId = new ResourceLocation(CrystalMultiplierMod.MODID,
                    "crystal_multiplication/auto_" + namespace + "_" + itemId.getPath());
            if (this.recipeManager.byKey(recipeId).isPresent()) {
                continue;
            }
            int[] override = overrides.get(item);
            int[] mirrored = catalyzer.get(item);
            int energy = override != null ? override[0] : mirrored != null ? mirrored[0] : defaultEnergy;
            int ticks = override != null ? override[1] : mirrored != null ? mirrored[1] : defaultTicks;
            int resultCount = mirrored != null ? mirrored[2] : RESULT_COUNT;
            all.add(new CrystalMultiplicationRecipe(recipeId, Ingredient.of(item),
                    new ItemStack(item, resultCount), energy, ticks, CRYSTALS_PER_PARALLEL));
            added++;
        }
        // 闪电科技里名字不带 crystal 的产物（数据包自定义的水晶之类）也补上。
        for (Map.Entry<Item, int[]> entry : catalyzer.entrySet()) {
            Item item = entry.getKey();
            if (!covered.add(item)) {
                continue;
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId == null) {
                continue;
            }
            ResourceLocation recipeId = new ResourceLocation(CrystalMultiplierMod.MODID,
                    "crystal_multiplication/auto_" + itemId.getNamespace() + "_" + itemId.getPath());
            if (this.recipeManager.byKey(recipeId).isPresent()) {
                continue;
            }
            int[] override = overrides.get(item);
            int[] mirrored = entry.getValue();
            all.add(new CrystalMultiplicationRecipe(recipeId, Ingredient.of(item),
                    new ItemStack(item, mirrored[2]),
                    override != null ? override[0] : mirrored[0],
                    override != null ? override[1] : mirrored[1],
                    CRYSTALS_PER_PARALLEL));
            added++;
        }
        if (added > 0) {
            this.recipeManager.replaceRecipes(all);
        }
    }

    @Override
    public @NotNull String getName() {
        return CrystalMultiplierMod.MODID + ":auto_crystal_recipes";
    }

    /**
     * 解析 {@link CrystalMultiplierConfig#recipeOverrides()} 里的 {@code modid:item=energy:ticks} 行。
     *
     * <p>解析失败的条目一律忽略并打一行 warn（不会让整合包作者的一个笔误把整个数据包重载搞崩），
     * 返回值按物品索引，value 是 {@code {energyPerTick, ticks}}。</p>
     */
    private static Map<Item, int[]> parseOverrides() {
        List<String> raw = CrystalMultiplierConfig.recipeOverrides();
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<Item, int[]> parsed = new HashMap<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int split = entry.indexOf('=');
            if (split <= 0) {
                LOGGER.warn("crystal_multiplier: ignoring malformed recipeOverrides entry '{}', expected 'modid:item=energy:ticks'", entry);
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(entry.substring(0, split).trim());
            String[] numbers = entry.substring(split + 1).trim().split(":", -1);
            if (itemId == null || numbers.length != 2) {
                LOGGER.warn("crystal_multiplier: ignoring malformed recipeOverrides entry '{}', expected 'modid:item=energy:ticks'", entry);
                continue;
            }
            int energy;
            int ticks;
            try {
                energy = Integer.parseInt(numbers[0].trim());
                ticks = Integer.parseInt(numbers[1].trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("crystal_multiplier: ignoring recipeOverrides entry '{}', energy and ticks must be whole numbers", entry);
                continue;
            }
            if (energy < 1 || ticks < 1) {
                LOGGER.warn("crystal_multiplier: ignoring recipeOverrides entry '{}', energy and ticks must be >= 1", entry);
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) {
                LOGGER.warn("crystal_multiplier: ignoring recipeOverrides entry '{}', unknown item '{}'", entry, itemId);
                continue;
            }
            parsed.put(item, new int[]{energy, ticks});
        }
        return parsed;
    }

    /**
     * Reads the crystal catalyser recipes of "AE2 Lightning Tech" ({@code ae2lt}) and turns them into
     * numbers this machine can use, so the crystals that mod adds cost the same here as they do there.
     *
     * <p>Their machine takes a crystal block as a catalyst (never consumed), a fluid, FE and one bolt
     * of lightning, and produces the matching crystal in one second. This machine has no fluid and no
     * lightning, so only the FE part of the cost is carried over: one second per batch and
     * {@code energyPerCycle / 20} FE per tick. Recipes from the {@code dust/} folder are skipped (those
     * make dust, not crystals).</p>
     *
     * @return output item -> {@code {energyPerTick, ticks, amount}}
     */
    private static Map<Item, int[]> readCatalyzerRecipes(ResourceManager resourceManager) {
        if (!ModList.get().isLoaded(CATALYZER_MODID)) {
            return Map.of();
        }
        Map<Item, int[]> result = new HashMap<>();
        Map<ResourceLocation, ?> files = resourceManager.listResources(CATALYZER_RECIPE_DIR,
                path -> path.getPath().endsWith(".json"));
        for (ResourceLocation file : files.keySet()) {
            if (file.getPath().contains("/dust/")) {
                continue;
            }
            try (var reader = resourceManager.getResource(file).orElseThrow().openAsReader()) {
                com.google.gson.JsonObject root =
                        com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("output")) {
                    continue;
                }
                com.google.gson.JsonObject output = root.getAsJsonObject("output");
                ResourceLocation outputId = ResourceLocation.tryParse(output.get("id").getAsString());
                if (outputId == null) {
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(outputId);
                if (item == null || item == Items.AIR || result.containsKey(item)) {
                    continue;
                }
                int count = output.has("count") ? Math.max(1, output.get("count").getAsInt()) : 1;
                int energyPerCycle = root.has("energyPerCycle") ? root.get("energyPerCycle").getAsInt() : 100_000;
                int ticks = CATALYZER_TICKS;
                int energyPerTick = Math.max(1, energyPerCycle / ticks);
                result.put(item, new int[]{energyPerTick, ticks, count});
            } catch (RuntimeException | java.io.IOException e) {
                LOGGER.debug("crystal_multiplier: skipping unreadable catalyser recipe {}", file, e);
            }
        }
        return result;
    }

    /** 工具类物品（镐斧锹锄剑/剪刀之类）不算水晶。 */
    private static boolean isToolLike(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.HOES) || stack.is(ItemTags.SWORDS) || stack.is(C_TOOLS);
    }
}
