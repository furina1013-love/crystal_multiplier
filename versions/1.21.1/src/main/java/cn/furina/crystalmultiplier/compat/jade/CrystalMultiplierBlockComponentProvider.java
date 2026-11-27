package cn.furina.crystalmultiplier.compat.jade;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;

/**
 * Jade tooltip for every crystal multiplier tier.
 *
 * <p>Implements both sides of the Jade API so the numbers are the real ones: the server data provider
 * copies tier, parallel lanes, seed count, running state and progress into the tag Jade ships to the
 * client ({@link #appendServerData}), and the block component provider renders them ({@link #appendTooltip}).
 * When no server data is available (for example when the world is not backed by our server side) it
 * falls back to the client block entity, and when neither exists it adds nothing at all.</p>
 */
public class CrystalMultiplierBlockComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(CrystalMultiplierMod.MODID, "crystal_multiplier");

    private static final String TAG_TIER = "CrystalTier";
    private static final String TAG_PARALLEL = "CrystalParallel";
    private static final String TAG_SEEDS = "CrystalSeeds";
    private static final String TAG_SEED_CAPACITY = "CrystalSeedCapacity";
    private static final String TAG_RUNNING = "CrystalRunning";
    private static final String TAG_PROGRESS = "CrystalProgress";

    private static final String TIER_KEY_PREFIX = "tier.crystal_multiplier.";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof CrystalMultiplierBlockEntity machine)) {
            return;
        }
        data.putString(TAG_TIER, machine.getTier().getSerializedName());
        data.putInt(TAG_PARALLEL, machine.getParallelism());
        data.putInt(TAG_SEEDS, machine.getSeedSlot() == null ? 0 : machine.getSeedSlot().getStoredCount());
        data.putInt(TAG_SEED_CAPACITY, machine.getSeedCapacity());
        data.putBoolean(TAG_RUNNING, machine.getActive());
        data.putFloat(TAG_PROGRESS, (float) machine.getScaledProgress());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CrystalMultiplierBlockEntity machine = accessor.getBlockEntity() instanceof CrystalMultiplierBlockEntity be
                ? be : null;
        CompoundTag data = accessor.getServerData();

        String tier;
        int parallel;
        int seeds;
        int seedCapacity;
        boolean running;
        float progress;
        if (data != null && data.contains(TAG_TIER)) {
            tier = data.getString(TAG_TIER);
            parallel = data.getInt(TAG_PARALLEL);
            seeds = data.getInt(TAG_SEEDS);
            seedCapacity = data.getInt(TAG_SEED_CAPACITY);
            running = data.getBoolean(TAG_RUNNING);
            progress = data.getFloat(TAG_PROGRESS);
        } else if (machine != null) {
            tier = machine.getTier().getSerializedName();
            parallel = machine.getParallelism();
            seeds = machine.getSeedSlot() == null ? 0 : machine.getSeedSlot().getStoredCount();
            seedCapacity = machine.getSeedCapacity();
            running = machine.getActive();
            progress = (float) machine.getScaledProgress();
        } else {
            return;
        }

        Component runningText = Component.translatable(running ? "gui.yes" : "gui.no");
        int percent = Math.max(0, Math.min(100, Math.round(progress * 100.0F)));

        tooltip.add(Component.translatable("jade.crystal_multiplier.info",
                Component.translatable(TIER_KEY_PREFIX + tier), parallel));
        // Existing key of the machine GUI: "Seeds: %s / %s" / "种子: %s / %s".
        tooltip.add(Component.translatable("gui.crystal_multiplier.seed_stored", seeds, seedCapacity));
        tooltip.add(Component.translatable("jade.crystal_multiplier.status", runningText, percent));
    }
}
