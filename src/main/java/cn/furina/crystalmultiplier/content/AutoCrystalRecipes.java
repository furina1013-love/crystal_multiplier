package cn.furina.crystalmultiplier.content;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

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

    /** Same shape as the datapack recipes: 2 crystals out, 16 seeds per parallel lane. */
    /** Numbers used for crystals that are discovered by scanning the item registry. */
    private static final int DEFAULT_ENERGY_PER_TICK = 320;
    private static final int DEFAULT_TICKS = 280;

    /** Mods whose "crystal" items are ore processing intermediates, not AE2 style crystals. */
    private static final Set<String> BLACKLISTED_NAMESPACES = Set.of("minecraft", "mekanism",
            CrystalMultiplierMod.MODID);

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
            all.add(new CrystalMultiplicationRecipe(id, Ingredient.of(item), new ItemStack(item, RESULT_COUNT),
                    candidate.energyPerTick(), candidate.ticks(), CRYSTALS_PER_PARALLEL));
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
            String namespace = itemId.getNamespace();
            if (BLACKLISTED_NAMESPACES.contains(namespace) || !ModList.get().isLoaded(namespace)) {
                continue;
            }
            Item item = entry.getValue();
            if (!covered.add(item)) {
                continue;
            }
            ResourceLocation recipeId = new ResourceLocation(CrystalMultiplierMod.MODID,
                    "crystal_multiplication/auto_" + namespace + "_" + itemId.getPath());
            if (this.recipeManager.byKey(recipeId).isPresent()) {
                continue;
            }
            all.add(new CrystalMultiplicationRecipe(recipeId, Ingredient.of(item),
                    new ItemStack(item, RESULT_COUNT), DEFAULT_ENERGY_PER_TICK, DEFAULT_TICKS,
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
}
