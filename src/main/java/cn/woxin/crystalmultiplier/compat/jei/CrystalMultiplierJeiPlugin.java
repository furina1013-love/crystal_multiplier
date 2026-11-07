package cn.woxin.crystalmultiplier.compat.jei;

import cn.woxin.crystalmultiplier.CrystalMultiplierMod;
import cn.woxin.crystalmultiplier.client.CrystalMultiplierScreen;
import cn.woxin.crystalmultiplier.content.CrystalMultiplicationRecipe;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.RegistryObject;

/**
 * JEI entry point: discovered by JEI itself (bytecode scan of the {@link JeiPlugin} annotation), so this
 * class is only ever touched when JEI is installed and nothing here ever has to be loaded on a server.
 */
@JeiPlugin
public class CrystalMultiplierJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = new ResourceLocation("crystal_multiplier", "jei_plugin");

    private static ItemStack anyMachine() {
        List<RegistryObject<net.minecraft.world.item.Item>> items = CrystalMultiplierMod.TIER_ITEMS;
        return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get(0).get());
    }

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrystalMultiplicationRecipeCategory(
                registration.getJeiHelpers().getGuiHelper(), anyMachine()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<CrystalMultiplicationRecipe> recipes = new ArrayList<>();
        if (Minecraft.getInstance().level != null) {
            RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
            recipes.addAll(recipeManager.getAllRecipesFor(CrystalMultiplicationRecipe.TYPE));
        }
        registration.addRecipes(CrystalMultiplicationRecipeCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (RegistryObject<net.minecraft.world.item.Item> item : CrystalMultiplierMod.TIER_ITEMS) {
            registration.addRecipeCatalyst(new ItemStack(item.get()),
                    CrystalMultiplicationRecipeCategory.RECIPE_TYPE);
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // The progress arrow of the machine screen opens this category.
        registration.addRecipeClickArea(CrystalMultiplierScreen.class, 64, 33, 26, 16,
                CrystalMultiplicationRecipeCategory.RECIPE_TYPE);
    }
}
