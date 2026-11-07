package cn.woxin.crystalmultiplier.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

import cn.woxin.crystalmultiplier.CrystalMultiplierMod;
import cn.woxin.crystalmultiplier.menu.CrystalMultiplierMenu;

/**
 * The machine logic. Recipes only need their seed to be present inside the seed slot:
 * the seed is never consumed and the machine only consumes Forge Energy.
 */
public class CrystalMultiplierBlockEntity extends BlockEntity implements MenuProvider {

    public static final TagKey<Item> UPGRADES_TAG = ItemTags.create(new ResourceLocation("crystal_multiplier", "upgrades"));

    private static final int DATA_ENERGY = 0;
    private static final int DATA_CAPACITY = 1;
    private static final int DATA_PROGRESS = 2;
    private static final int DATA_TICKS = 3;
    private static final int DATA_COST = 4;
    private static final int DATA_ACTIVE = 5;
    private static final int DATA_TIER = 6;
    private static final int DATA_COUNT = 7;

    private final CrystalTier tier;
    private final MachineInventory inventory;

    private int energy;
    private double progress;
    private int ticksRequired;
    private int energyPerTick;
    private boolean active;

    private final int[] syncedData = new int[DATA_COUNT];

    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (toReceive <= 0) {
                return 0;
            }
            int accepted = Math.min(CrystalMultiplierBlockEntity.this.getEnergyCapacity()
                    - CrystalMultiplierBlockEntity.this.energy, Math.min(toReceive,
                    CrystalMultiplierBlockEntity.this.getMaxReceive()));
            if (!simulate && accepted > 0) {
                CrystalMultiplierBlockEntity.this.energy += accepted;
                CrystalMultiplierBlockEntity.this.setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return CrystalMultiplierBlockEntity.this.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return CrystalMultiplierBlockEntity.this.getEnergyCapacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    private final IItemHandler externalInventory = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1 + CrystalMultiplierBlockEntity.this.tier.getOutputSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return CrystalMultiplierBlockEntity.this.inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0) {
                return stack;
            }
            return CrystalMultiplierBlockEntity.this.inventory.insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 0) {
                return ItemStack.EMPTY;
            }
            return CrystalMultiplierBlockEntity.this.inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return CrystalMultiplierBlockEntity.this.inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && CrystalMultiplierBlockEntity.this.inventory.isItemValid(0, stack);
        }
    };

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            if (CrystalMultiplierBlockEntity.this.level != null
                    && CrystalMultiplierBlockEntity.this.level.isClientSide) {
                return index >= 0 && index < DATA_COUNT ? CrystalMultiplierBlockEntity.this.syncedData[index] : 0;
            }
            return CrystalMultiplierBlockEntity.this.serverValue(index);
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < DATA_COUNT) {
                CrystalMultiplierBlockEntity.this.syncedData[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private int serverValue(int index) {
        return switch (index) {
            case DATA_ENERGY -> this.energy;
            case DATA_CAPACITY -> this.getEnergyCapacity();
            case DATA_PROGRESS -> this.ticksRequired <= 0 ? 0
                    : (int) Math.min(100.0D, Math.floor(this.progress * 100.0D / this.ticksRequired));
            case DATA_TICKS -> this.ticksRequired;
            case DATA_COST -> this.energyPerTick;
            case DATA_ACTIVE -> this.active ? 1 : 0;
            case DATA_TIER -> this.tier.ordinal();
            default -> 0;
        };
    }

    public CrystalMultiplierBlockEntity(BlockPos pos, BlockState state) {
        super(CrystalMultiplierMod.MULTIPLIER_BE.get(), pos, state);
        this.tier = state.getBlock() instanceof CrystalMultiplierBlock block ? block.getTier() : CrystalTier.BASIC;
        this.inventory = new MachineInventory(1 + this.tier.getOutputSlots() + this.tier.getUpgradeSlots());
    }

    public CrystalTier getTier() {
        return this.tier;
    }

    public MachineInventory getInventory() {
        return this.inventory;
    }

    public ContainerData getDataAccess() {
        return this.dataAccess;
    }

    public int getSeedSlot() {
        return 0;
    }

    public int getOutputStart() {
        return 1;
    }

    public int getOutputEnd() {
        return 1 + this.tier.getOutputSlots();
    }

    public int getUpgradeStart() {
        return this.getOutputEnd();
    }

    public int getUpgradeEnd() {
        return this.getUpgradeStart() + this.tier.getUpgradeSlots();
    }

    public int getEnergy() {
        return this.energy;
    }

    public int getEnergyCapacity() {
        return this.tier.getEnergyCapacity();
    }

    public int getMaxReceive() {
        return this.tier.getBaseTransfer();
    }

    public int getEnergyPerTick() {
        return this.energyPerTick;
    }

    public int getTicksRequired() {
        return this.ticksRequired;
    }

    public boolean isActive() {
        return this.active;
    }

    /* ------------------------------------------------------------------ upgrades */

    public static boolean isUpgradeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(UPGRADES_TAG)) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "mekanism".equals(key.getNamespace()) && key.getPath().startsWith("upgrade_");
    }

    public static int upgradeKind(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return 0;
        }
        if ("mekanism".equals(key.getNamespace())) {
            return switch (key.getPath()) {
                case "upgrade_speed" -> 1;
                case "upgrade_energy" -> 2;
                default -> 3;
            };
        }
        return 3;
    }

    public int countUpgrades(int kind) {
        int count = 0;
        for (int slot = this.getUpgradeStart(); slot < this.getUpgradeEnd(); slot++) {
            ItemStack stack = this.inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && upgradeKind(stack) == kind) {
                count++;
            }
        }
        return count;
    }

    /* --------------------------------------------------------------- processing */

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystalMultiplierBlockEntity entity) {
        entity.tick();
    }

    private void tick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        int speedUpgrades = this.countUpgrades(1);
        int energyUpgrades = this.countUpgrades(2);
        double speedFactor = 1.0D + 0.5D * speedUpgrades;
        double energyPenalty = 1.0D + 0.75D * speedUpgrades;
        double energyEfficiency = 1.0D + 0.5D * energyUpgrades;

        CrystalMultiplicationRecipe recipe = this.findRecipe();
        boolean working = false;

        if (recipe != null) {
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            if (this.canAccept(result)) {
                this.ticksRequired = Math.max(1,
                        (int) Math.round(recipe.getTicks() / (this.tier.getSpeedMultiplier() * speedFactor)));
                this.energyPerTick = Math.max(1, (int) Math.round(recipe.getEnergyPerTick()
                        * energyPenalty / energyEfficiency / this.tier.getSpeedMultiplier()));
                if (this.energy >= this.energyPerTick) {
                    this.energy -= this.energyPerTick;
                    this.progress += 1.0D;
                    working = true;
                    this.setChanged();
                    if (this.progress >= this.ticksRequired) {
                        this.progress = 0.0D;
                        this.pushResult(result);
                    }
                }
            } else {
                this.progress = 0.0D;
            }
        } else {
            this.progress = 0.0D;
            this.ticksRequired = 0;
            this.energyPerTick = 0;
        }

        if (this.active != working) {
            this.active = working;
            BlockState newState = this.getBlockState().setValue(CrystalMultiplierBlock.ACTIVE, working);
            this.level.setBlock(this.worldPosition, newState, Block.UPDATE_CLIENTS);
            this.setChanged();
        }
    }

    @Nullable
    public CrystalMultiplicationRecipe findRecipe() {
        if (this.level == null) {
            return null;
        }
        ItemStack seed = this.inventory.getStackInSlot(0);
        if (seed.isEmpty()) {
            return null;
        }
        for (CrystalMultiplicationRecipe recipe : this.level.getRecipeManager()
                .getAllRecipesFor(CrystalMultiplicationRecipe.TYPE)) {
            if (recipe.matchesSeed(seed)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canAccept(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        for (int slot = this.getOutputStart(); slot < this.getOutputEnd(); slot++) {
            ItemStack existing = this.inventory.getStackInSlot(slot);
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameTags(existing, result)
                    && existing.getCount() + result.getCount() <= Math.min(existing.getMaxStackSize(), 64)) {
                return true;
            }
        }
        return false;
    }

    private void pushResult(ItemStack result) {
        ItemStack remaining = result.copy();
        for (int slot = this.getOutputStart(); slot < this.getOutputEnd() && !remaining.isEmpty(); slot++) {
            ItemStack existing = this.inventory.getStackInSlot(slot);
            if (existing.isEmpty()) {
                this.inventory.setStackInSlot(slot, remaining.copy());
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameTags(existing, remaining)) {
                int space = Math.min(existing.getMaxStackSize(), 64) - existing.getCount();
                int moved = Math.min(space, remaining.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining.shrink(moved);
                    this.inventory.setStackInSlot(slot, existing);
                }
            }
        }
        this.setChanged();
    }

    /* ------------------------------------------------------------------- saving */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", this.inventory.serializeNBT());
        tag.putInt("energy", this.energy);
        tag.putDouble("progress", this.progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("inventory")) {
            this.inventory.deserializeNBT(tag.getCompound("inventory"));
        }
        this.energy = tag.getInt("energy");
        this.progress = tag.getDouble("progress");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.crystal_multiplier.crystal_multiplier");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CrystalMultiplierMenu(containerId, playerInventory, this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.energyCapability = LazyOptional.of(() -> this.energyStorage);
        this.itemCapability = LazyOptional.of(() -> this.externalInventory);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.energyCapability.invalidate();
        this.itemCapability.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return this.energyCapability.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return this.itemCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    public class MachineInventory extends ItemStackHandler {

        public MachineInventory(int size) {
            super(size);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == CrystalMultiplierBlockEntity.this.getSeedSlot()) {
                return true;
            }
            if (slot >= CrystalMultiplierBlockEntity.this.getUpgradeStart()) {
                return isUpgradeItem(stack);
            }
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == CrystalMultiplierBlockEntity.this.getSeedSlot()) {
                return 1;
            }
            return 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            CrystalMultiplierBlockEntity.this.setChanged();
        }
    }
}
