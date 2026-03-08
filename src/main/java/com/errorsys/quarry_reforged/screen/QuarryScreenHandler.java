package com.errorsys.quarry_reforged.screen;

import com.errorsys.quarry_reforged.content.ModScreenHandlers;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class QuarryScreenHandler extends ScreenHandler {
    public static final int GUI_WIDTH = 230;
    public static final int GUI_HEIGHT = 282;
    public static final int OUTPUT_ROWS = 2;
    public static final int UPGRADE_SLOTS = 8;
    // Final live slot positions. Update these after you finish previewing the layout in QuarryScreen.
    public static final int UPGRADE_X = 22;
    public static final int UPGRADE_Y = 57;
    public static final int OUTPUT_X = 22;
    public static final int OUTPUT_Y = 95;
    public static final int ENERGY_ITEM_X = 191;
    public static final int ENERGY_ITEM_Y = 113;
    public static final int PLAYER_INV_X = (GUI_WIDTH - (9 * 18)) / 2;
    public static final int PLAYER_INV_Y = 158;
    public static final int HOTBAR_X = (GUI_WIDTH - (9 * 18)) / 2 + 1;
    public static final int HOTBAR_Y = 225;
    private boolean machineSlotsVisible = true;
    public final QuarryBlockEntity quarry;
    public final PropertyDelegate props;
    public final BlockPos pos;

    public QuarryScreenHandler(int syncId, PlayerInventory playerInv, PacketByteBuf buf) {
        this(syncId, playerInv, (QuarryBlockEntity) playerInv.player.getWorld().getBlockEntity(buf.readBlockPos()));
    }

    public QuarryScreenHandler(int syncId, PlayerInventory playerInv, QuarryBlockEntity quarry) {
        super(ModScreenHandlers.QUARRY, syncId);
        this.quarry = quarry;
        this.props = quarry.getPropertyDelegate();
        this.pos = quarry.getPos();

        Inventory out = quarry.getOutputInventory();
        Inventory upg = quarry.getUpgradeInventory();
        Inventory energyInv = quarry.getEnergyInventory();

        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            this.addSlot(new Slot(upg, i, UPGRADE_X + i * 18, UPGRADE_Y) {
                @Override public boolean canInsert(ItemStack stack) { return quarry.canInsertUpgrade(stack); }
                @Override public int getMaxItemCount() { return 1; }
                @Override public int getMaxItemCount(ItemStack stack) { return 1; }
                @Override public boolean isEnabled() { return machineSlotsVisible; }
                @Override public void setStack(ItemStack stack) {
                    if (!stack.isEmpty() && stack.getCount() > 1) {
                        ItemStack single = stack.copy();
                        single.setCount(1);
                        super.setStack(single);
                        return;
                    }
                    super.setStack(stack);
                }
            });
        }

        int idx = 0;
        for (int r = 0; r < OUTPUT_ROWS; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(out, idx++, OUTPUT_X + c * 18, OUTPUT_Y + r * 18) {
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                    @Override public boolean isEnabled() { return machineSlotsVisible; }
                });
            }
        }

        this.addSlot(new Slot(energyInv, 0, ENERGY_ITEM_X, ENERGY_ITEM_Y) {
            @Override public boolean canInsert(ItemStack stack) { return quarry.isValidEnergyItem(stack); }
            @Override public int getMaxItemCount() { return 1; }
            @Override public int getMaxItemCount(ItemStack stack) { return 1; }
            @Override public boolean isEnabled() { return machineSlotsVisible; }
            @Override public void setStack(ItemStack stack) {
                if (!stack.isEmpty() && stack.getCount() > 1) {
                    ItemStack single = stack.copy();
                    single.setCount(1);
                    super.setStack(single);
                    return;
                }
                super.setStack(stack);
            }
        });

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(playerInv, c, HOTBAR_X + c * 18, HOTBAR_Y));
        }

        this.addProperties(props);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return quarry != null && quarry.canPlayerAccess(player);
    }

    public void setMachineSlotsVisible(boolean visible) {
        this.machineSlotsVisible = visible;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot sourceSlot = this.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasStack()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getStack();
        ItemStack movedStack = sourceStack.copy();

        int upgradesStart = 0;
        int upgradesEnd = UPGRADE_SLOTS;
        int outputStart = upgradesEnd;
        int outputEnd = outputStart + (9 * OUTPUT_ROWS);
        int energySlot = outputEnd;
        int playerStart = energySlot + 1;
        int playerMainEnd = playerStart + 27;
        int playerEnd = this.slots.size();

        boolean moved = false;
        if (index >= outputStart && index < outputEnd) {
            moved = this.insertItem(sourceStack, playerStart, playerEnd, true);
        } else if (index == energySlot) {
            moved = this.insertItem(sourceStack, playerStart, playerEnd, true);
        } else if (index >= upgradesStart && index < upgradesEnd) {
            moved = this.insertItem(sourceStack, playerStart, playerEnd, true);
        } else if (index >= playerStart && index < playerEnd) {
            if (quarry.isValidEnergyItem(sourceStack)) {
                moved |= this.insertItem(sourceStack, energySlot, energySlot + 1, false);
            }
            if (!sourceStack.isEmpty() && quarry.isValidUpgrade(sourceStack)) {
                moved |= distributeUpgradesOnePerSlot(sourceStack, upgradesStart, upgradesEnd);
            }
            if (!moved) {
                if (index < playerMainEnd) {
                    moved = this.insertItem(sourceStack, playerMainEnd, playerEnd, false);
                } else {
                    moved = this.insertItem(sourceStack, playerStart, playerMainEnd, false);
                }
            }
        }

        if (!moved) return ItemStack.EMPTY;

        if (sourceStack.isEmpty()) {
            sourceSlot.setStack(ItemStack.EMPTY);
        } else {
            sourceSlot.markDirty();
        }
        if (sourceStack.getCount() == movedStack.getCount()) return ItemStack.EMPTY;
        sourceSlot.onTakeItem(player, sourceStack);
        this.sendContentUpdates();
        return movedStack;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        super.onSlotClick(slotIndex, button, actionType, player);
        this.sendContentUpdates();
    }

    private boolean distributeUpgradesOnePerSlot(ItemStack sourceStack, int startInclusive, int endExclusive) {
        if (sourceStack.isEmpty() || !quarry.isValidUpgrade(sourceStack)) return false;

        boolean moved = false;
        for (int i = startInclusive; i < endExclusive && !sourceStack.isEmpty(); i++) {
            if (!quarry.canInsertUpgrade(sourceStack)) break;
            Slot target = this.slots.get(i);
            if (target.hasStack()) continue;
            if (!target.canInsert(sourceStack)) continue;

            target.setStack(sourceStack.split(1));
            target.markDirty();
            moved = true;
        }
        return moved;
    }
}
