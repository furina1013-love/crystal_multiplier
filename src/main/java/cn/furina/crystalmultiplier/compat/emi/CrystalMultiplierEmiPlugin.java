package cn.furina.crystalmultiplier.compat.emi;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.content.CrystalMultiplicationRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * EMI entry point.
 *
 * <p>EMI is an optional client side dependency: this class is never touched by the mod itself.
 * EMI discovers it through the {@link EmiEntrypoint} annotation while scanning the mod file
 * (Forge {@code ModFileScanData} annotations, see {@code EmiAgnosForge}), so the class is only
 * ever loaded - and its EMI types only ever resolved - when EMI is actually installed. When EMI
 * is absent nothing references this class and the mod loads normally.</p>
 */
@EmiEntrypoint
public class CrystalMultiplierEmiPlugin implements EmiPlugin {

    public static final ResourceLocation CATEGORY_ID =
            new ResourceLocation("crystal_multiplier", "crystal_multiplication");

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory category = new EmiRecipeCategory(CATEGORY_ID, EmiStack.of(anyMachine()));
        registry.addCategory(category);

        // Every tier is a valid workstation for the category.
        for (var block : CrystalMultiplierMod.TIER_BLOCKS) {
            registry.addWorkstation(category, EmiStack.of(block.getItemStack()));
        }

        // Recipes are data driven, so read them from the live recipe manager at registration time.
        for (CrystalMultiplicationRecipe recipe
                : registry.getRecipeManager().getAllRecipesFor(CrystalMultiplicationRecipe.TYPE)) {
            registry.addRecipe(new CrystalMultiplicationEmiRecipe(category, recipe));
        }
    }

    private static ItemStack anyMachine() {
        return CrystalMultiplierMod.TIER_BLOCKS.isEmpty()
                ? ItemStack.EMPTY
                : CrystalMultiplierMod.TIER_BLOCKS.get(0).getItemStack();
    }
}
