package cn.furina.crystalmultiplier.compat.emi;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.content.CrystalMultiplicationRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * EMI entry point (EMI 1.1.x for NeoForge 1.21.1).
 *
 * <p>Discovery on NeoForge is done by EMI itself: it scans every mod file's
 * {@code ModFileScanData} for classes annotated with {@link EmiEntrypoint}, loads that class by name and
 * instantiates it (public no-arg constructor) when it implements {@link EmiPlugin}. Nothing in this mod
 * references this class, so without EMI installed it is never loaded and never linked &mdash; that is
 * exactly why EMI can stay a {@code compileOnly} dependency.</p>
 */
@EmiEntrypoint
public class CrystalMultiplierEmiPlugin implements EmiPlugin {

    /** Must match the vanilla recipe type id; EMI derives the category name key as
     *  {@code emi.category.crystal_multiplier.crystal_multiplication}. */
    public static final ResourceLocation CATEGORY_ID =
            ResourceLocation.fromNamespaceAndPath(CrystalMultiplierMod.MODID, "crystal_multiplication");

    private static final String[] MACHINE_PATHS = {
            "crystal_multiplier",
            "crystal_multiplier_advanced",
            "crystal_multiplier_elite",
            "crystal_multiplier_ultimate"
    };

    /** Resolves the tier machines by registry name instead of touching the main class' registration code. */
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

    @Override
    public void register(EmiRegistry registry) {
        List<ItemStack> machines = machineStacks();
        EmiStack icon = machines.isEmpty() ? EmiStack.EMPTY : EmiStack.of(machines.get(0));
        EmiRecipeCategory category = new EmiRecipeCategory(CATEGORY_ID, icon);
        registry.addCategory(category);

        for (ItemStack machine : machines) {
            registry.addWorkstation(category, EmiStack.of(machine));
        }

        for (RecipeHolder<CrystalMultiplicationRecipe> holder
                : registry.getRecipeManager().getAllRecipesFor(CrystalMultiplicationRecipe.TYPE)) {
            registry.addRecipe(new CrystalMultiplicationEmiRecipe(category, holder.id(), holder.value()));
        }
    }
}
