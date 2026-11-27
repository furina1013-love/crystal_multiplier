package cn.furina.crystalmultiplier.compat.emi;

import cn.furina.crystalmultiplier.content.CrystalMultiplicationRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * EMI view of a single crystal multiplication recipe: seed on the left, result on the right, plus the
 * energy per tick, the duration and how many seeds make one parallel lane. The wording keys are the
 * ones JEI already uses ({@code jei.crystal_multiplier.*}) so both viewers show identical text and the
 * language files keep a single set of statements for the recipe itself.
 */
public class CrystalMultiplicationEmiRecipe extends BasicEmiRecipe {

    private static final int WIDTH = 156;
    private static final int HEIGHT = 54;
    private static final int TEXT_COLOR = 0xFF404040;

    private final CrystalMultiplicationRecipe recipe;

    public CrystalMultiplicationEmiRecipe(EmiRecipeCategory category, ResourceLocation id,
                                          CrystalMultiplicationRecipe recipe) {
        super(category, id, WIDTH, HEIGHT);
        this.recipe = recipe;
        this.inputs.add(EmiIngredient.of(recipe.getSeed()));
        this.outputs.add(EmiStack.of(recipe.getResultItem()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int perLane = this.recipe.getCrystalsPerParallel() > 0 ? this.recipe.getCrystalsPerParallel() : 16;

        SlotWidget seedSlot = widgets.addSlot(this.inputs.get(0), 8, 18);
        seedSlot.appendTooltip(Component.translatable("jei.crystal_multiplier.seed", perLane));

        // recipeContext() wires the output slot into EMI's recipe tree / "craftable" logic.
        widgets.addSlot(this.outputs.get(0), 132, 18).recipeContext(this);

        widgets.addText(Component.translatable("jei.crystal_multiplier.seed", perLane), 30, 6, TEXT_COLOR, false);
        widgets.addText(Component.translatable("jei.crystal_multiplier.energy", this.recipe.getEnergyPerTick()),
                30, 20, TEXT_COLOR, false);
        widgets.addText(Component.translatable("jei.crystal_multiplier.time",
                String.format(Locale.ROOT, "%.1f", this.recipe.getTicks() / 20.0D)), 30, 34, TEXT_COLOR, false);
    }
}
