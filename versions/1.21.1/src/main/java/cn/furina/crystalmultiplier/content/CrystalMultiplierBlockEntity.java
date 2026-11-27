package cn.furina.crystalmultiplier.content;

import cn.furina.crystalmultiplier.compat.ae2.Ae2Compat;
import cn.furina.crystalmultiplier.config.CrystalMultiplierConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
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
import mekanism.common.tile.interfaces.IRedstoneControl;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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
 *
 * <p>1.21.1 notes: energy is a plain {@code long} (Mekanism 10.7 dropped FloatingLong), all
 * serialisation takes a {@link HolderLookup.Provider}, and the tile is built from a
 * {@link Holder} instead of an {@code IBlockProvider}.</p>
 */
public class CrystalMultiplierBlockEntity extends TileEntityMekanism implements ISideConfiguration {

    /** Kept for the menu/screen code: upper bound for a single simulated extraction. */
    public static final int MAX_STACK_COUNT = CrystalSlot.MAX_VISIBLE;

    /* --------------------------------------------------- production feedback */
    /** How far in front of the face the chime plays, in blocks (0.5 = block centre, +0.6 = just outside). */
    private static final double EJECT_FRONT_OFFSET = 0.6D;
    /** At most one chime per this many ticks, so 20 tps machines cannot spam the sound channel. */
    private static final int EJECT_SOUND_INTERVAL_TICKS = 40;
    /** Deliberately quiet: a hint, not a fanfare. */
    private static final float EJECT_SOUND_VOLUME = 0.25F;
    private static final float EJECT_SOUND_PITCH = 1.4F;

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
    /** Per-machine switch: hand every finished batch straight to an adjacent ME network. */
    private boolean aeOutput;

    /** Whether finished batches go into an adjacent ME network instead of the output slots. */
    public boolean isAeOutputEnabled() {
        return this.aeOutput;
    }

    /** Toggled from the machine GUI; synced to the client through the container trackers. */
    public void setAeOutput(boolean enabled) {
        if (this.aeOutput != enabled) {
            this.aeOutput = enabled;
            this.setChanged();
        }
    }

    /** Game time of the last chime; {@code Long.MIN_VALUE / 2} so the very first batch always chimes. */
    private long lastEjectSoundTick = Long.MIN_VALUE / 2;

    /** Last comparator level handed back by {@link #getRedstoneLevel()}, only used to avoid pointless updates. */
    private int lastComparatorLevel = -1;

    public CrystalMultiplierBlockEntity(Holder<Block> blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
        // Built right away (the slots and the energy container exist once super() returned) so the
        // side/ejector components are already registered when Mekanism reads the tile NBT - that is
        // what makes the side configuration survive a reload.
        this.setupComponents();
    }

    /**
     * Block entity type entry point. Mekanism 10.7 wants a {@code Holder<Block>}, which a modded
     * block always carries once it is registered, so no extra block lookup is needed here.
     */
    public CrystalMultiplierBlockEntity(BlockPos pos, BlockState state) {
        this(state.getBlock().builtInRegistryHolder(), pos, state);
    }

    public CrystalTier getTier() {
        BlockState state = this.getBlockState();
        Block block = state == null ? null : state.getBlock();
        return block instanceof CrystalMultiplierBlock multiplier ? multiplier.getTier() : CrystalTier.BASIC;
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        this.outputSlots = new ArrayList<>();
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
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
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        this.energyContainer = builder.addContainer(MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.setupComponents();
    }

    /**
     * Components need the slots and the energy container, which only exist after super() has run.
     * They are created lazily on first access so both the server and the client can build them, and
     * they are registered on the tile so Mekanism saves their side/ejector settings with the tile.
     */
    private void setupComponents() {
        if (this.configComponent != null) {
            return;
        }
        this.configComponent = new TileComponentConfig(this, new java.util.LinkedHashSet<>(List.of(
                TransmissionType.ITEM, TransmissionType.ENERGY)));
        ConfigInfo itemConfig = this.configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            // The machine only ever outputs items; the seed slot is internal and must not appear
            // as an item input in the side configuration. Energy is handled by its own config below.
            List<IInventorySlot> outputs = List.copyOf(this.outputSlots);
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputs));
            // Expose item output on every side except the back, which stays the energy input
            // (1.21.1 ConfigInfo defaults every side to DataType.NONE, so only OUTPUT is needed).
            for (RelativeSide side : RelativeSide.values()) {
                if (side != RelativeSide.BACK) {
                    itemConfig.setDataType(DataType.OUTPUT, side);
                }
            }
            itemConfig.setEjecting(true);
        }
        ConfigInfo energyConfig = this.configComponent.setupInputConfig(TransmissionType.ENERGY, this.energyContainer);
        if (energyConfig != null) {
            // A freshly placed machine accepts energy on every face; players narrow it down
            // through the side configuration if they want to.
            for (RelativeSide side : RelativeSide.values()) {
                energyConfig.setDataType(DataType.INPUT, side);
            }
        }
        this.ejectorComponent = new TileComponentEjector(this).setOutputData(this.configComponent, TransmissionType.ITEM);
        // Registering them makes Mekanism persist the side/ejector configuration together with the
        // tile; neither component is ticked by TileEntityMekanism itself (see onUpdateServer).
        this.addComponent(this.configComponent);
        this.addComponent(this.ejectorComponent);
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

    /** Seed slot capacity: the tier base only, upgrade cards deliberately do not enlarge it. */
    public int getSeedCapacity() {
        int capacity = this.getTier().getSeedStackLimit();
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
    protected boolean onUpdateServer() {
        boolean needsSync = super.onUpdateServer();
        if (this.level == null || this.level.isClientSide) {
            return needsSync;
        }
        if (this.configComponent == null) {
            this.setupComponents();
        }

        CrystalMultiplicationRecipe recipe = this.findRecipe();
        ItemStack seed = this.seedSlot.getStack();
        int seedCount = this.seedSlot.getStoredCount();
        int perParallel = this.getCrystalsPerParallel(recipe);
        // Any seed count at or above one lane is enough: the lanes are the integer division, the
        // leftovers simply do not form an extra lane (16 / 16 / 16 + 5 leftover = 3 lanes, still runs).
        this.seedValid = !seed.isEmpty() && seedCount >= perParallel;
        // Speed cards no longer speed the machine up: every card multiplies the number of parallel
        // lanes instead (base ^ cards/max, base = maxUpgradeMultiplier, so 8 cards = 10x lanes).
        double upgradeMultiplier = Math.max(1.0D, CrystalMultiplierConfig.maxUpgradeMultiplier());
        // Normalise by the configured step (default 8 = the vanilla cap of 8 cards) instead of the
        // enum max: addons that raise the per machine card cap keep scaling the lanes this way.
        double step = (double) CrystalMultiplierConfig.upgradeCardsPerStep();
        double fractionSpeed = (double) this.getUpgradeCount(Upgrade.SPEED) / step;
        double fractionEnergy = (double) this.getUpgradeCount(Upgrade.ENERGY) / step;
        double parallelFactor = Math.min(1_048_576.0D, Math.pow(upgradeMultiplier, fractionSpeed));
        int lanes = this.seedValid ? seedCount / perParallel : 0;
        this.parallelism = lanes <= 0 ? 0
                : (int) Math.min((long) CrystalMultiplierConfig.maxParallel(), Math.round(lanes * parallelFactor));

        boolean working = false;
        if (recipe != null && this.parallelism > 0 && this.canFunction()) {
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            int amount = Math.max(1, result.getCount() * this.parallelism);
            this.itemsPerBatch = amount;
            if (this.canAccept(result, amount)) {
                // Energy cards only cut the FE per tick (base ^ -cards/max, 8 cards = 1/10). They do
                // not touch the energy buffer (that is tier only) nor the seed slot.
                double energyFactor = Math.min(1_048_576.0D, Math.pow(upgradeMultiplier, -fractionEnergy));
                double baseSpeed = Math.max(0.01D, CrystalMultiplierConfig.baseSpeedMultiplier());
                double baseEnergy = Math.max(0.0001D, CrystalMultiplierConfig.baseEnergyMultiplier());

                // Batch time depends on the tier and the global base speed only, never on cards.
                int ticks = Math.max(1, (int) Math.round(recipe.getTicks()
                        / (this.getTier().getSpeedMultiplier() * baseSpeed)));
                long costPerTick = Math.max(1L, Math.round(recipe.getEnergyPerTick() * (double) this.parallelism
                        * this.getTier().getSpeedMultiplier() * baseSpeed * baseEnergy * energyFactor));
                this.ticksRequired = ticks;
                this.energyPerTick = (int) Math.min(Integer.MAX_VALUE, costPerTick);

                // 1.21.1: Mekanism energy is a long, so no FloatingLong conversion is needed.
                long charge = Math.min((long) this.energyPerTick, this.energyContainer.getEnergy());
                if (charge > 0) {
                    long paid = this.energyContainer.extract(charge, Action.EXECUTE, AutomationType.INTERNAL);
                    if (paid > 0) {
                        this.progress += (double) paid / (double) this.energyPerTick;
                        working = true;
                        if (this.progress >= this.ticksRequired) {
                            this.progress = 0.0D;
                            if (this.pushResult(result, amount)) {
                                // Only when something actually landed in the output slots.
                                this.playEjectChime();
                            }
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
            this.setChanged();
            needsSync = true;
        }
        // Comparator: Mekanism only recomputes the signal when the tile asks for it (inventory changes do
        // it on their own), so the progress ramp has to nudge it here. markDirtyComparator() is a cheap
        // boolean flag and the actual notifyComparatorChange() only fires when the level really changed.
        int comparatorLevel = this.getRedstoneLevel();
        if (comparatorLevel != this.lastComparatorLevel) {
            this.lastComparatorLevel = comparatorLevel;
            this.markDirtyComparator();
        }
        // TileEntityMekanism only ticks the frequency and upgrade components, so the ejector has to be
        // ticked explicitly, exactly like TileEntityConfigurableMachine does for Mekanism machines.
        // It runs last on purpose: pushResult above already put this tick's products into the output
        // slots, so with the "eject every tick" option on they leave the machine in the same tick.
        // AE2: hand the finished batch to an adjacent ME network first (产物回网); whatever the
        // network does not take stays in the slots for the ejector below.
        Ae2Compat.drainOutput(this);
        if (this.ejectorComponent != null) {
            this.ejectorComponent.tickServer();
        }
        return needsSync;
    }

    /**
     * Comparator output of the machine, 0-15.
     *
     * <p>Hook chain in 1.21.1 (verified against Mekanism 10.7.19): the block is registered with
     * {@code Attributes.COMPARATOR}, {@code BlockMekanism#hasAnalogOutputSignal} then reports true,
     * {@code BlockMekanism#getAnalogOutputSignal} reads {@code IComparatorSupport#getCurrentRedstoneLevel()}
     * and that cached field is recomputed from {@code IComparatorSupport#getRedstoneLevel()} - which is the
     * method overridden here. There is no {@code getAnalogOutputSignal} on {@code TileEntityMekanism} in
     * this version, and {@code getRedstoneLevel()} is not final, so this is the right hook.</p>
     *
     * <p>Formula: the larger of
     * <ul>
     *   <li>progress of the current operation in percent * 15 (0 when idle), and</li>
     *   <li>fill ratio of the seed slot * 15 (an empty machine reads 0, a full one reads 15).</li>
     * </ul>
     * A completely filled seed slot therefore pins the output at 15 ("there is work available"); the
     * progress curve is what you read while the slot is only partly filled.</p>
     */
    @Override
    public int getRedstoneLevel() {
        if (!this.supportsComparator()) {
            return 0;
        }
        double progressFraction = this.ticksRequired > 0 ? this.progress / (double) this.ticksRequired : 0.0D;
        int progressLevel = (int) Math.round(Math.max(0.0D, Math.min(1.0D, progressFraction)) * 15.0D);
        int capacity = Math.max(1, this.getSeedCapacity());
        int stored = this.seedSlot == null ? 0 : this.seedSlot.getStoredCount();
        int seedLevel = (int) Math.round(Math.max(0.0D, Math.min(1.0D, stored / (double) capacity)) * 15.0D);
        return Math.max(0, Math.min(15, Math.max(progressLevel, seedLevel)));
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
        // 1.21.1 hands out RecipeHolders instead of bare recipes.
        for (RecipeHolder<CrystalMultiplicationRecipe> holder
                : this.level.getRecipeManager().getAllRecipesFor(CrystalMultiplicationRecipe.TYPE)) {
            CrystalMultiplicationRecipe recipe = holder.value();
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
        // With the AE output switch on, a connected network counts as free space, so the machine
        // keeps running even while every output slot is full.
        int remaining = amount - Ae2Compat.insert(this, result, amount, true);
        if (remaining <= 0) {
            return true;
        }
        for (CrystalSlot slot : this.outputSlots) {
            if (remaining <= 0) {
                break;
            }
            remaining = slot.insertInternal(result.copyWithCount(remaining), Action.SIMULATE).getCount();
        }
        return remaining <= 0;
    }

    /**
     * Moves one finished batch into the output slots.
     *
     * @return true when at least one item actually went in, which is what triggers the sound/particles
     */
    private boolean pushResult(ItemStack result, int amount) {
        // Products never touch the output slots when AE output is on and a network is reachable.
        int remaining = amount - Ae2Compat.insert(this, result, amount, false);
        for (CrystalSlot slot : this.outputSlots) {
            if (remaining <= 0) {
                break;
            }
            remaining = slot.insertInternal(result.copyWithCount(remaining), Action.EXECUTE).getCount();
        }
        this.setChanged();
        return remaining < amount;
    }

    /**
     * Audio feedback for a finished batch: one quiet, rate limited chime in front of the block.
     *
     * <p>The vanilla particle puff that used to run here was removed on request: on a wall of
     * machines the sparks covered the products and cost one particle packet per batch. The chime is
     * rate limited to one every {@link #EJECT_SOUND_INTERVAL_TICKS} ticks and only plays when a real
     * item just left the output slots.</p>
     *
     * <p>1.21.1 notes: {@code Level#playSound(Player, double, double, double, SoundEvent, SoundSource,
     * float, float)} is what the vanilla code uses to broadcast the packet from the server, so nearby
     * clients still hear it. The client copy of the tile never reaches this method.</p>
     */
    private void playEjectChime() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        Direction facing = this.getDirection();
        // Middle of the front face, nudged a little outwards so the chime is placed at the output side.
        double x = this.worldPosition.getX() + 0.5D + facing.getStepX() * EJECT_FRONT_OFFSET;
        double y = this.worldPosition.getY() + 0.5D + facing.getStepY() * EJECT_FRONT_OFFSET;
        double z = this.worldPosition.getZ() + 0.5D + facing.getStepZ() * EJECT_FRONT_OFFSET;
        long gameTime = serverLevel.getGameTime();
        if (gameTime - this.lastEjectSoundTick >= EJECT_SOUND_INTERVAL_TICKS) {
            this.lastEjectSoundTick = gameTime;
            serverLevel.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    EJECT_SOUND_VOLUME, EJECT_SOUND_PITCH);
        }
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
    public @NotNull IUpgradeData getUpgradeData(@NotNull HolderLookup.Provider provider) {
        if (this.configComponent == null) {
            this.setupComponents();
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Energy", this.energyContainer.serializeNBT(provider));
        ListTag slots = new ListTag();
        for (CrystalSlot slot : this.getMachineSlots()) {
            slots.add(slot.serializeNBT(provider));
        }
        tag.put("Slots", slots);
        CompoundTag components = new CompoundTag();
        for (ITileComponent component : this.getComponents()) {
            component.write(components, provider);
        }
        tag.put("Components", components);
        tag.putDouble("CrystalProgress", this.progress);
        tag.putInt("CrystalTicksRequired", this.ticksRequired);
        tag.putInt("CrystalEnergyPerTick", this.energyPerTick);
        tag.putInt("CrystalParallelism", this.parallelism);
        tag.putInt("CrystalItemsPerBatch", this.itemsPerBatch);
        tag.putBoolean("CrystalSeedValid", this.seedValid);
        tag.putBoolean("CrystalAeOutput", this.aeOutput);
        tag.putInt("ControlType", this.getControlType().ordinal());
        tag.putBoolean("Active", this.getActive());
        return new CrystalUpgradeData(tag);
    }

    @Override
    public void parseUpgradeData(@NotNull HolderLookup.Provider provider, @NotNull IUpgradeData data) {
        if (!(data instanceof CrystalUpgradeData upgrade)) {
            super.parseUpgradeData(provider, data);
            return;
        }
        CompoundTag tag = upgrade.getTag();
        this.progress = tag.getDouble("CrystalProgress");
        this.ticksRequired = tag.getInt("CrystalTicksRequired");
        this.energyPerTick = tag.getInt("CrystalEnergyPerTick");
        this.parallelism = tag.getInt("CrystalParallelism");
        this.itemsPerBatch = tag.getInt("CrystalItemsPerBatch");
        this.seedValid = tag.getBoolean("CrystalSeedValid");
        this.aeOutput = tag.getBoolean("CrystalAeOutput");
        if (tag.contains("ControlType", Tag.TAG_INT)) {
            this.setControlType(IRedstoneControl.RedstoneControl.BY_ID.apply(tag.getInt("ControlType")));
        }
        if (tag.contains("Energy", Tag.TAG_COMPOUND)) {
            this.energyContainer.deserializeNBT(provider, tag.getCompound("Energy"));
        }
        ListTag slots = tag.getList("Slots", Tag.TAG_COMPOUND);
        List<CrystalSlot> machine = this.getMachineSlots();
        for (int i = 0; i < Math.min(slots.size(), machine.size()); i++) {
            machine.get(i).deserializeNBT(provider, slots.getCompound(i));
        }
        if (this.configComponent == null) {
            this.setupComponents();
        }
        CompoundTag components = tag.getCompound("Components");
        for (ITileComponent component : this.getComponents()) {
            component.read(components, provider);
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
        container.track(SyncableBoolean.create(() -> this.aeOutput, value -> this.aeOutput = value));
        // The hidden overflow of the crystal slots lives outside the ItemStack, so it has to be
        // pushed to the client explicitly for the GUI to show the real stored amounts.
        for (CrystalSlot slot : this.getMachineSlots()) {
            if (slot != null) {
                container.track(SyncableInt.create(slot::getStoredCount, slot::setSyncedStoredCount));
            }
        }
    }

    /* ----------------------------------------------------------------- saving */

    @Override
    public void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putDouble("CrystalProgress", this.progress);
        tag.putInt("CrystalTicksRequired", this.ticksRequired);
        tag.putInt("CrystalEnergyPerTick", this.energyPerTick);
        tag.putInt("CrystalParallelism", this.parallelism);
        tag.putInt("CrystalItemsPerBatch", this.itemsPerBatch);
        tag.putBoolean("CrystalSeedValid", this.seedValid);
        tag.putBoolean("CrystalAeOutput", this.aeOutput);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.progress = tag.getDouble("CrystalProgress");
        this.ticksRequired = tag.getInt("CrystalTicksRequired");
        this.energyPerTick = tag.getInt("CrystalEnergyPerTick");
        this.parallelism = tag.getInt("CrystalParallelism");
        this.itemsPerBatch = tag.getInt("CrystalItemsPerBatch");
        this.seedValid = tag.getBoolean("CrystalSeedValid");
        this.aeOutput = tag.getBoolean("CrystalAeOutput");
    }
}
