package com.errorsys.quarry_reforged.client.render;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.client.render.component.BeamRenderer;
import com.errorsys.quarry_reforged.client.render.component.QuarryRenderMaterialPolicy;
import com.errorsys.quarry_reforged.content.blockentity.QuarryMarkerBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class QuarryMarkerBlockEntityRenderer implements BlockEntityRenderer<QuarryMarkerBlockEntity> {
    private static final Identifier LASER_TEXTURE = new Identifier(QuarryReforged.MOD_ID, "textures/entity/quarry/laser.png");
    private static final double PREVIEW_BEAM_WIDTH = 2.0 / 16.0;
    private static final int MIN_SHADED_BLOCK_LIGHT = 10;
    private static final int MIN_SHADED_LIGHT = MIN_SHADED_BLOCK_LIGHT << 4;

    private final QuarryRenderMaterialPolicy materialPolicy = new QuarryRenderMaterialPolicy();
    private final BeamRenderer beamRenderer = new BeamRenderer(materialPolicy, LASER_TEXTURE, PREVIEW_BEAM_WIDTH);

    public QuarryMarkerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(QuarryMarkerBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!be.hasPreview() || !be.isOriginMarker()) return;
        BlockPos origin = be.getPos();
        int shadedLight = clampShadedLight(light);

        Vec3d p000 = toLocalCenter(origin, be.getMinX(), be.getMinY(), be.getMinZ());
        Vec3d p001 = toLocalCenter(origin, be.getMinX(), be.getMinY(), be.getMaxZ());
        Vec3d p010 = toLocalCenter(origin, be.getMinX(), be.getMaxY(), be.getMinZ());
        Vec3d p011 = toLocalCenter(origin, be.getMinX(), be.getMaxY(), be.getMaxZ());
        Vec3d p100 = toLocalCenter(origin, be.getMaxX(), be.getMinY(), be.getMinZ());
        Vec3d p101 = toLocalCenter(origin, be.getMaxX(), be.getMinY(), be.getMaxZ());
        Vec3d p110 = toLocalCenter(origin, be.getMaxX(), be.getMaxY(), be.getMinZ());
        Vec3d p111 = toLocalCenter(origin, be.getMaxX(), be.getMaxY(), be.getMaxZ());

        // Bottom ring.
        beamRenderer.render(p000, p001, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p001, p101, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p101, p100, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p100, p000, matrices, vertexConsumers, shadedLight);

        // Top ring.
        beamRenderer.render(p010, p011, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p011, p111, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p111, p110, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p110, p010, matrices, vertexConsumers, shadedLight);

        // Vertical edges.
        beamRenderer.render(p000, p010, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p001, p011, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p100, p110, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(p101, p111, matrices, vertexConsumers, shadedLight);
    }

    private Vec3d toLocalCenter(BlockPos origin, int x, int y, int z) {
        return new Vec3d(
                x + 0.5 - origin.getX(),
                y + 0.5 - origin.getY(),
                z + 0.5 - origin.getZ()
        );
    }

    private int clampShadedLight(int light) {
        int block = Math.max(light & 0xFFFF, MIN_SHADED_LIGHT);
        int sky = Math.max((light >> 16) & 0xFFFF, MIN_SHADED_LIGHT);
        return block | (sky << 16);
    }

    @Override
    public boolean rendersOutsideBoundingBox(QuarryMarkerBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 256;
    }
}
