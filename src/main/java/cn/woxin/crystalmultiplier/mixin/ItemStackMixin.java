package cn.woxin.crystalmultiplier.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft 1.20.1 stores the item count as a byte in NBT and in the network protocol, which caps
 * every ItemStack at 127 items. The in-memory field is a full int, so the machine's large slots can
 * hold more if the count survives a save/load or a packet round trip.
 *
 * <p>The mixin writes an extra int tag for large stacks and restores it after the vanilla
 * deserialization. It deliberately leaves {@link ItemStack#getMaxStackSize()} alone: machine slots
 * opt into large stacks through their own slot implementation, while player inventory slots keep
 * the vanilla 64 item limit.</p>
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    private static final String CRYSTAL_COUNT = "CrystalCount";

    @Inject(method = "m_41739_", at = @At("RETURN"), remap = false)
    private void crystal$saveLargeCount(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        int count = ((ItemStack) (Object) this).getCount();
        if (count > 127) {
            result.putInt(CRYSTAL_COUNT, count);
            // Keep the vanilla byte sane for third party readers instead of storing a negative
            // or zero truncated value.
            result.putByte("Count", (byte) Math.min(count, ((ItemStack) (Object) this).getMaxStackSize()));
        } else {
            result.remove(CRYSTAL_COUNT);
        }
    }

    @Inject(method = "m_41712_", at = @At("RETURN"), cancellable = true, remap = false)
    private static void crystal$loadLargeCount(CompoundTag tag, CallbackInfoReturnable<ItemStack> cir) {
        int count = tag.getInt(CRYSTAL_COUNT);
        if (count <= 127) {
            return;
        }
        ItemStack stack = cir.getReturnValue();
        if (stack == null || stack.isEmpty()) {
            // Vanilla stores Count as a byte, so counts that are multiples of 256 (or otherwise
            // truncate to zero) make ItemStack.of return EMPTY. Rebuild the stack from the id.
            if (!tag.contains("id", 8)) {
                return;
            }
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("id")));
            if (item == Items.AIR) {
                return;
            }
            stack = new ItemStack(item, count);
            if (tag.contains("tag", 10)) {
                stack.setTag(tag.getCompound("tag"));
            }
            cir.setReturnValue(stack);
        } else {
            stack.setCount(count);
        }
    }

}
