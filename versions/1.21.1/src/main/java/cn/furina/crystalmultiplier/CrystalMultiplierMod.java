package cn.furina.crystalmultiplier;

import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import cn.furina.crystalmultiplier.content.AutoCrystalRecipes;
import cn.furina.crystalmultiplier.content.CrystalMultiplicationRecipe;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlock;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.content.CrystalTier;
import cn.furina.crystalmultiplier.menu.CrystalMultiplierMenu;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Upgrade;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point of the Crystal Multiplier - NeoForge 1.21.1 port of the 1.20.1/Forge 47 build.
 * <p>
 * Registration summary (all on the mod bus):
 * <ul>
 *   <li>four Mekanism-style blocks + their block entity types (Mek's own deferred registers,
 *       so the block item, the tooltip item and the block entity type stay in sync),</li>
 *   <li>one menu type (Mek's container register),</li>
 *   <li>the {@code crystal_multiplication} recipe type and serializer,</li>
 *   <li>a creative tab,</li>
 *   <li>the common config spec and the recipe-manager reload listener.</li>
 * </ul>
 * 1.21.1 differences against the 1.20.1 source: {@code net.neoforged.*} packages, recipe
 * type/serializer keys from {@link Registries}, energy config is a {@code LongSupplier},
 * upgrade support uses the varargs overload, block entity types go through
 * {@code mekBuilder(...).serverTicker(...).clientTicker(...).build()}, and configs are
 * registered on the {@link ModContainer}. The two 1.20.1 mixins (ItemStack / FriendlyByteBuf)
 * are intentionally NOT ported.
 */
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
            DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<RecipeSerializer<?>, ?> MULTIPLICATION_SERIALIZER;
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB;

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
                        // Required for comparator output: BlockMekanism#hasAnalogOutputSignal only asks
                        // the tile when the block carries Attributes.COMPARATOR.
                        Attributes.COMPARATOR,
                        new AttributeStateFacing(),
                        new AttributeTier<>(tier),
                        new AttributeUpgradeable(() -> TIER_BLOCKS.get(slot + 1))
                };
            } else {
                attributes = new Attribute[]{
                        Attributes.INVENTORY,
                        Attributes.ACTIVE,
                        Attributes.REDSTONE,
                        Attributes.COMPARATOR,
                        new AttributeStateFacing(),
                        new AttributeTier<>(tier)
                };
            }
            // 1.21.1: the block description is a Mekanism ILangEntry (functional: getTranslationKey()),
            // energy config takes LongSuppliers, and supported upgrades are a varargs list.
            String descriptionKey = tier.getDescriptionKey();
            BlockTypeTile<CrystalMultiplierBlockEntity> type = BlockTypeTile.BlockTileBuilder
                    .<CrystalMultiplierBlockEntity>createBlock(() -> TIER_TILES.get(slot), () -> descriptionKey)
                    .withGui(() -> MULTIPLIER_CONTAINER)
                    .with(attributes)
                    .withEnergyConfig(
                            () -> (long) tier.getBaseTransfer(),
                            () -> (long) tier.getEnergyCapacity())
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY)
                    .build();
            BlockRegistryObject<CrystalMultiplierBlock, BlockItem> block =
                    BLOCKS.register(tier.getRegistryName(), () -> new CrystalMultiplierBlock(type, tier));
            TIER_BLOCKS.add(block);
            // BlockEntityType.BlockEntitySupplier is (BlockPos, BlockState) -> BE, and that constructor exists
            //  on the block entity (the 1.20.1 IBlockProvider variant became a Holder<Block>).
            TIER_TILES.add(BLOCK_ENTITIES.mekBuilder(block, CrystalMultiplierBlockEntity::new)
                    .serverTicker(TileEntityMekanism::tickServer)
                    .clientTicker(TileEntityMekanism::tickClient)
                    .build());
        }
        MULTIPLIER_CONTAINER = CONTAINERS.register("crystal_multiplier", CrystalMultiplierBlockEntity.class,
                (id, inventory, tile) -> new CrystalMultiplierMenu(id, inventory, tile));

        // Recipes keep a single serializer instance: the 1.21.1 sync path looks the serializer up by
        // identity in BuiltInRegistries.RECIPE_SERIALIZER, so the one the recipes report must be the
        // registered one (the recipe class owns SERIALIZER with a private constructor).
        MULTIPLICATION_SERIALIZER = RECIPE_SERIALIZERS.register("crystal_multiplication",
                () -> CrystalMultiplicationRecipe.SERIALIZER);
        RECIPE_TYPES.register("crystal_multiplication", () -> CrystalMultiplicationRecipe.TYPE);

        TAB = TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.crystal_multiplier"))
                .icon(() -> new ItemStack(TIER_BLOCKS.get(tiers.length - 1).asItem()))
                // CreativeModeTab.Output#accept(ItemLike) - BlockRegistryObject is ItemLike.
                .displayItems((params, output) -> {
                    for (BlockRegistryObject<CrystalMultiplierBlock, BlockItem> block : TIER_BLOCKS) {
                        output.accept(block);
                    }
                })
                .build());
    }

    public CrystalMultiplierMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CrystalMultiplierConfig.SPEC);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CONTAINERS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register(modBus);
        // Addon crystals (lattra / redstone) have no datapack json: the recipe is generated at
        // reload time whenever the addon mod and its item are actually present.
        NeoForge.EVENT_BUS.addListener(CrystalMultiplierMod::onAddReloadListeners);
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        // TODO(1.21.1): ReloadableServerResources#getRecipeManager() could not be verified against the
        //  1.21.1 bytecode offline (the recipe manager got refactored only in 1.21.2, so this should be
        //  fine) - check on the first compile. AutoCrystalRecipes' constructor signature comes from the
        //  class port itself.
        event.addListener(new AutoCrystalRecipes(event.getServerResources().getRecipeManager()));
    }
}
