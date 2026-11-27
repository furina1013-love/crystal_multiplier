package cn.furina.crystalmultiplier.content;

import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Mekanism machine slot that can hold more than one ItemStack worth of items.
 *
 * <p>1.21.1 stores an item count in a plain int on the wire and in the components, so the
 * 1.20.1 ItemStack/FriendlyByteBuf mixins are gone. Two things still limit a single
 * {@link ItemStack}: its own {@code DataComponents.MAX_STACK_SIZE} (64 by default) and the size
 * guard of the vanilla ItemStack codec. A visible stack is therefore never bigger than the item's
 * own stack limit; everything above it is kept in {@link #extra} and serialised next to the stack
 * in this slot's own NBT.</p>
 *
 * <p>The GUI reads {@link #getStoredCount()} through the container trackers of
 * {@code CrystalMultiplierBlockEntity}, so the hidden overflow is synced to the client.</p>
 */
public class CrystalSlot extends BasicInventorySlot {

    /** Absolute safety ceiling for the visible stack; real visibility follows the item limit. */
    public static final int MAX_VISIBLE = 64;

    /** NBT key of the hidden overflow, next to the vanilla stack tag. */
    private static final String EXTRA_KEY = "CrystalExtra";

    /** Output slots are never filled by players or pipes. */
    private static final BiPredicate<ItemStack, AutomationType> NEVER_INSERT = (stack, type) -> false;

    private final IntSupplier capacity;
    private final boolean output;
    private int extra;

    private CrystalSlot(IntSupplier capacity, boolean output, @Nullable IContentsListener listener, int x, int y) {
        super(MAX_VISIBLE,
                output
                        ? ConstantPredicates.<ItemStack, AutomationType>alwaysTrueBi()
                        : ConstantPredicates.<ItemStack>notExternal(),
                output
                        ? NEVER_INSERT
                        : ConstantPredicates.<ItemStack, AutomationType>alwaysTrueBi(),
                ConstantPredicates.<ItemStack>alwaysTrue(),
                listener, x, y);
        this.capacity = capacity;
        this.output = output;
        // The slot handles its own limits: getLimit is the real tier capacity, the stack itself
        // never grows past its own max stack size.
        this.obeyStackLimit = false;
        this.setSlotType(output ? ContainerSlotType.OUTPUT : ContainerSlotType.INPUT);
    }

    public static CrystalSlot seed(IntSupplier capacity, @Nullable IContentsListener listener, int x, int y) {
        return new CrystalSlot(capacity, false, listener, x, y);
    }

    public static CrystalSlot output(IntSupplier capacity, @Nullable IContentsListener listener, int x, int y) {
        return new CrystalSlot(capacity, true, listener, x, y);
    }

    public boolean isOutput() {
        return this.output;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        // Output slots are filled by the machine through insertInternal, never by players or pipes.
        return !this.output && super.isItemValid(stack);
    }

    /** Total amount stored in this slot: the visible ItemStack plus the hidden overflow. */
    public int getStoredCount() {
        return this.current.getCount() + this.extra;
    }

    /** Real capacity of this slot (64/128/256/512 for the machine tiers). */
    public int getCapacity() {
        return Math.max(1, this.capacity.getAsInt());
    }

    @Override
    public int getCount() {
        return this.getStoredCount();
    }

    @Override
    public int getLimit(ItemStack stack) {
        // getLimit is what pipes/hoppers use to size their insertion, the real limit is the tier.
        return this.getCapacity();
    }

    /** How many items may live inside one visible stack without breaking vanilla assumptions. */
    private int visibleLimit(ItemStack stack) {
        int limit = stack.isEmpty() ? MAX_VISIBLE : stack.getMaxStackSize();
        return Math.max(1, Math.min(MAX_VISIBLE, limit));
    }

    private ItemStack insert(ItemStack stack, Action action) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (!this.isEmpty() && !ItemStack.isSameItemSameComponents(this.current, stack)) {
            return stack;
        }
        int stored = this.getStoredCount();
        int space = this.getCapacity() - stored;
        if (space <= 0) {
            return stack;
        }
        int moved = Math.min(space, stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (action.execute()) {
            this.write(stack, stored + moved);
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(moved);
        return remainder;
    }

    @Override
    public ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
        // Output slots are filled by the machine itself through insertInternal.
        return this.output ? stack : this.insert(stack, action);
    }

    /** Machine internal insertion, allowed for output slots as well. */
    public ItemStack insertInternal(ItemStack stack, Action action) {
        return this.insert(stack, action);
    }

    @Override
    public ItemStack extractItem(int amount, Action action, AutomationType automationType) {
        if (this.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        // Pipes must not pull the seed back out of the machine; players and the machine itself may.
        if (!this.output && automationType == AutomationType.EXTERNAL) {
            return ItemStack.EMPTY;
        }
        int perStack = Math.max(1, this.current.getMaxStackSize());
        int moved = Math.min(Math.min(amount, perStack), this.getStoredCount());
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = this.current.copyWithCount(moved);
        if (action.execute()) {
            this.write(this.current, this.getStoredCount() - moved);
        }
        return result;
    }

    /**
     * Removes up to {@code amount} stored items in one go, without the per-stack extraction cap of
     * {@link #extractItem(int, Action, AutomationType)}. Used by the AE2 output so a full 4096 item
     * slot can leave the machine in a single tick.
     *
     * @return how many items were actually removed
     */
    public int shrinkStored(int amount) {
        int stored = this.getStoredCount();
        if (stored <= 0 || amount <= 0) {
            return 0;
        }
        int moved = Math.min(stored, amount);
        this.write(this.current, stored - moved);
        return moved;
    }

    @Override
    public void setStack(ItemStack stack) {
        if (stack.isEmpty()) {
            this.write(ItemStack.EMPTY, 0);
            return;
        }
        if (!this.isItemValid(stack)) {
            throw new RuntimeException("Invalid stack for crystal slot: " + stack);
        }
        this.write(stack, stack.getCount());
    }

    @Override
    public void setStackUnchecked(ItemStack stack) {
        if (stack.isEmpty()) {
            this.write(ItemStack.EMPTY, 0);
            return;
        }
        // Deserialisation hands the visible stack back; the overflow arrives separately in
        // deserializeNBT. Keeping a matching overflow here makes vanilla slot code that re-applies
        // the same stack idempotent instead of wiping the stored amount.
        int incoming = stack.getCount();
        int vanillaLimit = stack.getMaxStackSize();
        int kept = incoming <= vanillaLimit && ItemStack.isSameItemSameComponents(this.current, stack)
                ? this.extra : 0;
        this.write(stack, incoming + kept);
    }

    private void write(ItemStack prototype, int total) {
        int clamped = Math.max(0, Math.min(this.getCapacity(), total));
        if (clamped <= 0 || prototype.isEmpty()) {
            this.current = ItemStack.EMPTY;
            this.extra = 0;
        } else {
            int visible = Math.min(clamped, this.visibleLimit(prototype));
            this.current = prototype.copyWithCount(visible);
            this.extra = clamped - visible;
        }
        this.onContentsChanged();
    }

    /** Client side update of the stored total pushed by the container tracker of the tile. */
    public void setSyncedStoredCount(int total) {
        this.extra = Math.max(0, total - this.current.getCount());
    }

    @Override
    public @NotNull CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        if (this.extra > 0) {
            tag.putInt(EXTRA_KEY, this.extra);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        // Applied after the vanilla stack: the overflow is authoritative, the visible stack is only
        // the part vanilla is able to carry.
        this.extra = Math.max(0, tag.getInt(EXTRA_KEY));
    }

}
