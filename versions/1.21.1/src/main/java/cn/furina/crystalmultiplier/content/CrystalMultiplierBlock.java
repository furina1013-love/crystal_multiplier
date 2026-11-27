package cn.furina.crystalmultiplier.content;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Upgrade;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tile.component.TileComponentUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The four crystal multiplier tiers share one block class; only the {@link CrystalTier} and the
 * block type (energy buffer, GUI, upgrades, side configuration) differ.
 * <p>
 * 1.21.1 port notes: Mekanism 10.7 renamed {@code ItemRegistryObject#getItemStack(int)} to
 * {@code asStack(int)}; the entity tick helpers themselves ({@code TileEntityMekanism::tickServer} /
 * {@code tickClient}, used by the block entity type registration) are unchanged.
 */
public class CrystalMultiplierBlock extends BlockTile<CrystalMultiplierBlockEntity, BlockTypeTile<CrystalMultiplierBlockEntity>> {

    private final CrystalTier tier;

    public CrystalMultiplierBlock(BlockTypeTile<CrystalMultiplierBlockEntity> type, CrystalTier tier) {
        super(type, properties -> properties
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
        this.tier = tier;
    }

    public CrystalTier getTier() {
        return this.tier;
    }

    // TODO(1.21.1): Block#playerWillDestroy(Level, BlockPos, BlockState, Player) and the static
    //  Block#popResource(Level, BlockPos, ItemStack) could not be checked against the 1.21.1 bytecode
    //  offline (neither Mek/AE2/JEI calls them, so there was nothing to compare with). Both are long
    //  standing vanilla Block signatures - verify on the first compile.
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof CrystalMultiplierBlockEntity machine) {
                // Drop everything that is stored in the machine slots, including the hidden overflow.
                for (CrystalSlot slot : machine.getMachineSlots()) {
                    ItemStack prototype = slot.getStack();
                    while (!prototype.isEmpty() && slot.getStoredCount() > 0) {
                        int maxStack = Math.max(1, prototype.getMaxStackSize());
                        int amount = Math.min(slot.getStoredCount(), maxStack);
                        ItemStack extracted = slot.extractItem(amount, Action.EXECUTE, AutomationType.MANUAL);
                        if (extracted.isEmpty()) {
                            break;
                        }
                        popResource(level, pos, extracted);
                    }
                }
                // Installed Mekanism upgrades are kept in a virtual slot, so they have to be dropped by hand.
                TileComponentUpgrade upgradeComponent = machine.getComponent();
                if (upgradeComponent != null) {
                    dropUpgrades(level, pos, upgradeComponent);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void dropUpgrades(Level level, BlockPos pos, TileComponentUpgrade component) {
        for (Upgrade type : new Upgrade[]{Upgrade.SPEED, Upgrade.ENERGY}) {
            int count = component.getUpgrades(type);
            while (count > 0) {
                int amount = Math.min(count, Math.max(1, upgradeStack(type, 1).getMaxStackSize()));
                ItemStack stack = upgradeStack(type, amount);
                if (stack.isEmpty()) {
                    break;
                }
                popResource(level, pos, stack);
                count -= amount;
            }
        }
        // Cards that are still waiting in the upgrade input slot are not counted as installed yet.
        for (ItemStack stack : new ItemStack[]{component.getUpgradeSlot().getStack(), component.getUpgradeOutputSlot().getStack()}) {
            if (!stack.isEmpty()) {
                popResource(level, pos, stack.copy());
            }
        }
        component.getUpgradeSlot().setEmpty();
        component.getUpgradeOutputSlot().setEmpty();
    }

    private static ItemStack upgradeStack(Upgrade type, int count) {
        return switch (type) {
            // Mekanism 10.7: ItemRegistryObject#getItemStack(int) was renamed to asStack(int).
            case SPEED -> MekanismItems.SPEED_UPGRADE.asStack(count);
            case ENERGY -> MekanismItems.ENERGY_UPGRADE.asStack(count);
            default -> ItemStack.EMPTY;
        };
    }
}
