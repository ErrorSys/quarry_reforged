package com.errorsys.quarry_reforged.util;

import com.errorsys.quarry_reforged.content.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import team.reborn.energy.api.EnergyStorage;

public final class ApiRegistrations {
    private ApiRegistrations() {}

    public static void register() {
        EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energy, ModBlockEntities.QUARRY);
        ItemStorage.SIDED.registerForBlockEntity((be, dir) -> be.getSidedStorage(dir), ModBlockEntities.QUARRY);
    }
}
