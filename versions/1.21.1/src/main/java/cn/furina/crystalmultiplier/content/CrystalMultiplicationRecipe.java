package cn.furina.crystalmultiplier.content;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * A data driven crystal multiplication recipe.
 * The seed ingredient is only required to be present, it is never consumed.
 *
 * <p>1.21.1 notes: the recipe id no longer lives on the recipe itself (it is carried by
 * {@link net.minecraft.world.item.crafting.RecipeHolder}), so there is no {@code id} field and no
 * {@code getId()} override any more; {@code RegistryAccess} parameters became
 * {@code HolderLookup.Provider}, and {@code Container} as the recipe input type was replaced by
 * {@link SingleRecipeInput}.</p>
 */
public class CrystalMultiplicationRecipe implements Recipe<SingleRecipeInput> {

    /** Registered through the mod's recipe type deferred register (see {@code CrystalMultiplier}). */
    public static final RecipeType<CrystalMultiplicationRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return CrystalMultiplierMod.MODID + ":crystal_multiplication";
        }
    };

    /**
     * The one and only serializer instance. The mod <b>must</b> register exactly this object
     * ({@code RECIPE_SERIALIZERS.register("crystal_multiplication", () -> CrystalMultiplicationRecipe.SERIALIZER)}):
     * {@link #getSerializer()} returns it, and the 1.21.1 network dispatch looks the recipe serializer up
     * by identity in {@code BuiltInRegistries.RECIPE_SERIALIZER}, so a second instance would blow up on sync.
     * The constructor is private on purpose, so {@code Serializer::new} fails at compile time.
     */
    public static final CrystalMultiplicationRecipe.Serializer SERIALIZER = new Serializer();

    private static final int DEFAULT_ENERGY_PER_TICK = 200;
    private static final int DEFAULT_TICKS = 200;

    private final Ingredient seed;
    private final ItemStack result;
    private final int energyPerTick;
    private final int ticks;
    private final int crystalsPerParallel;

    public CrystalMultiplicationRecipe(Ingredient seed, ItemStack result, int energyPerTick, int ticks) {
        this(seed, result, energyPerTick, ticks, 0);
    }

    public CrystalMultiplicationRecipe(Ingredient seed, ItemStack result, int energyPerTick, int ticks,
                                       int crystalsPerParallel) {
        this.seed = seed;
        this.result = result;
        this.energyPerTick = Math.max(1, energyPerTick);
        this.ticks = Math.max(1, ticks);
        this.crystalsPerParallel = Math.max(0, crystalsPerParallel);
    }

    /**
     * How many crystals of the seed kind form one parallel lane.
     * 0 means "use the global config value".
     */
    public int getCrystalsPerParallel() {
        return this.crystalsPerParallel;
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
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.matchesSeed(input.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result.copy();
    }

    /** Convenience overload for callers that have no registry lookup at hand (JEI, tooltips). */
    public ItemStack getResultItem() {
        return this.result.copy();
    }

    /** Raw, non copied result stack: only used as a codec getter, never hand it out. */
    private ItemStack resultStack() {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(this.seed);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /**
     * 1.21.1 serializer: a {@link MapCodec} for json/data packs plus a
     * {@link StreamCodec} over {@link RegistryFriendlyByteBuf} for the network.
     */
    public static final class Serializer implements RecipeSerializer<CrystalMultiplicationRecipe> {

        private Serializer() {
        }

        private static final MapCodec<CrystalMultiplicationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC.fieldOf("seed").forGetter(CrystalMultiplicationRecipe::getSeed),
                        ItemStack.CODEC.fieldOf("result").forGetter(CrystalMultiplicationRecipe::resultStack),
                        Codec.INT.optionalFieldOf("energy_per_tick", DEFAULT_ENERGY_PER_TICK)
                                .forGetter(CrystalMultiplicationRecipe::getEnergyPerTick),
                        Codec.INT.optionalFieldOf("ticks", DEFAULT_TICKS)
                                .forGetter(CrystalMultiplicationRecipe::getTicks),
                        Codec.INT.optionalFieldOf("crystals_per_parallel", 0)
                                .forGetter(CrystalMultiplicationRecipe::getCrystalsPerParallel))
                .apply(instance, CrystalMultiplicationRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, CrystalMultiplicationRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CrystalMultiplicationRecipe::getSeed,
                        ItemStack.STREAM_CODEC, CrystalMultiplicationRecipe::resultStack,
                        ByteBufCodecs.VAR_INT, (CrystalMultiplicationRecipe recipe) -> recipe.energyPerTick,
                        ByteBufCodecs.VAR_INT, (CrystalMultiplicationRecipe recipe) -> recipe.ticks,
                        ByteBufCodecs.VAR_INT, (CrystalMultiplicationRecipe recipe) -> recipe.crystalsPerParallel,
                        CrystalMultiplicationRecipe::new);

        @Override
        public MapCodec<CrystalMultiplicationRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CrystalMultiplicationRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
