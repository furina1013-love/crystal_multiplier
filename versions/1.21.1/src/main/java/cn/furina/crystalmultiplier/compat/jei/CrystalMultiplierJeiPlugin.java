package cn.furina.crystalmultiplier.compat.jei;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.client.CrystalMultiplierScreen;
import cn.furina.crystalmultiplier.content.CrystalMultiplicationRecipe;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * JEI entry point (JEI 19.x for NeoForge 1.21.1): discovered by JEI itself (bytecode scan of the
 * {@link JeiPlugin} annotation), so this class is only ever touched when JEI is installed and
 * nothing here ever has to be loaded on a server.
 *
 * <p>Wiring change for 1.21.1: JEI must be a {@code compileOnly} (+ {@code runtimeOnly} for dev)
 * dependency in {@code build.gradle}; nothing has to be registered in the mod main class.</p>
 */
@JeiPlugin
public class CrystalMultiplierJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(CrystalMultiplierMod.MODID, "jei_plugin");

    /** Registry order of the four machine tiers, used for the catalysts and the category icon. */
    private static final String[] MACHINE_PATHS = {
            "crystal_multiplier",
            "crystal_multiplier_advanced",
            "crystal_multiplier_elite",
            "crystal_multiplier_ultimate"
    };

    /**
     * Resolves the tier machine items by registry name; deliberately not tied to the Java field names
     * of the main class so this client-only plugin stays decoupled from the registration code.
     */
    private static List<ItemStack> machineStacks() {
        List<ItemStack> stacks = new ArrayList<>(MACHINE_PATHS.length);
        for (String path : MACHINE_PATHS) {
            BuiltInRegistries.ITEM
                    .getOptional(ResourceLocation.fromNamespaceAndPath(CrystalMultiplierMod.MODID, path))
                    .filter(item -> item != Items.AIR)
                    .ifPresent(item -> stacks.add(new ItemStack(item)));
        }
        return stacks;
    }

    private static ItemStack anyMachine() {
        List<ItemStack> stacks = machineStacks();
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
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
            // 1.21.1: the recipe manager returns RecipeHolders, the category works on the values.
            Minecraft.getInstance().level.getRecipeManager()
                    .getAllRecipesFor(CrystalMultiplicationRecipe.TYPE)
                    .forEach(holder -> recipes.add(holder.value()));
        }
        registration.addRecipes(CrystalMultiplicationRecipeCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (ItemStack stack : machineStacks()) {
            registration.addRecipeCatalyst(stack, CrystalMultiplicationRecipeCategory.RECIPE_TYPE);
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // The progress arrow of the machine screen opens this category.
        // JEI 19 needs an AbstractContainerScreen subclass here (the Mekanism based screen is one).
        registration.addRecipeClickArea(CrystalMultiplierScreen.class, 52, 45, 32, 8,
                CrystalMultiplicationRecipeCategory.RECIPE_TYPE);
    }
}
