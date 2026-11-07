package cn.woxin.crystalmultiplier.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraftforge.network.PacketDistributor;
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

import mekanism.api.Upgrade;
import mekanism.common.item.interfaces.IUpgradeItem;

import cn.woxin.crystalmultiplier.CrystalMultiplierMod;
import cn.woxin.crystalmultiplier.config.CrystalMultiplierConfig;
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
    private static final int DATA_PARALLEL = 7;
    private static final int DATA_SEED_OK = 8;
    private static final int DATA_PER_PARALLEL = 9;
    private static final int DATA_BATCH = 10;
    private static final int DATA_COUNT = 11;

    /** No item count may exceed this: 1.20.1 stores the count in a signed byte, on disk and on the wire. */
    public static final int MAX_STACK_COUNT = 127;

    /**
     * Upper bound of one upgrade slot. Mekanism upgrade cards stack to 8; the real cap per kind comes
     * from {@link Upgrade#getMax()} and is enforced by the inventory validation, so this only has to
     * stay byte safe.
     */
    public static final int MAX_UPGRADE_SLOT_STACK = 64;

    private final CrystalTier tier;
    private final MachineInventory inventory;

    private int energy;
    private double progress;
    private int ticksRequired;
    private int energyPerTick;
    private boolean active;
    private int parallelism;
    private int perParallel = 16;
    private int itemsPerBatch;
    private boolean seedValid;
    private int syncCooldown;
    private int lastSyncedEnergy = -1;
    private int lastSyncedExtra = -1;

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
            case DATA_PARALLEL -> this.parallelism;
            case DATA_SEED_OK -> this.seedValid ? 1 : 0;
            case DATA_PER_PARALLEL -> this.perParallel;
            case DATA_BATCH -> this.itemsPerBatch;
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

    /** Currently running parallel lanes, driven purely by the seed stack size. */
    public int getParallelism() {
        return this.parallelism;
    }

    /** False when the seed stack size is not a multiple of the crystals per parallel lane. */
    public boolean isSeedValid() {
        return this.seedValid;
    }

    /** How many crystals of one kind make up a single parallel lane. */
    public int getCrystalsPerParallel(@Nullable CrystalMultiplicationRecipe recipe) {
        int fromRecipe = recipe == null ? 0 : recipe.getCrystalsPerParallel();
        int per = fromRecipe > 0 ? fromRecipe : CrystalMultiplierConfig.crystalsPerParallel();
        return Math.max(1, per);
    }

    /** How many items one finished batch produces right now (parallel lanes times the recipe result). */
    public int getItemsPerBatch() {
        return this.itemsPerBatch;
    }

    /** Seed slot capacity: base value plus the bonus of every inserted upgrade card. */
    public int getSeedCapacity() {
        int upgrades = 0;
        for (int slot = this.getUpgradeStart(); slot < this.getUpgradeEnd(); slot++) {
            if (!this.inventory.getStackInSlot(slot).isEmpty()) {
                upgrades++;
            }
        }
        int capacity = this.tier.getSeedStackLimit()
                + CrystalMultiplierConfig.seedCapacityPerUpgrade() * upgrades;
        return Math.max(1, Math.min(CrystalMultiplierConfig.seedSlotCapCeiling(), capacity));
    }

    /** How many items one output slot of this tier holds. */
    public int getOutputStackLimit() {
        return this.tier.getOutputStackLimit();
    }

    /* ------------------------------------------------------------------ upgrades */

    /**
     * Asks Mekanism itself what kind of upgrade a stack is: every upgrade item of Mekanism and of any
     * addon implements {@link IUpgradeItem}, so this works for cards this mod has never heard of.
     * The id based fallback only exists for stacks whose item is not loaded through that interface
     * (for example a datapack card listed in our own tag).
     */
    @Nullable
    public static Upgrade upgradeType(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof IUpgradeItem upgradeItem) {
            Upgrade type = upgradeItem.getUpgradeType(stack);
            if (type != null) {
                return type;
            }
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !"mekanism".equals(key.getNamespace())) {
            return null;
        }
        return switch (key.getPath()) {
            case "upgrade_speed" -> Upgrade.SPEED;
            case "upgrade_energy" -> Upgrade.ENERGY;
            case "upgrade_filter" -> Upgrade.FILTER;
            case "upgrade_gas" -> Upgrade.GAS;
            case "upgrade_muffling" -> Upgrade.MUFFLING;
            case "upgrade_anchor" -> Upgrade.ANCHOR;
            case "upgrade_stone_generator" -> Upgrade.STONE_GENERATOR;
            default -> null;
        };
    }

    public static boolean isUpgradeItem(ItemStack stack) {
        return upgradeType(stack) != null || (!stack.isEmpty() && stack.is(UPGRADES_TAG));
    }

    /** How many cards of this kind a machine accepts, straight from Mekanism (8 for speed and energy). */
    public static int maxUpgradeCount(ItemStack stack) {
        Upgrade type = upgradeType(stack);
        int max = type == null ? 8 : type.getMax();
        return Math.max(1, Math.min(64, max));
    }

    /** 1 = speed, 2 = energy, 3 = any other card that is accepted but does not change the maths. */
    public static int upgradeKind(ItemStack stack) {
        Upgrade type = upgradeType(stack);
        if (type == Upgrade.SPEED) {
            return 1;
        }
        if (type == Upgrade.ENERGY) {
            return 2;
        }
        return 3;
    }

    /** Number of inserted Mekanism speed upgrade cards (8 cards = ten times the speed by default). */
    public int getSpeedUpgradeCount() {
        return this.countUpgrades(1);
    }

    /** Number of inserted Mekanism energy upgrade cards (8 cards = a tenth of the FE per tick by default). */
    public int getEnergyUpgradeCount() {
        return this.countUpgrades(2);
    }

    public int countUpgrades(int kind) {
        int count = 0;
        for (int slot = this.getUpgradeStart(); slot < this.getUpgradeEnd(); slot++) {
            ItemStack stack = this.inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && upgradeKind(stack) == kind) {
                // Cards stack, so a stacked slot counts as several cards just like in Mekanism.
                count += stack.getCount() + this.inventory.getExtra(slot);
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
        // Mekanism's own upgrade maths (MekanismUtils#getTicks and #getEnergyPerTick in Mekanism 10):
        //   fraction      = installed / Upgrade#getMax()            (8 for both speed and energy)
        //   ticks         = base ticks  * multiplier ^ (-fractionSpeed)
        //   energyPerTick = base energy * multiplier ^ (2 * fractionSpeed - fractionEnergy)
        // multiplier is Mekanism's own config option "maxUpgradeMultiplier" (default 10). Eight speed
        // cards therefore make the machine ten times faster while drawing a hundred times the FE per
        // tick, and eight energy cards cut the FE per tick to a tenth - same behaviour as a Mekanism
        // machine, including the fact that speed is an overclock and not a free lunch.
        double upgradeMultiplier = Math.max(1.0D, CrystalMultiplierConfig.maxUpgradeMultiplier());
        double fractionSpeed = (double) speedUpgrades / (double) Upgrade.SPEED.getMax();
        double fractionEnergy = (double) energyUpgrades / (double) Upgrade.ENERGY.getMax();
        double speedFactor = Math.min(4096.0D, Math.pow(upgradeMultiplier, fractionSpeed));
        double energyUpgradeFactor = Math.min(1_048_576.0D,
                Math.pow(upgradeMultiplier, 2.0D * fractionSpeed - fractionEnergy));
        double baseSpeed = Math.max(0.01D, CrystalMultiplierConfig.baseSpeedMultiplier());
        double baseEnergy = Math.max(0.0001D, CrystalMultiplierConfig.baseEnergyMultiplier());

        CrystalMultiplicationRecipe recipe = this.findRecipe();
        ItemStack seed = this.inventory.getStackInSlot(0);
        // The slot may hold more items than a single ItemStack can carry, so always count the stored total.
        int seedCount = this.inventory.getStored(0);
        int perParallel = Math.max(1, this.getCrystalsPerParallel(recipe));

        // The seed stack is the only thing that decides what and how much the machine produces.
        // It is never consumed, it may only contain one crystal kind (a single slot cannot mix items)
        // and its size has to be a multiple of crystalsPerParallel, every multiple being one parallel lane.
        this.seedValid = !seed.isEmpty() && seedCount % perParallel == 0;
        int lanes = this.seedValid
                ? Math.min(CrystalMultiplierConfig.maxParallel(), seedCount / perParallel)
                : 0;
        this.parallelism = lanes;
        this.perParallel = perParallel;

        boolean working = false;

        if (recipe != null && lanes > 0) {
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            int amount = Math.max(1, result.getCount() * lanes);
            this.itemsPerBatch = amount;
            if (this.canAccept(result, amount)) {
                // Everything that changes the throughput is applied exactly once, right here:
                // parallel lanes (seed count), speed cards (shorter batches) and the tick/energy
                // scaling of the tier. Nothing is applied a second time anywhere else.
                int ticks = Math.max(1, (int) Math.round(recipe.getTicks()
                        / (this.tier.getSpeedMultiplier() * baseSpeed * speedFactor)));
                // The tier and the base speed appear in the numerator and in the denominator, so the FE
                // one operation needs stays constant across tiers; only the Mekanism upgrade factor
                // changes the price of a crystal (speed cards make it more expensive, energy cards cheaper).
                int costPerTick = Math.max(1, (int) Math.round(recipe.getEnergyPerTick() * lanes
                        * this.tier.getSpeedMultiplier() * baseSpeed * baseEnergy * energyUpgradeFactor));
                if (CrystalMultiplierConfig.oneTickOutput()) {
                    // Optional global switch: one finished batch per tick, but the whole FE cost of that
                    // operation is paid inside that single tick, so the cost per crystal never changes.
                    this.ticksRequired = 1;
                    this.energyPerTick = (int) Math.min(Integer.MAX_VALUE, (long) costPerTick * ticks);
                } else {
                    this.ticksRequired = ticks;
                    this.energyPerTick = costPerTick;
                }
                // Mekanism style throttling: when the internal buffer cannot pay a whole tick the machine
                // does not stall dead, it spends what it has and works proportionally slower. Without this
                // an overclocked machine could ask for more FE per tick than its buffer can ever hold.
                int charge = Math.min(this.energyPerTick, this.energy);
                if (charge > 0) {
                    this.energy -= charge;
                    this.progress += (double) charge / (double) this.energyPerTick;
                    working = true;
                    this.setChanged();
                    if (this.progress >= this.ticksRequired) {
                        this.progress = 0.0D;
                        this.pushResult(result, amount);
                    }
                }
            } else {
                this.progress = 0.0D;
            }
        } else {
            this.progress = 0.0D;
            this.ticksRequired = 0;
            this.energyPerTick = 0;
            this.itemsPerBatch = 0;
        }

        if (this.parallelism == 0) {
            this.itemsPerBatch = 0;
        }

        if (this.active != working) {
            this.active = working;
            BlockState newState = this.getBlockState().setValue(CrystalMultiplierBlock.ACTIVE, working);
            this.level.setBlock(this.worldPosition, newState, Block.UPDATE_CLIENTS);
            this.setChanged();
        }

        if (--this.syncCooldown <= 0) {
            this.syncCooldown = 5;
            this.syncToClients();
        }
    }

    /** Pushes energy and friends to the clients, because a ContainerData slot is only a short. */
    private void syncToClients() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        int extraSignature = java.util.Arrays.hashCode(this.inventory.copyExtra());
        if (!this.active && this.lastSyncedEnergy == this.energy && this.lastSyncedExtra == extraSignature) {
            return;
        }
        this.lastSyncedEnergy = this.energy;
        this.lastSyncedExtra = extraSignature;
        CompoundTag tag = this.makeSyncTag();
        PacketDistributor.TRACKING_CHUNK
                .with(() -> this.level.getChunkAt(this.worldPosition))
                .send(ClientboundBlockEntityDataPacket.create(this, entity -> tag));
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

    private boolean canAccept(ItemStack result, int amount) {
        if (result.isEmpty() || amount <= 0) {
            return false;
        }
        int remaining = amount;
        for (int slot = this.getOutputStart(); slot < this.getOutputEnd() && remaining > 0; slot++) {
            remaining = this.inventory.insertUnchecked(slot, result.copyWithCount(remaining), true).getCount();
        }
        return remaining <= 0;
    }

    private void pushResult(ItemStack result, int amount) {
        int remaining = amount;
        for (int slot = this.getOutputStart(); slot < this.getOutputEnd() && remaining > 0; slot++) {
            remaining = this.inventory.insertUnchecked(slot, result.copyWithCount(remaining), false).getCount();
        }
        this.setChanged();
    }

    /* ------------------------------------------------------------------ syncing */

    /**
     * A ContainerData slot travels as a short, so energy (up to 128,000,000), the energy per tick
     * and the batch size would be cut in half on the client. Those values therefore go through the
     * block entity update packet instead of the menu data slots.
     */
    private CompoundTag makeSyncTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("sync_energy", this.energy);
        tag.putInt("sync_cost", this.energyPerTick);
        tag.putInt("sync_batch", this.itemsPerBatch);
        tag.putDouble("sync_progress", this.progress);
        // Items above the byte safe stack limit live outside of the ItemStack and need their own channel.
        tag.putIntArray("sync_extra", this.inventory.copyExtra());
        return tag;
    }

    private void readSyncTag(CompoundTag tag) {
        if (tag.contains("sync_energy")) {
            this.energy = tag.getInt("sync_energy");
        }
        if (tag.contains("sync_cost")) {
            this.energyPerTick = tag.getInt("sync_cost");
        }
        if (tag.contains("sync_batch")) {
            this.itemsPerBatch = tag.getInt("sync_batch");
        }
        if (tag.contains("sync_progress")) {
            this.progress = tag.getDouble("sync_progress");
        }
        if (tag.contains("sync_extra")) {
            this.inventory.applyExtra(readIntList(tag, "sync_extra"));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.makeSyncTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, entity -> entity instanceof CrystalMultiplierBlockEntity machine
                ? machine.makeSyncTag()
                : new CompoundTag());
    }

    /* ------------------------------------------------------------------- saving */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", this.inventory.serializeNBT());
        // Only persist the overflow counters when something actually overflowed: keeps saves small and
        // leaves no stale all-zero array behind that would shadow a datapack/command provided value.
        int[] extraSnapshot = this.inventory.copyExtra();
        boolean hasOverflow = false;
        for (int value : extraSnapshot) {
            if (value != 0) {
                hasOverflow = true;
                break;
            }
        }
        if (hasOverflow) {
            tag.putIntArray("inventory_extra", extraSnapshot);
        }
        tag.putInt("energy", this.energy);
        tag.putDouble("progress", this.progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("inventory")) {
            this.inventory.deserializeNBT(tag.getCompound("inventory"));
        }
        if (tag.contains("inventory_extra")) {
            this.inventory.applyExtra(readIntList(tag, "inventory_extra"));
        }
        this.energy = tag.getInt("energy");
        this.progress = tag.getDouble("progress");
        this.readSyncTag(tag);
    }

    /**
     * Reads an integer sequence from NBT. Both a real int array ({@code [I;1,2,3]}) and a plain list of
     * ints ({@code [1,2,3]}) are accepted, so hand written command or datapack data cannot silently be
     * dropped because of a missing type prefix.
     */
    private static int[] readIntList(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_INT_ARRAY)) {
            return tag.getIntArray(key);
        }
        if (tag.contains(key, Tag.TAG_LIST)) {
            ListTag list = tag.getList(key, Tag.TAG_INT);
            int[] values = new int[list.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = list.getInt(i);
            }
            return values;
        }
        return new int[0];
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

    /**
     * The machine inventory. A single {@link ItemStack} can only carry 127 items in 1.20.1 (the count is
     * stored in a signed byte, both in the save file and on the wire), but the tiers advertise much bigger
     * stacks (64/128/256/512). Every slot therefore keeps a normal, byte safe visible stack plus a hidden
     * overflow counter, and all access goes through this handler so that nothing is ever truncated.
     */
    public class MachineInventory extends ItemStackHandler {

        private final int[] extra;

        public MachineInventory(int size) {
            super(size);
            this.extra = new int[size];
        }

        /** Items of this slot that do not fit into the visible stack. */
        public int getExtra(int slot) {
            return slot >= 0 && slot < this.extra.length ? this.extra[slot] : 0;
        }

        /** Real amount of items in this slot: visible stack plus overflow. */
        public int getStored(int slot) {
            return this.getStackInSlot(slot).getCount() + this.getExtra(slot);
        }

        public int[] copyExtra() {
            return this.extra.clone();
        }

        public void applyExtra(int[] values) {
            if (values == null) {
                return;
            }
            for (int slot = 0; slot < this.extra.length; slot++) {
                int requested = slot < values.length ? Math.max(0, values[slot]) : 0;
                ItemStack visible = this.stacks.get(slot);
                if (requested <= 0 || visible.isEmpty()) {
                    // An overflow counter without a visible stack has no item type to restore: drop it.
                    this.extra[slot] = 0;
                    continue;
                }
                // Rebuilding through write keeps two promises: the total never exceeds the slot capacity
                // and a non empty overflow always comes with a byte safe (127) visible stack.
                this.write(slot, visible, Math.min(this.capacityOf(slot), visible.getCount() + requested));
            }
        }

        /** Total amount this slot may hold, visible stack plus overflow. */
        public int capacityOf(int slot) {
            CrystalMultiplierBlockEntity machine = CrystalMultiplierBlockEntity.this;
            if (slot == machine.getSeedSlot()) {
                return Math.max(1, machine.getSeedCapacity());
            }
            if (slot >= machine.getOutputStart() && slot < machine.getOutputEnd()) {
                return Math.max(1, machine.getOutputStackLimit());
            }
            // Upgrade cards stack like they do inside a Mekanism machine (a speed card stacks to 8);
            // how many cards of one kind a machine accepts is decided by isItemValid.
            return MAX_UPGRADE_SLOT_STACK;
        }

        /**
         * Writes a total amount into a slot, splitting it into a byte safe visible stack plus overflow.
         * The invariant this relies on: whenever the overflow is not empty the visible stack is full
         * ({@link #MAX_STACK_COUNT}), so vanilla menu code can never grow it past the byte limit.
         */
        private void write(int slot, ItemStack prototype, int stored) {
            int total = Math.max(0, Math.min(this.capacityOf(slot), stored));
            if (total <= 0 || prototype.isEmpty()) {
                this.stacks.set(slot, ItemStack.EMPTY);
                this.extra[slot] = 0;
            } else {
                int visible = Math.min(total, MAX_STACK_COUNT);
                this.stacks.set(slot, prototype.copyWithCount(visible));
                this.extra[slot] = total - visible;
            }
            this.onContentsChanged(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == CrystalMultiplierBlockEntity.this.getSeedSlot()) {
                return true;
            }
            if (slot >= CrystalMultiplierBlockEntity.this.getUpgradeStart()) {
                if (!isUpgradeItem(stack)) {
                    return false;
                }
                // Same rule as Mekanism: at most Upgrade#getMax cards of one kind.
                int installed = 0;
                for (int other = CrystalMultiplierBlockEntity.this.getUpgradeStart();
                        other < CrystalMultiplierBlockEntity.this.getUpgradeEnd(); other++) {
                    ItemStack existing = this.getStackInSlot(other);
                    if (!existing.isEmpty() && ItemStack.isSameItem(existing, stack)) {
                        installed += existing.getCount() + this.getExtra(other);
                    }
                }
                return installed + stack.getCount() <= maxUpgradeCount(stack);
            }
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.capacityOf(slot);
        }

        @Override
        public int getStackLimit(int slot, ItemStack stack) {
            int capacity = this.capacityOf(slot);
            if (slot == CrystalMultiplierBlockEntity.this.getSeedSlot()) {
                return capacity;
            }
            if (slot >= CrystalMultiplierBlockEntity.this.getOutputStart()
                    && slot < CrystalMultiplierBlockEntity.this.getOutputEnd()) {
                int itemMax = stack.getMaxStackSize();
                return Math.max(1, Math.min(capacity, itemMax >= 64 ? capacity : itemMax));
            }
            return Math.max(1, Math.min(capacity, stack.getMaxStackSize()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !this.isItemValid(slot, stack)) {
                return stack;
            }
            return this.insertUnchecked(slot, stack, simulate);
        }

        /** Insert that skips {@link #isItemValid}: the machine itself fills its output slots with this. */
        public ItemStack insertUnchecked(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= this.getSlots()) {
                return stack;
            }
            ItemStack existing = this.getStackInSlot(slot);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, stack)) {
                return stack;
            }
            int stored = existing.getCount() + this.getExtra(slot);
            int space = this.capacityOf(slot) - stored;
            if (space <= 0) {
                return stack;
            }
            int moved = Math.min(space, stack.getCount());
            if (moved <= 0) {
                return stack;
            }
            if (!simulate) {
                this.write(slot, stack, stored + moved);
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(moved);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = this.getStackInSlot(slot);
            if (existing.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int stored = existing.getCount() + this.getExtra(slot);
            // Hand out at most one byte safe stack per call, so pipes and menus never get a broken stack.
            int perStack = Math.max(1, Math.min(MAX_STACK_COUNT, existing.getMaxStackSize()));
            int moved = Math.min(Math.min(amount, perStack), stored);
            if (moved <= 0) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                this.write(slot, existing, stored - moved);
            }
            return existing.copyWithCount(moved);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                this.write(slot, ItemStack.EMPTY, 0);
                return;
            }
            // Keep the overflow of a matching stack, otherwise a plain slot click would eat it.
            int kept = ItemStack.isSameItemSameTags(this.getStackInSlot(slot), stack) ? this.getExtra(slot) : 0;
            this.write(slot, stack, stack.getCount() + kept);
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            CrystalMultiplierBlockEntity.this.setChanged();
        }
    }
}
