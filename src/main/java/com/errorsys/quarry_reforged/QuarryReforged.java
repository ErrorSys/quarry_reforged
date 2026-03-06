package com.errorsys.quarry_reforged;

import com.errorsys.quarry_reforged.config.ModConfig;
import com.errorsys.quarry_reforged.command.ModCommands;
import com.errorsys.quarry_reforged.content.ModBlockEntities;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.ModItems;
import com.errorsys.quarry_reforged.content.ModScreenHandlers;
import com.errorsys.quarry_reforged.net.ModNetworking;
import com.errorsys.quarry_reforged.util.ApiRegistrations;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class
QuarryReforged implements ModInitializer {
    public static final String MOD_ID = "quarry_reforged";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Quarry Reforged initializing...");
        ModConfig.loadOrCreate();
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModScreenHandlers.register();
        ModNetworking.registerServer();
        ApiRegistrations.register();
        ModCommands.register();
        LOGGER.info("Quarry Reforged initialized.");
    }
}
