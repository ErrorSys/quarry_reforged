package com.errorsys.quarry_reforged.content;

import com.errorsys.quarry_reforged.QuarryReforged;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static Item SPEED_UPGRADE;
    public static Item FORTUNE_UPGRADE_1;
    public static Item FORTUNE_UPGRADE_2;
    public static Item FORTUNE_UPGRADE_3;
    public static Item SILK_UPGRADE;
    public static Item CHUNKLOAD_UPGRADE;
    public static Item VOID_UPGRADE;

    private ModItems() {}

    public static void register() {
        SPEED_UPGRADE = reg("speed_upgrade", new Item(new Item.Settings().maxCount(16)));
        FORTUNE_UPGRADE_1 = reg("fortune_upgrade_1", new Item(new Item.Settings().maxCount(1)));
        FORTUNE_UPGRADE_2 = reg("fortune_upgrade_2", new Item(new Item.Settings().maxCount(1)));
        FORTUNE_UPGRADE_3 = reg("fortune_upgrade_3", new Item(new Item.Settings().maxCount(1)));
        SILK_UPGRADE = reg("silk_upgrade", new Item(new Item.Settings().maxCount(1)));
        CHUNKLOAD_UPGRADE = reg("chunkload_upgrade", new Item(new Item.Settings().maxCount(1)));
        VOID_UPGRADE = reg("void_upgrade", new Item(new Item.Settings().maxCount(1)));
    }

    private static Item reg(String path, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(QuarryReforged.MOD_ID, path), item);
    }
}
