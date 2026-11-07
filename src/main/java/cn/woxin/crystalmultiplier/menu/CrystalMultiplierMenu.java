package cn.woxin.crystalmultiplier.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import cn.woxin.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.woxin.crystalmultiplier.content.CrystalTier;

public class CrystalMultiplierMenu extends AbstractContainerMenu {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 198;

    // Layout shared with the baked background texture (textures/gui/machine.png) and the slot overlay
    // drawn by the screen: energy gauge on the left, seed -> arrow -> output grid, upgrades in the
    // middle band, player inventory at the bottom.
    private static final int SEED_X = 28;
    private static final int SEED_Y = 24;
    private static final int OUTPUT_BASE_X = 84;
    private static final int OUTPUT_BASE_Y = 18;
    private static final int OUTPUT_COLUMNS = 4;
    private static final int UPGRADE_BASE_X = 26;
    private static final int UPGRADE_BASE_Y = 76;
    private static final int UPGRADE_COLUMNS = 5;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 118;
    private static final int HOTBAR_Y = 176;

    private final CrystalMultiplierBlockEntity blockEntity;
    private final ContainerData data;
    private final CrystalTier tier;

    public CrystalMultiplierMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, restoreBlockEntity(playerInventory, data));
    }

    /**
     * The server side menu provider writes the machine position into the open screen packet so that the
     * client can look its block entity up again. Fail with a readable message instead of a blind cast.
     */
    private static CrystalMultiplierBlockEntity restoreBlockEntity(Inventory playerInventory, FriendlyByteBuf data) {
        if (data == null || !data.isReadable()) {
            throw new IllegalStateException("crystal_multiplier: the open screen packet carried no block position");
        }
        BlockPos pos = data.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof CrystalMultiplierBlockEntity multiplier)) {
            throw new IllegalStateException("crystal_multiplier: no machine block entity at " + pos);
        }
        return multiplier;
    }

    public CrystalMultiplierMenu(int containerId, Inventory playerInventory, CrystalMultiplierBlockEntity blockEntity) {
        super(cn.woxin.crystalmultiplier.CrystalMultiplierMod.MULTIPLIER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.tier = blockEntity.getTier();

        IItemHandler inventory = blockEntity.getInventory();
        this.addSlot(new SlotItemHandler(inventory, blockEntity.getSeedSlot(), SEED_X, SEED_Y) {
            @Override
            public int getMaxStackSize() {
                // Never more than one byte safe stack: everything above that lives in the hidden
                // overflow counter of the slot, which shift click and pipes fill for you.
                return Math.min(CrystalMultiplierBlockEntity.MAX_STACK_COUNT, blockEntity.getSeedCapacity());
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return Math.min(CrystalMultiplierBlockEntity.MAX_STACK_COUNT, blockEntity.getSeedCapacity());
            }
        });

        for (int index = 0; index < this.tier.getOutputSlots(); index++) {
            int column = index % OUTPUT_COLUMNS;
            int row = index / OUTPUT_COLUMNS;
            this.addSlot(new SlotItemHandler(inventory, blockEntity.getOutputStart() + index,
                    OUTPUT_BASE_X + column * 18, OUTPUT_BASE_Y + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public int getMaxStackSize() {
                    return Math.min(CrystalMultiplierBlockEntity.MAX_STACK_COUNT, blockEntity.getOutputStackLimit());
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    // Higher tiers pack more items into the same output slot; the part above the byte
                    // safe limit is kept in the overflow counter of the slot.
                    int limit = blockEntity.getOutputStackLimit();
                    return Math.max(1, Math.min(Math.min(CrystalMultiplierBlockEntity.MAX_STACK_COUNT, limit),
                            stack.getMaxStackSize() >= 64 ? limit : stack.getMaxStackSize()));
                }
            });
        }

        for (int index = 0; index < this.tier.getUpgradeSlots(); index++) {
            int column = index % UPGRADE_COLUMNS;
            int row = index / UPGRADE_COLUMNS;
            this.addSlot(new SlotItemHandler(inventory, blockEntity.getUpgradeStart() + index,
                    UPGRADE_BASE_X + column * 18, UPGRADE_BASE_Y + row * 18) {
                @Override
                public int getMaxStackSize() {
                    return CrystalMultiplierBlockEntity.MAX_UPGRADE_SLOT_STACK;
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    // Mekanism cards stack up to 8 in one slot, exactly like inside a Mekanism machine.
                    return Math.max(1, Math.min(CrystalMultiplierBlockEntity.MAX_UPGRADE_SLOT_STACK,
                            stack.getMaxStackSize()));
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * 18, HOTBAR_Y));
        }

        this.data = blockEntity.getDataAccess();
        this.addDataSlots(this.data);
    }

    public CrystalMultiplierBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public CrystalTier getTier() {
        return this.tier;
    }

    public int getProgressPercent() {
        return this.data.get(2);
    }

    public int getEnergy() {
        // Big numbers (energy, capacity, cost per tick, batch size) are synced through the block entity
        // update packet: a menu data slot is a short and would truncate them.
        return this.blockEntity.getEnergy();
    }

    public int getEnergyCapacity() {
        return this.blockEntity.getEnergyCapacity();
    }

    public int getTicksRequired() {
        return this.data.get(3);
    }

    public int getEnergyPerTick() {
        return this.blockEntity.getEnergyPerTick();
    }

    public boolean isActive() {
        return this.data.get(5) != 0;
    }

    public int getParallelism() {
        return this.data.get(7);
    }

    public boolean isSeedValid() {
        return this.data.get(8) != 0;
    }

    public int getSeedCapacity() {
        return this.blockEntity.getSeedCapacity();
    }

    public int getSpeedUpgrades() {
        return this.blockEntity.getSpeedUpgradeCount();
    }

    public int getEnergyUpgrades() {
        return this.blockEntity.getEnergyUpgradeCount();
    }

    public int getItemsPerBatch() {
        return this.blockEntity.getItemsPerBatch();
    }

    public int getCrystalsPerParallel() {
        int per = this.data.get(9);
        return per <= 0 ? 16 : per;
    }

    public int getMachineSlotCount() {
        return 1 + this.tier.getOutputSlots() + this.tier.getUpgradeSlots();
    }

    /** Real amount in a machine slot, including the part that does not fit into the visible stack. */
    public int getStoredCount(int slot) {
        return this.blockEntity.getInventory().getStored(slot);
    }

    /** Total capacity of a machine slot (visible stack plus overflow). */
    public int getSlotCapacity(int slot) {
        return this.blockEntity.getInventory().capacityOf(slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int machineSlots = this.getMachineSlotCount();
            if (index < machineSlots) {
                if (this.isOutputSlot(index)) {
                    // An output slot holds the visible stack plus a hidden overflow, so one shift click keeps
                    // handing the player byte safe stacks until the slot is empty or the inventory is full.
                    int before = this.blockEntity.getInventory().getStored(index);
                    this.drainOutputSlot(index);
                    int moved = before - this.blockEntity.getInventory().getStored(index);
                    return moved <= 0 ? ItemStack.EMPTY : result.copyWithCount(moved);
                }
                if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean moved = false;
                if (CrystalMultiplierBlockEntity.isUpgradeItem(stack)) {
                    moved = this.moveItemStackTo(stack, this.blockEntity.getUpgradeStart(),
                            this.blockEntity.getUpgradeEnd(), false);
                }
                if (!moved) {
                    // Route this through the handler: the visible stack alone can never hold the 128/256/512
                    // items of the higher tiers, the rest has to land in the overflow counter.
                    ItemStack rest = this.blockEntity.getInventory()
                            .insertItem(this.blockEntity.getSeedSlot(), stack, false);
                    if (rest.getCount() < stack.getCount()) {
                        moved = true;
                        stack.setCount(rest.getCount());
                    }
                }
                if (!moved && !this.moveItemStackTo(stack, machineSlots, this.slots.size(), false)) {
                    if (index < machineSlots + 27) {
                        if (!this.moveItemStackTo(stack, machineSlots + 27, this.slots.size(), false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(stack, machineSlots, machineSlots + 27, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    private boolean isOutputSlot(int menuIndex) {
        return menuIndex >= this.blockEntity.getOutputStart() && menuIndex < this.blockEntity.getOutputEnd();
    }

    /** Empties an output slot into the player inventory, one byte safe stack per pass. */
    private void drainOutputSlot(int menuIndex) {
        int machineSlots = this.getMachineSlotCount();
        for (int pass = 0; pass < 256; pass++) {
            ItemStack offered = this.blockEntity.getInventory()
                    .extractItem(menuIndex, CrystalMultiplierBlockEntity.MAX_STACK_COUNT, true);
            if (offered.isEmpty()) {
                return;
            }
            ItemStack moving = offered.copy();
            if (!this.moveItemStackTo(moving, machineSlots, this.slots.size(), true)) {
                return;
            }
            int moved = offered.getCount() - moving.getCount();
            if (moved <= 0) {
                return;
            }
            this.blockEntity.getInventory().extractItem(menuIndex, moved, false);
            if (!moving.isEmpty()) {
                return;
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity.getLevel() != null
                && this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) == this.blockEntity
                && player.distanceToSqr(this.blockEntity.getBlockPos().getX() + 0.5D,
                this.blockEntity.getBlockPos().getY() + 0.5D,
                this.blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
