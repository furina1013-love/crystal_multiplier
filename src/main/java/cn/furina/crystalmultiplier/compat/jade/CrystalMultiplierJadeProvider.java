package cn.furina.crystalmultiplier.compat.jade;

import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.content.CrystalTier;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade block component for every crystal multiplier tier.
 *
 * <p>It is both the client side tooltip provider and the server side data provider, the same
 * pattern Jade's own furnace provider uses: the server collects the machine state into the
 * accessor's compound tag and the client renders it. If the tag is missing (for example a very
 * old Jade or a single player edge case) the client falls back to the local block entity.</p>
 */
public enum CrystalMultiplierJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    public static final ResourceLocation UID =
            new ResourceLocation("crystal_multiplier", "crystal_multiplier");

    private static final String KEY_TIER = "CrystalMultiplierTier";
    private static final String KEY_PARALLELISM = "CrystalMultiplierParallelism";
    private static final String KEY_SEEDS = "CrystalMultiplierSeeds";
    private static final String KEY_RUNNING = "CrystalMultiplierRunning";
    private static final String KEY_PROGRESS = "CrystalMultiplierProgress";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof CrystalMultiplierBlockEntity multiplier)) {
            return;
        }
        CrystalTier tier = multiplier.getTier();
        data.putString(KEY_TIER, tier.getSerializedName());
        data.putInt(KEY_PARALLELISM, multiplier.getParallelism());
        data.putInt(KEY_SEEDS, multiplier.getSeedSlot() == null ? 0 : multiplier.getSeedSlot().getStoredCount());
        data.putBoolean(KEY_RUNNING, multiplier.getActive());
        data.putFloat(KEY_PROGRESS, (float) multiplier.getScaledProgress());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        boolean fromServer = data != null && data.contains(KEY_TIER);

        String tier;
        int parallelism;
        int seeds;
        boolean running;
        float progress;

        if (fromServer) {
            tier = data.getString(KEY_TIER);
            parallelism = data.getInt(KEY_PARALLELISM);
            seeds = data.getInt(KEY_SEEDS);
            running = data.getBoolean(KEY_RUNNING);
            progress = data.getFloat(KEY_PROGRESS);
        } else if (accessor.getBlockEntity() instanceof CrystalMultiplierBlockEntity multiplier) {
            // Fallback for the rare case the server tag is not present (e.g. an older Jade).
            tier = multiplier.getTier().getSerializedName();
            parallelism = multiplier.getParallelism();
            seeds = multiplier.getSeedSlot() == null ? 0 : multiplier.getSeedSlot().getStoredCount();
            running = multiplier.getActive();
            progress = (float) multiplier.getScaledProgress();
        } else {
            return;
        }

        tooltip.add(Component.translatable("jade.crystal_multiplier.info",
                Component.translatable("tier.crystal_multiplier." + tier), parallelism));
        tooltip.add(Component.translatable("jade.crystal_multiplier.status",
                Component.translatable(running ? "gui.yes" : "gui.no"),
                String.format(Locale.ROOT, "%.0f", Math.max(0.0F, Math.min(1.0F, progress)) * 100.0F)));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
