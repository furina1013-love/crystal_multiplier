package cn.furina.crystalmultiplier.content;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.jetbrains.annotations.Nullable;
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
        Map<Item, int[]> external = readExternalRecipes(resourceManager);
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
            int[] mirrored = external.get(item);
            int energy = override != null ? override[0] : mirrored != null ? mirrored[0] : defaultEnergy;
            int ticks = override != null ? override[1] : mirrored != null ? mirrored[1] : defaultTicks;
            int resultCount = mirrored != null ? mirrored[2] : RESULT_COUNT;
            all.add(new CrystalMultiplicationRecipe(recipeId, Ingredient.of(item),
                    new ItemStack(item, resultCount), energy, ticks, CRYSTALS_PER_PARALLEL));
            added++;
        }
        // 闪电科技里名字不带 crystal 的产物（数据包自定义的水晶之类）也补上。
        for (Map.Entry<Item, int[]> entry : external.entrySet()) {
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
     * Mirrors crystal recipes that other mods - or data packs - define in their own recipe type.
     *
     * <p>Nothing here is bound to one mod id: the list of mirrored types and the skip words are config
     * options ({@code compatRecipeTypes}, {@code compatSkipKeywords}), so a fork of a known mod, or a
     * pack that re-defines the same recipe type, is picked up exactly the same way. For every matching
     * json the output item becomes the seed, {@code energyPerCycle} the FE cost, the recipe's own
     * {@code time} (falling back to {@code compatTicksPerCycle}) the duration and {@code output.count}
     * the batch size, so the numbers follow the source machine instead of a hard-coded table.</p>
     *
     * <p>Recipes whose items do not exist are skipped without noise, which is also how the "only when
     * that mod is installed" behaviour of the source mod's conditional recipes is reproduced.</p>
     *
     * @return seed item -> {@code {energyPerTick, ticks, amount}}
     */
    private static Map<Item, int[]> readExternalRecipes(ResourceManager resourceManager) {
        List<String> configuredTypes = CrystalMultiplierConfig.compatRecipeTypes();
        if (configuredTypes.isEmpty()) {
            return Map.of();
        }
        Set<String> wantedTypes = new LinkedHashSet<>();
        Set<String> folders = new LinkedHashSet<>();
        for (String raw : configuredTypes) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                LOGGER.warn("crystal_multiplier: ignoring compatRecipeTypes entry '{}', expected 'namespace:type'", raw);
                continue;
            }
            wantedTypes.add(id.toString());
            // Recipes of a type live under data/<namespace>/recipes/<type path>/
            folders.add("recipes/" + id.getPath());
        }
        if (folders.isEmpty()) {
            return Map.of();
        }
        List<String> skipWords = CrystalMultiplierConfig.compatSkipKeywords();
        int defaultTicks = Math.max(1, CrystalMultiplierConfig.compatTicksPerCycle());
        Map<Item, int[]> result = new LinkedHashMap<>();
        for (String folder : folders) {
            Map<ResourceLocation, ?> files = resourceManager.listResources(folder,
                    path -> path.getPath().endsWith(".json"));
            for (ResourceLocation file : files.keySet()) {
                if (matchesAny(file.getPath(), skipWords)) {
                    continue;
                }
                try (var reader = resourceManager.getResource(file).orElseThrow().openAsReader()) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!root.has("type") || !isMirroredType(root.get("type").getAsString(), wantedTypes)) {
                        continue;
                    }
                    Item item = outputItem(root);
                    if (item == null || result.containsKey(item)) {
                        continue;
                    }
                    int ticks = ownTicks(root, defaultTicks);
                    result.put(item, new int[]{Math.max(1, ownEnergy(root) / ticks), ticks, outputCount(root)});
                } catch (RuntimeException | java.io.IOException e) {
                    LOGGER.debug("crystal_multiplier: skipping unreadable external recipe {}", file, e);
                }
            }
        }
        if (!result.isEmpty()) {
            LOGGER.info("crystal_multiplier: mirrored {} external crystal recipe(s) from {}", result.size(), wantedTypes);
        }
        return result;
    }

    /** True when the recipe type is one of the configured ones (a fork keeping the same path is fine). */
    private static boolean isMirroredType(String type, Set<String> wantedTypes) {
        if (wantedTypes.contains(type)) {
            return true;
        }
        int colon = type.indexOf(':');
        String typePath = colon < 0 ? type : type.substring(colon + 1);
        for (String wanted : wantedTypes) {
            int wantedColon = wanted.indexOf(':');
            if (wanted.substring(wantedColon + 1).equals(typePath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(String path, List<String> words) {
        for (String word : words) {
            if (!word.isEmpty() && path.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Output item of an external recipe ({@code {id,count}} or {@code {item,count}}). */
    @Nullable
    private static Item outputItem(JsonObject root) {
        if (!root.has("output") || !root.get("output").isJsonObject()) {
            return null;
        }
        JsonObject output = root.getAsJsonObject("output");
        String key = output.has("id") ? "id" : output.has("item") ? "item" : null;
        if (key == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(output.get(key).getAsString());
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    private static int outputCount(JsonObject root) {
        JsonObject output = root.getAsJsonObject("output");
        return output != null && output.has("count") ? Math.max(1, output.get("count").getAsInt()) : 1;
    }

    /** The source recipe's own processing time in ticks, when it states one. */
    private static int ownTicks(JsonObject root, int fallback) {
        for (String key : new String[]{"time", "duration", "processingTime"}) {
            if (root.has(key) && root.get(key).isJsonPrimitive() && root.get(key).getAsJsonPrimitive().isNumber()) {
                return Math.max(1, root.get(key).getAsInt());
            }
        }
        return fallback;
    }

    private static int ownEnergy(JsonObject root) {
        for (String key : new String[]{"energyPerCycle", "energy", "totalEnergy"}) {
            if (root.has(key) && root.get(key).isJsonPrimitive() && root.get(key).getAsJsonPrimitive().isNumber()) {
                int value = root.get(key).getAsInt();
                if (value > 0) {
                    return value;
                }
            }
        }
        return 100_000;
    }

    /** 工具类物品（镐斧锹锄剑/剪刀之类）不算水晶。 */
    private static boolean isToolLike(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.HOES) || stack.is(ItemTags.SWORDS) || stack.is(C_TOOLS);
    }
}
