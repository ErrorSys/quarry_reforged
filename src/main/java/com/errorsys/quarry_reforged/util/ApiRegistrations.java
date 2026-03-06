package com.errorsys.quarry_reforged.util;

import com.errorsys.quarry_reforged.content.ModBlockEntities;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;

import java.util.Collections;
import java.util.Iterator;

public final class ApiRegistrations {
    private ApiRegistrations() {}

    public static void register() {
        EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energy, ModBlockEntities.QUARRY);
        ItemStorage.SIDED.registerForBlockEntity((be, dir) -> new ExtractOnlyStorage(be.getOutputInventory()), ModBlockEntities.QUARRY);
    }

    private record ExtractOnlyStorage(Inventory inv) implements Storage<ItemVariant> {

        @Override
            public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
                long extracted = 0;
                for (int i = 0; i < inv.size() && extracted < maxAmount; i++) {
                    ItemStack s = inv.getStack(i);
                    if (s.isEmpty()) continue;
                    if (!ItemVariant.of(s).equals(resource)) continue;
                    int take = (int) Math.min(maxAmount - extracted, s.getCount());
                    s.decrement(take);
                    extracted += take;
                }
                return extracted;
            }

            @Override
            public Iterator<StorageView<ItemVariant>> iterator() {
                return Collections.emptyIterator();
            }
        }
}
