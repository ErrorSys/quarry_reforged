package com.errorsys.quarry_reforged.client.render.component;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class LaserCubeRenderer {

    private final QuarryRenderPrimitives primitives;
    private final QuarryRenderMaterialPolicy materialPolicy;
    private final Identifier texture;
    private final double cubeSize;

    public LaserCubeRenderer(QuarryRenderPrimitives primitives,
                             QuarryRenderMaterialPolicy materialPolicy,
                             Identifier texture,
                             double cubeSize) {
        this.primitives = primitives;
        this.materialPolicy = materialPolicy;
        this.texture = texture;
        this.cubeSize = cubeSize;
    }

    public void render(Vec3d cubePos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        RenderLayer layer = materialPolicy.layerFor(QuarryRenderMaterialPolicy.ComponentKind.CUBE, texture);
        int resolvedLight = materialPolicy.lightFor(QuarryRenderMaterialPolicy.ComponentKind.CUBE, light);
        primitives.renderBoxTextured(
                matrices,
                vertexConsumers,
                layer,
                resolvedLight,
                cubePos.x - cubeSize / 2.0,
                cubePos.y - cubeSize / 2.0,
                cubePos.z - cubeSize / 2.0,
                cubeSize,
                cubeSize,
                cubeSize,
                QuarryComponentUvMaps.CUBE,
                QuarryComponentUvMaps.TEX_W,
                QuarryComponentUvMaps.TEX_H
        );
    }
}
