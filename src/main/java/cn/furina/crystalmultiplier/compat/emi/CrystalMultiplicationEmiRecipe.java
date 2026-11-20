package cn.furina.crystalmultiplier.compat.emi;

import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.furina.crystalmultiplier.content.CrystalMultiplicationRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

/**
 * One EMI page for one {@link CrystalMultiplicationRecipe}: seed on the left, result on the right,
 * energy per tick / duration / parallel lane size in between.
 */
public class CrystalMultiplicationEmiRecipe implements EmiRecipe {

    private static final int WIDTH = 166;
    private static final int HEIGHT = 60;
    private static final int TEXT_COLOR = 0xFF404040;

    private final EmiRecipeCategory category;
    private final CrystalMultiplicationRecipe recipe;
    private final EmiIngredient seed;
    private final EmiStack result;

    public CrystalMultiplicationEmiRecipe(EmiRecipeCategory category, CrystalMultiplicationRecipe recipe) {
        this.category = category;
        this.recipe = recipe;
        this.seed = EmiIngredient.of(recipe.getSeed());
        this.result = EmiStack.of(recipe.getResultItem(RegistryAccess.EMPTY));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return this.category;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return this.recipe.getId();
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(this.seed);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(this.result);
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.seed, 2, 18);
        widgets.addFillingArrow(26, 22, 22);
        widgets.addSlot(this.result, 52, 18);

        int perLane = this.recipe.getCrystalsPerParallel() > 0
                ? this.recipe.getCrystalsPerParallel()
                : CrystalMultiplierConfig.crystalsPerParallel();

        widgets.addText(Component.translatable("jei.crystal_multiplier.energy",
                this.recipe.getEnergyPerTick()), 80, 6, TEXT_COLOR, false);
        widgets.addText(Component.translatable("jei.crystal_multiplier.time",
                String.format(Locale.ROOT, "%.1f", this.recipe.getTicks() / 20.0D)), 80, 22, TEXT_COLOR, false);
        widgets.addText(Component.translatable("jei.crystal_multiplier.seed", perLane),
                80, 38, TEXT_COLOR, false);
    }

    /** The seed is never consumed, so this page is informational only and gets no recipe tree. */
    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public Recipe<?> getBackingRecipe() {
        return this.recipe;
    }
}
