package com.errorsys.quarry_reforged.client;

import com.errorsys.quarry_reforged.content.ModBlockEntities;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.ModScreenHandlers;
import com.errorsys.quarry_reforged.client.debug.RediscoveryOverlayHud;
import com.errorsys.quarry_reforged.client.net.ClientDebugNetworking;
import com.errorsys.quarry_reforged.client.render.QuarryBlockEntityRenderer;
import com.errorsys.quarry_reforged.client.render.QuarryMarkerBlockEntityRenderer;
import com.errorsys.quarry_reforged.client.screen.QuarryScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class QuarryReforgedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.QUARRY, QuarryScreen::new);
        BlockEntityRendererFactories.register(ModBlockEntities.QUARRY, QuarryBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.QUARRY_MARKER, QuarryMarkerBlockEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FRAME, RenderLayer.getTranslucent());
        ClientDebugNetworking.register();
        RediscoveryOverlayHud.register();
    }
}
