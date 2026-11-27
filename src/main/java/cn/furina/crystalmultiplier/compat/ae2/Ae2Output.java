package cn.furina.crystalmultiplier.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.content.CrystalSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Pushes the produced crystals straight into the ME network of the cable / interface that touches
 * the machine (the "产物回网" behaviour of Productive Bees Genesis, reduced to what this machine
 * needs).
 *
 * <p>The machine is not an AE2 grid node itself, so a cable next to it is still enough: every tick
 * the six neighbours are asked for their {@link IInWorldGridNodeHost} node, and the first one that
 * is booted on a grid with a storage service becomes the target. Nothing is cached on purpose - a
 * cached {@link MEStorage} would keep accepting items after the player cuts the cable, which would
 * silently void the output. When there is no network the method returns 0 and the normal Mekanism
 * ejector keeps handling the slots.</p>
 *
 * <p>Only loaded when AE2 is present; see {@link Ae2Compat}.</p>
 */
final class Ae2Output {

    private static final IActionSource SOURCE = IActionSource.empty();

    private Ae2Output() {
    }

    /**
     * Inserts {@code amount} of {@code stack} into an adjacent ME network right away, without the
     * item ever touching the output slots.
     *
     * @param simulate {@code true} to only ask the network whether it would take the items
     * @return how many items the network accepted (all of them when {@code simulate} is set)
     */
    static int insert(CrystalMultiplierBlockEntity machine, ItemStack stack, int amount, boolean simulate) {
        if (amount <= 0 || stack.isEmpty()) {
            return 0;
        }
        Level level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return 0;
        }
        MEStorage storage = findStorage(level, machine.getBlockPos());
        if (storage == null) {
            return 0;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0;
        }
        long accepted = storage.insert(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, SOURCE);
        return (int) Math.min(Math.max(accepted, 0L), Integer.MAX_VALUE);
    }

    static int drain(CrystalMultiplierBlockEntity machine) {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return 0;
        }
        boolean hasOutput = false;
        for (CrystalSlot slot : machine.getOutputSlots()) {
            if (slot.getStoredCount() > 0) {
                hasOutput = true;
                break;
            }
        }
        if (!hasOutput) {
            // Idle machine: do not even walk the six neighbours, this runs every tick.
            return 0;
        }
        MEStorage storage = findStorage(level, machine.getBlockPos());
        if (storage == null) {
            return 0;
        }
        int moved = 0;
        for (CrystalSlot slot : machine.getOutputSlots()) {
            int stored = slot.getStoredCount();
            if (stored <= 0) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            long accepted = storage.insert(key, stored, Actionable.MODULATE, SOURCE);
            if (accepted <= 0L) {
                continue;
            }
            moved += slot.shrinkStored((int) Math.min(accepted, Integer.MAX_VALUE));
        }
        return moved;
    }

    /** First neighbour that exposes a booted grid node with network storage, or {@code null}. */
    private static MEStorage findStorage(Level level, BlockPos origin) {
        for (Direction side : Direction.values()) {
            BlockPos neighbour = origin.relative(side);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            IInWorldGridNodeHost host = GridHelper.getNodeHost(level, neighbour);
            if (host == null) {
                continue;
            }
            IGridNode node = host.getGridNode(side.getOpposite());
            if (node == null) {
                // Cables and interfaces expose their node on the sides they actually connect to;
                // fall back to any side of the neighbour so a cable that only meets the machine
                // through a part is still found.
                for (Direction other : Direction.values()) {
                    node = host.getGridNode(other);
                    if (node != null) {
                        break;
                    }
                }
            }
            if (node == null || !node.hasGridBooted()) {
                continue;
            }
            IGrid grid = node.getGrid();
            if (grid == null) {
                continue;
            }
            IStorageService service = grid.getService(IStorageService.class);
            if (service == null) {
                continue;
            }
            MEStorage storage = service.getInventory();
            if (storage != null) {
                return storage;
            }
        }
        return null;
    }
}
