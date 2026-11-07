package cn.woxin.crystalmultiplier.content;

import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/** Upgrade payload used by Mekanism's tier installer; keeps the machine contents intact. */
public class CrystalUpgradeData implements IUpgradeData {

    private final CompoundTag tag;

    public CrystalUpgradeData(@NotNull CompoundTag tag) {
        this.tag = tag.copy();
    }

    @NotNull
    public CompoundTag getTag() {
        return this.tag;
    }
}
