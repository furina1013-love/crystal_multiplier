package cn.woxin.crystalmultiplier;

import cn.woxin.crystalmultiplier.content.CrystalMultiplierBlock;
import cn.woxin.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.woxin.crystalmultiplier.content.CrystalMultiplicationRecipe;
import cn.woxin.crystalmultiplier.content.CrystalTier;
import cn.woxin.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.woxin.crystalmultiplier.menu.CrystalMultiplierMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

@Mod(CrystalMultiplierMod.MODID)
public class CrystalMultiplierMod {

    public static final String MODID = "crystal_multiplier";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final List<RegistryObject<Block>> TIER_BLOCKS = new ArrayList<>();
    public static final List<RegistryObject<Item>> TIER_ITEMS = new ArrayList<>();

    public static final RegistryObject<BlockEntityType<CrystalMultiplierBlockEntity>> MULTIPLIER_BE;
    public static final RegistryObject<MenuType<CrystalMultiplierMenu>> MULTIPLIER_MENU;
    public static final RegistryObject<RecipeSerializer<?>> MULTIPLICATION_SERIALIZER;
    public static final RegistryObject<CreativeModeTab> TAB;

    static {
        for (CrystalTier tier : CrystalTier.values()) {
            RegistryObject<Block> block = BLOCKS.register(tier.getRegistryName(), () -> new CrystalMultiplierBlock(
                    Block.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops(),
                    tier));
            TIER_BLOCKS.add(block);
            TIER_ITEMS.add(ITEMS.register(tier.getRegistryName(),
                    () -> new BlockItem(block.get(), new Item.Properties())));
        }

        MULTIPLIER_BE = BLOCK_ENTITIES.register("crystal_multiplier", () -> BlockEntityType.Builder
                .of(CrystalMultiplierBlockEntity::new,
                        TIER_BLOCKS.stream().map(RegistryObject::get).toArray(Block[]::new))
                .build(null));

        MULTIPLIER_MENU = MENUS.register("crystal_multiplier", () -> IForgeMenuType.create(CrystalMultiplierMenu::new));

        MULTIPLICATION_SERIALIZER = RECIPE_SERIALIZERS.register("crystal_multiplication",
                CrystalMultiplicationRecipe.Serializer::new);
        RECIPE_TYPES.register("crystal_multiplication", () -> CrystalMultiplicationRecipe.TYPE);

        TAB = TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.crystal_multiplier"))
                .icon(() -> new ItemStack(TIER_ITEMS.get(CrystalTier.values().length - 1).get()))
                .displayItems((params, output) -> TIER_ITEMS.forEach(item -> output.accept(item.get())))
                .build());
    }

    public CrystalMultiplierMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CrystalMultiplierConfig.SPEC,
                "crystal_multiplier-common.toml");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register(modBus);
    }
}
