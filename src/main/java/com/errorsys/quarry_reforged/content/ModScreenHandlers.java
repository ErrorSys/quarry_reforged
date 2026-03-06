package com.errorsys.quarry_reforged.content;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.screen.QuarryScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
    public static ScreenHandlerType<QuarryScreenHandler> QUARRY;

    private ModScreenHandlers() {}

    public static void register() {
        QUARRY = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(QuarryReforged.MOD_ID, "quarry"),
                new ExtendedScreenHandlerType<>(QuarryScreenHandler::new)
        );
    }
}
