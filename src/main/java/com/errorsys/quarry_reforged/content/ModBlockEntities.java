package com.errorsys.quarry_reforged.content;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.content.blockentity.QuarryMarkerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
    public static BlockEntityType<QuarryBlockEntity> QUARRY;
    public static BlockEntityType<QuarryMarkerBlockEntity> QUARRY_MARKER;

    private ModBlockEntities() {}

    public static void register() {
        QUARRY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(QuarryReforged.MOD_ID, "quarry"),
                FabricBlockEntityTypeBuilder.create(QuarryBlockEntity::new, ModBlocks.QUARRY).build()
        );
        QUARRY_MARKER = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(QuarryReforged.MOD_ID, "quarry_marker"),
                FabricBlockEntityTypeBuilder.create(QuarryMarkerBlockEntity::new, ModBlocks.MARKER).build()
        );
    }
}
