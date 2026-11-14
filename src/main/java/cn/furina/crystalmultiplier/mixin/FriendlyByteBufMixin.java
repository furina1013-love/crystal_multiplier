package cn.furina.crystalmultiplier.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla writes the item count as a single byte in the item packet format, which caps every
 * ItemStack at 255/127 items. Replace only the count read/write inside writeItemStack/readItem with
 * a VarInt so large stacks survive a container sync. (writeItem is a thin wrapper around the
 * Forge-added writeItemStack method, so the redirect has to target the latter.)
 *
 * <p>The redirect is deliberately limited to {@code writeItemStack}/{@code readItem}: the item id and
 * the optional tag are untouched, and the rest of the packet layout stays identical.</p>
 */
@Mixin(FriendlyByteBuf.class)
public class FriendlyByteBufMixin {

    /** Count read by the last readItem call, applied after vanilla built the ItemStack. */
    @Unique
    private int crystal$pendingCount;

    @Redirect(method = "writeItemStack", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;"))
    private ByteBuf crystal$writeCount(FriendlyByteBuf buf, int count) {
        return buf.writeVarInt(count);
    }

    @Inject(method = "m_130267_", at = @At("HEAD"), remap = false)
    private void crystal$resetRead(CallbackInfoReturnable<ItemStack> cir) {
        this.crystal$pendingCount = -1;
    }

    @Redirect(method = "m_130267_", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readByte()B"))
    private byte crystal$readCount(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.crystal$pendingCount = count;
        // An empty stack is encoded as count 0 and has no item id/tag after it. Returning 1
        // unconditionally would make vanilla try to read those missing bytes and desync the packet,
        // so only non-empty stacks get the placeholder count; the real value is applied at RETURN.
        return (byte) (count > 0 ? 1 : 0);
    }

    @Inject(method = "m_130267_", at = @At("RETURN"), remap = false)
    private void crystal$applyCount(CallbackInfoReturnable<ItemStack> cir) {
        int count = this.crystal$pendingCount;
        if (count > 0) {
            ItemStack stack = cir.getReturnValue();
            if (stack != null && !stack.isEmpty()) {
                stack.setCount(count);
            }
        }
    }
}
