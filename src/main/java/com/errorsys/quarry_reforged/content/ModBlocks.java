package com.errorsys.quarry_reforged.content;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.content.block.QuarryBlock;
import com.errorsys.quarry_reforged.content.block.QuarryFrameBlock;
import com.errorsys.quarry_reforged.content.block.QuarryMarkerBlock;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static Block QUARRY;
    public static Block MARKER;
    public static Block FRAME;

    public static final ItemGroup ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(ModBlocks.QUARRY))
            .displayName(Text.translatable("itemGroup.quarry_reforged"))
            .entries((ctx, entries) -> {
                entries.add(ModBlocks.QUARRY);
                entries.add(ModBlocks.MARKER);
                entries.add(ModBlocks.FRAME);
                entries.add(ModItems.SPEED_UPGRADE);
                entries.add(ModItems.FORTUNE_UPGRADE_1);
                entries.add(ModItems.FORTUNE_UPGRADE_2);
                entries.add(ModItems.FORTUNE_UPGRADE_3);
                entries.add(ModItems.SILK_UPGRADE);
                entries.add(ModItems.CHUNKLOAD_UPGRADE);
                entries.add(ModItems.VOID_UPGRADE);
            })
            .build();

    private ModBlocks() {}

    public static void register() {
        QUARRY = registerBlock("quarry", new QuarryBlock());
        MARKER = registerBlock("quarry_marker", new QuarryMarkerBlock());
        FRAME = registerBlock("quarry_frame", new QuarryFrameBlock());
        Registry.register(Registries.ITEM_GROUP, new Identifier(QuarryReforged.MOD_ID, "main"), ITEM_GROUP);
    }

    private static Block registerBlock(String path, Block block) {
        Identifier id = new Identifier(QuarryReforged.MOD_ID, path);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }
}
