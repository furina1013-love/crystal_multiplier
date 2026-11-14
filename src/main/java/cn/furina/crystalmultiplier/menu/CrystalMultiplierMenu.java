package cn.furina.crystalmultiplier.menu;

import cn.furina.crystalmultiplier.CrystalMultiplierMod;
import cn.furina.crystalmultiplier.content.CrystalMultiplierBlockEntity;
import cn.furina.crystalmultiplier.content.CrystalSlot;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container for the crystal multiplier. The machine slots are Mekanism inventory slots backed by
 * CrystalSlot, whose visible stack can hold the full tier capacity (thanks to the ItemStack mixin).
 * The output branch below still drains a slot completely in one shift click so large stacks are
 * never split into several clicks.
 */
public class CrystalMultiplierMenu extends MekanismTileContainer<CrystalMultiplierBlockEntity> {

    public CrystalMultiplierMenu(int id, Inventory inventory, CrystalMultiplierBlockEntity tile) {
        super(CrystalMultiplierMod.MULTIPLIER_CONTAINER, id, inventory, tile);
    }

    public List<CrystalSlot> getMachineSlots() {
        return this.tile.getMachineSlots();
    }

    /** Index of the first machine slot in {@link #slots}. Works for both the server and the client. */
    public int getMachineSlotStart() {
        return this.slots.size() - 36 - this.getMachineSlots().size();
    }

    public int getStoredCount(int machineIndex) {
        List<CrystalSlot> machineSlots = this.getMachineSlots();
        return machineIndex >= 0 && machineIndex < machineSlots.size() ? machineSlots.get(machineIndex).getStoredCount() : 0;
    }

    public int getSlotCapacity(int machineIndex) {
        List<CrystalSlot> machineSlots = this.getMachineSlots();
        return machineIndex >= 0 && machineIndex < machineSlots.size() ? machineSlots.get(machineIndex).getCapacity() : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        int machineStart = this.getMachineSlotStart();
        List<CrystalSlot> machineSlots = this.getMachineSlots();
        int machineIndex = index - machineStart;
        if (machineIndex >= 0 && machineIndex < machineSlots.size()) {
            CrystalSlot machineSlot = machineSlots.get(machineIndex);
            if (machineSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return this.drainToPlayer(player, this.slots.get(index), machineSlot);
        }
        // Player -> machine: Mekanism's slot implementation delegates to CrystalSlot#insertItem.
        return super.m_7648_(player, index);
    }

    /**
     * Moves the whole stored amount into the player inventory. The loop keeps going until the slot
     * is empty or the player inventory is full, so a shift click empties output slots holding 512
     * items too.
     */
    private ItemStack drainToPlayer(Player player, Slot containerSlot, CrystalSlot machineSlot) {
        ItemStack movedTotal = ItemStack.EMPTY;
        int playerStart = this.slots.size() - 36;
        while (!machineSlot.isEmpty()) {
            ItemStack extracted = machineSlot.extractItem(CrystalSlot.MAX_VISIBLE, Action.SIMULATE, AutomationType.MANUAL);
            if (extracted.isEmpty()) {
                break;
            }
            int before = extracted.getCount();
            this.moveItemStackTo(extracted, playerStart, this.slots.size(), true);
            int moved = before - extracted.getCount();
            if (moved <= 0) {
                break;
            }
            ItemStack moving = extracted.copyWithCount(moved);
            machineSlot.extractItem(moved, Action.EXECUTE, AutomationType.MANUAL);
            containerSlot.onTake(player, moving.copy());
            if (movedTotal.isEmpty()) {
                movedTotal = moving;
            } else {
                movedTotal.grow(moved);
            }
        }
        return movedTotal;
    }
}
