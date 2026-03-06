package com.errorsys.quarry_reforged.client.render.component;

import com.errorsys.quarry_reforged.client.render.QuarryRenderAnchors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class GantryRenderer {
    private static final double AXIS_Y_OFFSET = 1.0 / 1024.0;
    private static final int TEX_W = 16;
    private static final int TEX_H = 16;
    private static final QuarryUvPolicy.FacePixelRects UV_RECTS = new QuarryUvPolicy.FacePixelRects(
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 16),   // north
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 16),   // south
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 16),   // west
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 16),   // east
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 16),   // up
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 16)    // down
    );

    private final QuarryRenderPrimitives primitives;
    private final QuarryRenderMaterialPolicy materialPolicy;
    private final Identifier texture;
    private final double gantryW;
    private final double gantryH;

    public GantryRenderer(QuarryRenderPrimitives primitives,
                          QuarryRenderMaterialPolicy materialPolicy,
                          Identifier texture,
                          double gantryW,
                          double gantryH) {
        this.primitives = primitives;
        this.materialPolicy = materialPolicy;
        this.texture = texture;
        this.gantryW = gantryW;
        this.gantryH = gantryH;
    }

    public void render(QuarryRenderAnchors.GantrySpan span, Vec3d headPos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int shadedLight) {
        RenderLayer layer = materialPolicy.layerFor(QuarryRenderMaterialPolicy.ComponentKind.GANTRY, texture);
        int light = materialPolicy.lightFor(QuarryRenderMaterialPolicy.ComponentKind.GANTRY, shadedLight);
        double beamZ = headPos.z - gantryW / 2.0;
        double beamX = headPos.x - gantryW / 2.0;

        // Slightly separate the two rails on Y to avoid coincident/colliding faces at the crossing.
        primitives.renderBoxTextured(matrices, vertexConsumers, layer, light, span.minX(), span.minY() + AXIS_Y_OFFSET, beamZ, span.maxX() - span.minX(), gantryH, gantryW, UV_RECTS, TEX_W, TEX_H);
        primitives.renderBoxTextured(matrices, vertexConsumers, layer, light, beamX, span.minY() - AXIS_Y_OFFSET, span.minZ(), gantryW, gantryH, span.maxZ() - span.minZ(), UV_RECTS, TEX_W, TEX_H);
    }
}
