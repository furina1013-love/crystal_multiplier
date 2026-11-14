package cn.furina.crystalmultiplier.content;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
