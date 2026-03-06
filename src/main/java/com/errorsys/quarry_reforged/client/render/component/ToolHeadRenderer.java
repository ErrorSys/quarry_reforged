package com.errorsys.quarry_reforged.client.render.component;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class ToolHeadRenderer {
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
    private final double toolHeadW;
    private final double toolHeadH;

    public ToolHeadRenderer(QuarryRenderPrimitives primitives,
                            QuarryRenderMaterialPolicy materialPolicy,
                            Identifier texture,
                            double toolHeadW,
                            double toolHeadH) {
        this.primitives = primitives;
        this.materialPolicy = materialPolicy;
        this.texture = texture;
        this.toolHeadW = toolHeadW;
        this.toolHeadH = toolHeadH;
    }

    public void render(Vec3d headPos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int shadedLight) {
        RenderLayer layer = materialPolicy.layerFor(QuarryRenderMaterialPolicy.ComponentKind.TOOL_HEAD, texture);
        int light = materialPolicy.lightFor(QuarryRenderMaterialPolicy.ComponentKind.TOOL_HEAD, shadedLight);
        primitives.renderBoxTextured(
                matrices,
                vertexConsumers,
                layer,
                light,
                headPos.x - toolHeadW / 2.0,
                headPos.y - toolHeadH / 2.0,
                headPos.z - toolHeadW / 2.0,
                toolHeadW,
                toolHeadH,
                toolHeadW,
                UV_RECTS,
                TEX_W,
                TEX_H
        );
    }

    public double topY(Vec3d headPos) {
        return headPos.y + toolHeadH / 2.0;
    }
}
