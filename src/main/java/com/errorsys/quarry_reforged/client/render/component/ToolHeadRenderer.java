package com.errorsys.quarry_reforged.client.render.component;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class ToolHeadRenderer {

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
                QuarryComponentUvMaps.TOOL_HEAD,
                QuarryComponentUvMaps.TEX_W,
                QuarryComponentUvMaps.TEX_H
        );
    }

    public double topY(Vec3d headPos) {
        return headPos.y + toolHeadH / 2.0;
    }
}
