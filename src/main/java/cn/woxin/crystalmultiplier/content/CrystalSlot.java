package cn.woxin.crystalmultiplier.content;

import java.util.function.IntSupplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A Mekanism machine slot that can hold more than one ItemStack worth of items.
 *
 * <p>Vanilla 1.20.1 stores an item count in a signed byte, so the mod bundles small ItemStack and
 * FriendlyByteBuf mixins to keep large counts in NBT and on the wire. A visible stack can therefore
 * carry the full tier capacity; {@link #MAX_VISIBLE} is only the absolute fallback used if a future
 * code path ever needs a hidden overflow above 4096.</p>
 */
public class CrystalSlot extends BasicInventorySlot {

    /** Absolute safety ceiling for one visible stack, well above every tier capacity. */
    public static final int MAX_VISIBLE = 4096;

    private final IntSupplier capacity;
    private final boolean output;
    private int extra;

    private CrystalSlot(IntSupplier capacity, boolean output, @Nullable IContentsListener listener, int x, int y) {
        super(MAX_VISIBLE,
                output ? alwaysTrueBi : notExternal,
                output ? (stack, type) -> false : alwaysTrueBi,
                alwaysTrue,
                listener, x, y);
        this.capacity = capacity;
        this.output = output;
        // The slot handles its own limits: getLimit is a byte safe view, capacity is the real limit.
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
        // The bundled ItemStack mixin lets one visible stack hold the full tier capacity.
        return this.getCapacity();
    }

    private ItemStack insert(ItemStack stack, Action action) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (!this.isEmpty() && !ItemStack.isSameItemSameTags(this.current, stack)) {
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
    protected void setStackUnchecked(ItemStack stack) {
        if (stack.isEmpty()) {
            this.write(ItemStack.EMPTY, 0);
            return;
        }
        // Deserialization passes the real count through ItemStack#SizeOverride. Only a byte safe
        // incoming stack may be a plain visible stack; in that case keep an already present
        // overflow when the same item is set again by vanilla slot code. A count above the item
        // limit is a serialized total and must replace the overflow instead of being added to it.
        int incoming = stack.getCount();
        int vanillaLimit = stack.getItem().getMaxStackSize();
        int kept = incoming <= vanillaLimit && ItemStack.isSameItemSameTags(this.current, stack)
                ? this.extra : 0;
        this.write(stack, incoming + kept);
    }

    private void write(ItemStack prototype, int total) {
        int clamped = Math.max(0, Math.min(this.getCapacity(), total));
        if (clamped <= 0 || prototype.isEmpty()) {
            this.current = ItemStack.EMPTY;
            this.extra = 0;
        } else {
            int visible = Math.min(clamped, MAX_VISIBLE);
            this.current = prototype.copyWithCount(visible);
            this.extra = clamped - visible;
        }
        this.onContentsChanged();
    }
}
