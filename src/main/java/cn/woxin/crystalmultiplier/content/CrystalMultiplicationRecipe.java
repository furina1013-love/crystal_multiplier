package cn.woxin.crystalmultiplier.content;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * A data driven crystal multiplication recipe.
 * The seed ingredient is only required to be present, it is never consumed.
 */
public class CrystalMultiplicationRecipe implements Recipe<Container> {

    public static final RecipeType<CrystalMultiplicationRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return "crystal_multiplier:crystal_multiplication";
        }
    };

    private final ResourceLocation id;
    private final Ingredient seed;
    private final ItemStack result;
    private final int energyPerTick;
    private final int ticks;

    public CrystalMultiplicationRecipe(ResourceLocation id, Ingredient seed, ItemStack result,
                                       int energyPerTick, int ticks) {
        this.id = id;
        this.seed = seed;
        this.result = result;
        this.energyPerTick = Math.max(1, energyPerTick);
        this.ticks = Math.max(1, ticks);
    }

    public Ingredient getSeed() {
        return this.seed;
    }

    public int getEnergyPerTick() {
        return this.energyPerTick;
    }

    public int getTicks() {
        return this.ticks;
    }

    public boolean matchesSeed(ItemStack stack) {
        return !stack.isEmpty() && this.seed.test(stack);
    }

    @Override
    public boolean matches(Container container, Level level) {
        return this.matchesSeed(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return this.result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(this.seed);
        return list;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return cn.woxin.crystalmultiplier.CrystalMultiplierMod.MULTIPLICATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<CrystalMultiplicationRecipe> {

        @Override
        public CrystalMultiplicationRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient seed = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "seed"));
            JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
            ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(resultJson, "item"));
            int count = GsonHelper.getAsInt(resultJson, "count", 1);
            ItemStack result = new ItemStack(ForgeRegistries.ITEMS.getValue(itemId), count);
            int energyPerTick = GsonHelper.getAsInt(json, "energy_per_tick", 200);
            int ticks = GsonHelper.getAsInt(json, "ticks", 200);
            return new CrystalMultiplicationRecipe(id, seed, result, energyPerTick, ticks);
        }

        @Nullable
        @Override
        public CrystalMultiplicationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient seed = Ingredient.fromNetwork(buffer);
            ItemStack result = buffer.readItem();
            int energyPerTick = buffer.readVarInt();
            int ticks = buffer.readVarInt();
            return new CrystalMultiplicationRecipe(id, seed, result, energyPerTick, ticks);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CrystalMultiplicationRecipe recipe) {
            recipe.seed.toNetwork(buffer);
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeVarInt(recipe.ticks);
        }
    }
}
