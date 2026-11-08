package cn.woxin.crystalmultiplier.content;

import cn.woxin.crystalmultiplier.CrystalMultiplierMod;
import cn.woxin.crystalmultiplier.config.CrystalMultiplierConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.providers.IBlockProvider;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.api.RelativeSide;
import mekanism.common.tile.interfaces.IRedstoneControl;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The crystal multiplier, rebuilt on top of Mekanism's own machine framework.
 *
 * <p>Because this extends {@link TileEntityMekanism} the machine gets Mekanism's native GUI tabs for
 * upgrades, side configuration, energy and redstone control for free. The actual crystal logic is
 * still the one from this mod: the seed stack is never consumed, its size decides the number of
 * parallel lanes and every finished batch is pushed into the output slots.</p>
 */
public class CrystalMultiplierBlockEntity extends TileEntityMekanism implements ISideConfiguration {

    /** Kept for the menu/screen code: upper bound for a single simulated extraction. */
    public static final int MAX_STACK_COUNT = CrystalSlot.MAX_VISIBLE;

    /** Seed slot, followed by the output slots. Never initialise this here: super() calls getInitialInventory first. */
    private CrystalSlot seedSlot;
    private List<CrystalSlot> outputSlots;

    private MachineEnergyContainer<CrystalMultiplierBlockEntity> energyContainer;
    private TileComponentConfig configComponent;
    private TileComponentEjector ejectorComponent;

    /** Progress in the current operation, measured in "work ticks" (may be fractional when energy is short). */
    private double progress;
    private int ticksRequired;
    private int energyPerTick;
    private int parallelism;
    private int itemsPerBatch;
    private boolean seedValid;

    public CrystalMultiplierBlockEntity(IBlockProvider blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }

    public CrystalMultiplierBlockEntity(BlockPos pos, BlockState state) {
        this(blockProviderFor(state.getBlock()), pos, state);
    }

    private static IBlockProvider blockProviderFor(Block block) {
        for (var entry : CrystalMultiplierMod.TIER_BLOCKS) {
            if (entry.getBlock() == block) {
                return entry;
            }
        }
        throw new IllegalStateException("Unknown crystal multiplier block: " + block);
    }

    public CrystalTier getTier() {
        Block block = this.getBlockState().getBlock();
        return block instanceof CrystalMultiplierBlock multiplier ? multiplier.getTier() : CrystalTier.BASIC;
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        this.outputSlots = new ArrayList<>();
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this::getDirection, this::getConfig);
        this.seedSlot = CrystalSlot.seed(this::getSeedCapacity, listener, 26, 40);
        builder.addSlot(this.seedSlot);
        CrystalTier tier = this.getTier();
        for (int i = 0; i < tier.getOutputSlots(); i++) {
            int x = 88 + (i % 4) * 18;
            int y = 20 + (i / 4) * 18;
            CrystalSlot slot = CrystalSlot.output(this::getOutputStackLimit, listener, x, y);
            this.outputSlots.add(slot);
            builder.addSlot(slot);
        }
        return builder.build();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this::getDirection, this::getConfig);
        this.energyContainer = builder.addContainer(MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.configComponent == null) {
            this.setupComponents();
        }
    }

    /**
     * Components need the slots and the energy container, which only exist after super() has run.
     * They are created lazily on first access so both the server and the client can build them.
     */
    private void setupComponents() {
        if (this.configComponent != null) {
            return;
        }
        this.configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);
        ConfigInfo itemConfig = this.configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            // The machine only ever outputs items; the seed slot is internal and must not appear
            // as an item input in the side configuration. Energy is handled by its own config below.
            List<IInventorySlot> outputs = List.copyOf(this.outputSlots);
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputs));
            itemConfig.setDefaults();
            // Expose item output on every side except the back, which stays the energy input.
            itemConfig.setDataType(DataType.OUTPUT, RelativeSide.FRONT, RelativeSide.LEFT,
                    RelativeSide.RIGHT, RelativeSide.TOP, RelativeSide.BOTTOM);
            itemConfig.setEjecting(true);
        }
        this.configComponent.setupInputConfig(TransmissionType.ENERGY, this.energyContainer);
        this.ejectorComponent = new TileComponentEjector(this).setOutputData(this.configComponent, TransmissionType.ITEM);
    }

    @Override
    public TileComponentConfig getConfig() {
        if (this.configComponent == null) {
            this.setupComponents();
        }
        return this.configComponent;
    }

    @Override
    public TileComponentEjector getEjector() {
        if (this.ejectorComponent == null) {
            this.setupComponents();
        }
        return this.ejectorComponent;
    }

    public MachineEnergyContainer<CrystalMultiplierBlockEntity> getEnergyContainer() {
        return this.energyContainer;
    }

    public CrystalSlot getSeedSlot() {
        return this.seedSlot;
    }

    public List<CrystalSlot> getOutputSlots() {
        return this.outputSlots == null ? Collections.emptyList() : Collections.unmodifiableList(this.outputSlots);
    }

    public List<CrystalSlot> getMachineSlots() {
        List<CrystalSlot> slots = new ArrayList<>(1 + this.getOutputSlots().size());
        slots.add(this.seedSlot);
        slots.addAll(this.getOutputSlots());
        return slots;
    }

    /* ------------------------------------------------------------------ upgrades */

    public int getUpgradeCount(Upgrade type) {
        var upgradeComponent = this.getComponent();
        return upgradeComponent == null ? 0 : upgradeComponent.getUpgrades(type);
    }

    /** Seed slot capacity: the tier base plus one bonus per installed speed/energy upgrade card. */
    public int getSeedCapacity() {
        int installed = this.getUpgradeCount(Upgrade.SPEED) + this.getUpgradeCount(Upgrade.ENERGY);
        int capacity = this.getTier().getSeedStackLimit() + CrystalMultiplierConfig.seedCapacityPerUpgrade() * installed;
        return Math.max(1, Math.min(CrystalMultiplierConfig.seedSlotCapCeiling(), capacity));
    }

    public int getOutputStackLimit() {
        return this.getTier().getOutputStackLimit();
    }

    /* --------------------------------------------------------------- processing */

    public double getScaledProgress() {
        return this.ticksRequired <= 0 ? 0.0D : Math.min(1.0D, this.progress / (double) this.ticksRequired);
    }

    public int getParallelism() {
        return this.parallelism;
    }

    public int getItemsPerBatch() {
        return this.itemsPerBatch;
    }

    public int getEnergyPerTick() {
        return this.energyPerTick;
    }

    public int getTicksRequired() {
        return this.ticksRequired;
    }

    public boolean isSeedValid() {
        return this.seedValid;
    }

    public int getCrystalsPerParallel(@Nullable CrystalMultiplicationRecipe recipe) {
        int fromRecipe = recipe == null ? 0 : recipe.getCrystalsPerParallel();
        int per = fromRecipe > 0 ? fromRecipe : CrystalMultiplierConfig.crystalsPerParallel();
        return Math.max(1, per);
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.configComponent == null) {
            this.setupComponents();
        }
        // TileEntityMekanism only ticks the frequency and upgrade components. The ejector has to be
        // ticked explicitly, exactly like TileEntityConfigurableMachine does for Mekanism machines.
        if (this.ejectorComponent != null) {
            this.ejectorComponent.tickServer();
        }

        CrystalMultiplicationRecipe recipe = this.findRecipe();
        ItemStack seed = this.seedSlot.getStack();
        int seedCount = this.seedSlot.getStoredCount();
        int perParallel = this.getCrystalsPerParallel(recipe);
        // Any seed count at or above one lane is enough: the lanes are the integer division, the
        // leftovers simply do not form an extra lane (16 / 16 / 16 + 5 leftover = 3 lanes, still runs).
        this.seedValid = !seed.isEmpty() && seedCount >= perParallel;
        this.parallelism = this.seedValid ? Math.min(CrystalMultiplierConfig.maxParallel(), seedCount / perParallel) : 0;

        boolean working = false;
        if (recipe != null && this.parallelism > 0 && MekanismUtils.canFunction(this)) {
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            int amount = Math.max(1, result.getCount() * this.parallelism);
            this.itemsPerBatch = amount;
            if (this.canAccept(result, amount)) {
                double upgradeMultiplier = Math.max(1.0D, CrystalMultiplierConfig.maxUpgradeMultiplier());
                double fractionSpeed = (double) this.getUpgradeCount(Upgrade.SPEED) / (double) Upgrade.SPEED.getMax();
                double fractionEnergy = (double) this.getUpgradeCount(Upgrade.ENERGY) / (double) Upgrade.ENERGY.getMax();
                double speedFactor = Math.min(4096.0D, Math.pow(upgradeMultiplier, fractionSpeed));
                double energyFactor = Math.min(1_048_576.0D,
                        Math.pow(upgradeMultiplier, 2.0D * fractionSpeed - fractionEnergy));
                double baseSpeed = Math.max(0.01D, CrystalMultiplierConfig.baseSpeedMultiplier());
                double baseEnergy = Math.max(0.0001D, CrystalMultiplierConfig.baseEnergyMultiplier());

                int ticks = Math.max(1, (int) Math.round(recipe.getTicks()
                        / (this.getTier().getSpeedMultiplier() * baseSpeed * speedFactor)));
                int costPerTick = Math.max(1, (int) Math.round(recipe.getEnergyPerTick() * this.parallelism
                        * this.getTier().getSpeedMultiplier() * baseSpeed * baseEnergy * energyFactor));
                if (CrystalMultiplierConfig.oneTickOutput()) {
                    // One batch per tick (the default). The tuned recipe duration is not dropped: the
                    // whole operation's energy is folded into that single tick, so a faster tier or
                    // more speed cards still cost / pay exactly what they did before.
                    this.ticksRequired = 1;
                    this.energyPerTick = (int) Math.min(Integer.MAX_VALUE, (long) costPerTick * ticks);
                } else {
                    this.ticksRequired = ticks;
                    this.energyPerTick = costPerTick;
                }

                int charge = (int) Math.min(this.energyPerTick, this.energyContainer.getEnergy().getValue());
                if (charge > 0) {
                    FloatingLong extracted = this.energyContainer.extract(FloatingLong.create(charge), Action.EXECUTE, AutomationType.INTERNAL);
                    int paid = (int) Math.min(Integer.MAX_VALUE, extracted.getValue());
                    if (paid > 0) {
                        this.progress += (double) paid / (double) this.energyPerTick;
                        working = true;
                        if (this.progress >= this.ticksRequired) {
                            this.progress = 0.0D;
                            this.pushResult(result, amount);
                        }
                    }
                }
            } else {
                this.progress = 0.0D;
                this.itemsPerBatch = 0;
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
        if (this.getActive() != working) {
            this.setActive(working);
            this.markForSave();
        }
    }

    @Nullable
    public CrystalMultiplicationRecipe findRecipe() {
        if (this.level == null || this.seedSlot == null) {
            return null;
        }
        ItemStack seed = this.seedSlot.getStack();
        if (seed.isEmpty()) {
            return null;
        }
        for (CrystalMultiplicationRecipe recipe : this.level.getRecipeManager().getAllRecipesFor(CrystalMultiplicationRecipe.TYPE)) {
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
        for (CrystalSlot slot : this.outputSlots) {
            if (remaining <= 0) {
                break;
            }
            remaining = slot.insertInternal(result.copyWithCount(remaining), Action.SIMULATE).getCount();
        }
        return remaining <= 0;
    }

    private void pushResult(ItemStack result, int amount) {
        int remaining = amount;
        for (CrystalSlot slot : this.outputSlots) {
            if (remaining <= 0) {
                break;
            }
            remaining = slot.insertInternal(result.copyWithCount(remaining), Action.EXECUTE).getCount();
        }
        this.markForSave();
    }

    /* ---------------------------------------------------------------- syncing */

    @Override
    public Component getName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public Component getDisplayName() {
        // TileEntityMekanism defaults nameable tiles to container.<registry name>; use the block name
        // so the GUI title is the same tier-specific name shown by the item.
        return this.getName();
    }

    @Override
    public @NotNull IUpgradeData getUpgradeData() {
        if (this.configComponent == null) {
            this.setupComponents();
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Energy", this.energyContainer.serializeNBT());
        ListTag slots = new ListTag();
        for (CrystalSlot slot : this.getMachineSlots()) {
            slots.add(slot.serializeNBT());
        }
        tag.put("Slots", slots);
        CompoundTag components = new CompoundTag();
        for (ITileComponent component : this.getComponents()) {
            component.write(components);
        }
        tag.put("Components", components);
        tag.putDouble("CrystalProgress", this.progress);
        tag.putInt("CrystalTicksRequired", this.ticksRequired);
        tag.putInt("CrystalEnergyPerTick", this.energyPerTick);
        tag.putInt("CrystalParallelism", this.parallelism);
        tag.putInt("CrystalItemsPerBatch", this.itemsPerBatch);
        tag.putBoolean("CrystalSeedValid", this.seedValid);
        tag.putInt("ControlType", this.getControlType().ordinal());
        tag.putBoolean("Active", this.getActive());
        return new CrystalUpgradeData(tag);
    }

    @Override
    public void parseUpgradeData(@NotNull IUpgradeData data) {
        if (!(data instanceof CrystalUpgradeData upgrade)) {
            super.parseUpgradeData(data);
            return;
        }
        CompoundTag tag = upgrade.getTag();
        this.progress = tag.getDouble("CrystalProgress");
        this.ticksRequired = tag.getInt("CrystalTicksRequired");
        this.energyPerTick = tag.getInt("CrystalEnergyPerTick");
        this.parallelism = tag.getInt("CrystalParallelism");
        this.itemsPerBatch = tag.getInt("CrystalItemsPerBatch");
        this.seedValid = tag.getBoolean("CrystalSeedValid");
        if (tag.contains("ControlType", Tag.TAG_INT)) {
            this.setControlType(IRedstoneControl.RedstoneControl.byIndexStatic(tag.getInt("ControlType")));
        }
        if (tag.contains("Energy", Tag.TAG_COMPOUND)) {
            this.energyContainer.deserializeNBT(tag.getCompound("Energy"));
        }
        ListTag slots = tag.getList("Slots", Tag.TAG_COMPOUND);
        List<CrystalSlot> machine = this.getMachineSlots();
        for (int i = 0; i < Math.min(slots.size(), machine.size()); i++) {
            machine.get(i).deserializeNBT(slots.getCompound(i));
        }
        if (this.configComponent == null) {
            this.setupComponents();
        }
        CompoundTag components = tag.getCompound("Components");
        for (ITileComponent component : this.getComponents()) {
            component.read(components);
        }
        this.setActive(tag.getBoolean("Active"));
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableDouble.create(() -> this.progress, value -> this.progress = value));
        container.track(SyncableInt.create(() -> this.ticksRequired, value -> this.ticksRequired = value));
        container.track(SyncableInt.create(() -> this.energyPerTick, value -> this.energyPerTick = value));
        container.track(SyncableInt.create(() -> this.parallelism, value -> this.parallelism = value));
        container.track(SyncableInt.create(() -> this.itemsPerBatch, value -> this.itemsPerBatch = value));
    }

    /* ----------------------------------------------------------------- saving */

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("CrystalProgress", this.progress);
        tag.putInt("CrystalTicksRequired", this.ticksRequired);
        tag.putInt("CrystalEnergyPerTick", this.energyPerTick);
        tag.putInt("CrystalParallelism", this.parallelism);
        tag.putInt("CrystalItemsPerBatch", this.itemsPerBatch);
        tag.putBoolean("CrystalSeedValid", this.seedValid);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        this.progress = tag.getDouble("CrystalProgress");
        this.ticksRequired = tag.getInt("CrystalTicksRequired");
        this.energyPerTick = tag.getInt("CrystalEnergyPerTick");
        this.parallelism = tag.getInt("CrystalParallelism");
        this.itemsPerBatch = tag.getInt("CrystalItemsPerBatch");
        this.seedValid = tag.getBoolean("CrystalSeedValid");
    }
}
