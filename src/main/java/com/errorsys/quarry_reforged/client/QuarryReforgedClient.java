package com.errorsys.quarry_reforged.client;

import com.errorsys.quarry_reforged.content.ModBlockEntities;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.ModScreenHandlers;
import com.errorsys.quarry_reforged.client.render.QuarryBlockEntityRenderer;
import com.errorsys.quarry_reforged.client.render.QuarryMarkerBlockEntityRenderer;
import com.errorsys.quarry_reforged.client.screen.QuarryScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.minecraft.client.render.RenderLayer;

public class QuarryReforgedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenRegistry.register(ModScreenHandlers.QUARRY, QuarryScreen::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.QUARRY, QuarryBlockEntityRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.QUARRY_MARKER, QuarryMarkerBlockEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FRAME, RenderLayer.getTranslucent());
    }
}
