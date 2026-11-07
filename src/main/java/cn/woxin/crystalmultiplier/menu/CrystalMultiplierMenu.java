package cn.woxin.crystalmultiplier.menu;

import net.minecraft.network.FriendlyByteBuf;
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
    public static final int GUI_HEIGHT = 186;

    private static final int SEED_X = 44;
    private static final int SEED_Y = 35;
    private static final int OUTPUT_BASE_X = 106;
    private static final int OUTPUT_BASE_Y = 17;
    private static final int OUTPUT_COLUMNS = 4;
    private static final int UPGRADE_BASE_X = 26;
    private static final int UPGRADE_BASE_Y = 64;
    private static final int UPGRADE_COLUMNS = 5;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 104;
    private static final int HOTBAR_Y = 162;

    private final CrystalMultiplierBlockEntity blockEntity;
    private final ContainerData data;
    private final CrystalTier tier;

    public CrystalMultiplierMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, (CrystalMultiplierBlockEntity) playerInventory.player.level()
                .getBlockEntity(data.readBlockPos()));
    }

    public CrystalMultiplierMenu(int containerId, Inventory playerInventory, CrystalMultiplierBlockEntity blockEntity) {
        super(cn.woxin.crystalmultiplier.CrystalMultiplierMod.MULTIPLIER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.tier = blockEntity.getTier();

        IItemHandler inventory = blockEntity.getInventory();
        this.addSlot(new SlotItemHandler(inventory, blockEntity.getSeedSlot(), SEED_X, SEED_Y) {
            @Override
            public int getMaxStackSize() {
                return blockEntity.getSeedCapacity();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                // The seed slot ignores the vanilla 64 cap: upgrades can enlarge it, see the config.
                return blockEntity.getSeedCapacity();
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
            });
        }

        for (int index = 0; index < this.tier.getUpgradeSlots(); index++) {
            int column = index % UPGRADE_COLUMNS;
            int row = index / UPGRADE_COLUMNS;
            this.addSlot(new SlotItemHandler(inventory, blockEntity.getUpgradeStart() + index,
                    UPGRADE_BASE_X + column * 18, UPGRADE_BASE_Y + row * 18));
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
        return this.data.get(0);
    }

    public int getEnergyCapacity() {
        return this.data.get(1);
    }

    public int getTicksRequired() {
        return this.data.get(3);
    }

    public int getEnergyPerTick() {
        return this.data.get(4);
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

    public int getCrystalsPerParallel() {
        int per = this.data.get(9);
        return per <= 0 ? 16 : per;
    }

    public int getMachineSlotCount() {
        return 1 + this.tier.getOutputSlots() + this.tier.getUpgradeSlots();
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
                    moved = this.moveItemStackTo(stack, this.blockEntity.getSeedSlot(),
                            this.blockEntity.getSeedSlot() + 1, false);
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

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity.getLevel() != null
                && this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) == this.blockEntity
                && player.distanceToSqr(this.blockEntity.getBlockPos().getX() + 0.5D,
                this.blockEntity.getBlockPos().getY() + 0.5D,
                this.blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
