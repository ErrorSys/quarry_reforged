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
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class QuarryScreenHandler extends ScreenHandler {
    public static final int GUI_WIDTH = 230;
    public static final int GUI_HEIGHT = 282;
    public static final int OUTPUT_ROWS = 2;
    public static final int UPGRADE_SLOTS = 8;
    // Final live slot positions. Update these after you finish previewing the layout in QuarryScreen.
    public static final int UPGRADE_X = 15;
    public static final int UPGRADE_Y = 75;
    public static final int OUTPUT_X = 15;
    public static final int OUTPUT_Y = 116;
    public static final int PLAYER_INV_X = 35;
    public static final int PLAYER_INV_Y = 181;
    public static final int HOTBAR_X = 35;
    public static final int HOTBAR_Y = 251;
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

        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            this.addSlot(new Slot(upg, i, UPGRADE_X + i * 18, UPGRADE_Y) {
                @Override public boolean canInsert(ItemStack stack) { return quarry.isValidUpgrade(stack); }
                @Override public int getMaxItemCount() { return 1; }
            });
        }

        int idx = 0;
        for (int r = 0; r < OUTPUT_ROWS; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(out, idx++, OUTPUT_X + c * 18, OUTPUT_Y + r * 18) {
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                });
            }
        }

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

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();

            int upgradesStart = 0;
            int upgradesEnd = UPGRADE_SLOTS;
            int outputStart = upgradesEnd;
            int outputEnd = outputStart + (9 * OUTPUT_ROWS);
            int playerStart = outputEnd;
            int playerEnd = this.slots.size();

            if (index >= outputStart && index < outputEnd) {
                if (!this.insertItem(original, playerStart, playerEnd, true)) return ItemStack.EMPTY;
            } else if (index >= playerStart) {
                if (quarry.isValidUpgrade(original)) {
                    if (!this.insertItem(original, upgradesStart, upgradesEnd, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            } else if (index >= upgradesStart && index < upgradesEnd) {
                if (!this.insertItem(original, playerStart, playerEnd, true)) return ItemStack.EMPTY;
            }

            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}
