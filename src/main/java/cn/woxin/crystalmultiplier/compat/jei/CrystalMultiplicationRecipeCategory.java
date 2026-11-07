package cn.woxin.crystalmultiplier.compat.jei;

import cn.woxin.crystalmultiplier.content.CrystalMultiplicationRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category for the crystal multiplication recipes: one seed slot on the left, one result slot on
 * the right and the energy, time and lane information in between.
 */
public class CrystalMultiplicationRecipeCategory implements IRecipeCategory<CrystalMultiplicationRecipe> {

    public static final RecipeType<CrystalMultiplicationRecipe> RECIPE_TYPE =
            RecipeType.create("crystal_multiplier", "crystal_multiplication", CrystalMultiplicationRecipe.class);

    private static final int WIDTH = 156;
    private static final int HEIGHT = 54;
    private static final int TEXT = 0xFF404040;

    private final IDrawable background;
    private final IDrawable icon;

    public CrystalMultiplicationRecipeCategory(IGuiHelper guiHelper, ItemStack catalyst) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(catalyst);
    }

    @Override
    public RecipeType<CrystalMultiplicationRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.crystal_multiplier.category");
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CrystalMultiplicationRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 6).addIngredients(recipe.getSeed());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 128, 6)
                .addItemStack(recipe.getResultItem(RegistryAccess.EMPTY));
    }

    @Override
    public void draw(CrystalMultiplicationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        int per = recipe.getCrystalsPerParallel() > 0 ? recipe.getCrystalsPerParallel() : 16;
        graphics.drawString(font, Component.translatable("jei.crystal_multiplier.seed", per), 28, 10, TEXT, false);
        graphics.drawString(font, Component.translatable("jei.crystal_multiplier.energy", recipe.getEnergyPerTick()),
                28, 24, TEXT, false);
        graphics.drawString(font, Component.translatable("jei.crystal_multiplier.time",
                String.format(java.util.Locale.ROOT, "%.1f", recipe.getTicks() / 20.0D)), 28, 38, TEXT, false);
    }
}
