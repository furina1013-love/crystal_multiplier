package cn.furina.crystalmultiplier;

import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.furina.crystalmultiplier.content.AutoCrystalRecipes;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlock;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.content.CrystalMultiplicationRecipe;
import cn.furina.crystalmultiplier.content.CrystalTier;
import cn.furina.crystalmultiplier.menu.CrystalMultiplierMenu;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import mekanism.api.Upgrade;
import mekanism.api.math.FloatingLong;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeStateFacing;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(CrystalMultiplierMod.MODID)
public class CrystalMultiplierMod {

    public static final String MODID = "crystal_multiplier";

    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MODID);
    public static final TileEntityTypeDeferredRegister BLOCK_ENTITIES = new TileEntityTypeDeferredRegister(MODID);
    public static final ContainerTypeDeferredRegister CONTAINERS = new ContainerTypeDeferredRegister(MODID);

    public static final List<BlockRegistryObject<CrystalMultiplierBlock, BlockItem>> TIER_BLOCKS = new ArrayList<>();
    public static final List<TileEntityTypeRegistryObject<CrystalMultiplierBlockEntity>> TIER_TILES = new ArrayList<>();
    /** Has to stay non final: the menu factory needs the registry object that is created with it. */
    public static ContainerTypeRegistryObject<CrystalMultiplierMenu> MULTIPLIER_CONTAINER;

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<RecipeSerializer<?>> MULTIPLICATION_SERIALIZER;
    public static final RegistryObject<CreativeModeTab> TAB;

    static {
        CrystalTier[] tiers = CrystalTier.values();
        for (int index = 0; index < tiers.length; index++) {
            CrystalTier tier = tiers[index];
            int slot = index;
            Attribute[] attributes;
            if (slot + 1 < tiers.length) {
                attributes = new Attribute[]{
                        Attributes.INVENTORY,
                        Attributes.ACTIVE,
                        Attributes.REDSTONE,
                        new AttributeStateFacing(),
                        new AttributeTier<>(tier),
                        new AttributeUpgradeable(() -> TIER_BLOCKS.get(slot + 1))
                };
            } else {
                attributes = new Attribute[]{
                        Attributes.INVENTORY,
                        Attributes.ACTIVE,
                        Attributes.REDSTONE,
                        new AttributeStateFacing(),
                        new AttributeTier<>(tier)
                };
            }
            BlockTypeTile<CrystalMultiplierBlockEntity> type = BlockTypeTile.BlockTileBuilder
                    .<CrystalMultiplierBlockEntity>createBlock(() -> TIER_TILES.get(slot),
                            () -> "block.crystal_multiplier." + tier.getRegistryName())
                    .withGui(() -> MULTIPLIER_CONTAINER)
                    .with(attributes)
                    .withEnergyConfig(
                            () -> FloatingLong.create(tier.getBaseTransfer()),
                            () -> FloatingLong.create(tier.getEnergyCapacity()))
                    .withSupportedUpgrades(EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .build();
            BlockRegistryObject<CrystalMultiplierBlock, BlockItem> block =
                    BLOCKS.register(tier.getRegistryName(), () -> new CrystalMultiplierBlock(type, tier));
            TIER_BLOCKS.add(block);
            TIER_TILES.add(BLOCK_ENTITIES.register(block, CrystalMultiplierBlockEntity::new,
                    TileEntityMekanism::tickServer, TileEntityMekanism::tickClient));
        }
        MULTIPLIER_CONTAINER = CONTAINERS.register("crystal_multiplier", CrystalMultiplierBlockEntity.class,
                (id, inventory, tile) -> new CrystalMultiplierMenu(id, inventory, tile));

        MULTIPLICATION_SERIALIZER = RECIPE_SERIALIZERS.register("crystal_multiplication",
                CrystalMultiplicationRecipe.Serializer::new);
        RECIPE_TYPES.register("crystal_multiplication", () -> CrystalMultiplicationRecipe.TYPE);

        TAB = TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.crystal_multiplier"))
                .icon(() -> TIER_BLOCKS.get(tiers.length - 1).getItemStack())
                .displayItems((params, output) -> TIER_BLOCKS.forEach(block -> output.accept(block.getItemStack())))
                .build());
    }

    public CrystalMultiplierMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CrystalMultiplierConfig.SPEC,
                "crystal_multiplier-common.toml");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CONTAINERS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register(modBus);
        // Addon crystals (lattra / redstone) have no datapack json: the recipe is generated at
        // reload time whenever the addon mod and its item are actually present.
        MinecraftForge.EVENT_BUS.addListener(CrystalMultiplierMod::onAddReloadListeners);
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AutoCrystalRecipes(event.getServerResources().getRecipeManager()));
    }
}
