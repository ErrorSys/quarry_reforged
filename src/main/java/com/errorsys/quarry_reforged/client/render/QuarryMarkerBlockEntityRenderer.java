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
    private static final double HELPER_BEAM_WIDTH = PREVIEW_BEAM_WIDTH - (1.0 / 1024.0);
    private static final double PREVIEW_BEAM_END_EXTEND = 1.0 / 16.0;
    private static final double PREVIEW_EDGE_OUTSET = 1.0 / 1024.0;
    private static final int MIN_SHADED_BLOCK_LIGHT = 10;
    private static final int MIN_SHADED_LIGHT = MIN_SHADED_BLOCK_LIGHT << 4;

    private final QuarryRenderMaterialPolicy materialPolicy = new QuarryRenderMaterialPolicy();
    private final BeamRenderer beamRenderer = new BeamRenderer(materialPolicy, LASER_TEXTURE, PREVIEW_BEAM_WIDTH);
    private final BeamRenderer helperBeamRenderer = new BeamRenderer(materialPolicy, LASER_TEXTURE, HELPER_BEAM_WIDTH);

    public QuarryMarkerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(QuarryMarkerBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (be.hasPreview() && be.isOriginMarker()) {
            renderBoxPreview(be, matrices, vertexConsumers, light);
            return;
        }
        if (be.hasInvalidCardinalPreview()) {
            renderInvalidCardinalPreview(be, matrices, vertexConsumers, light);
        }
    }

    private void renderBoxPreview(QuarryMarkerBlockEntity be, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        BlockPos origin = be.getPos();
        int shadedLight = clampShadedLight(light);

        double minX = be.getMinX() - PREVIEW_EDGE_OUTSET;
        double minY = be.getMinY() - PREVIEW_EDGE_OUTSET;
        double minZ = be.getMinZ() - PREVIEW_EDGE_OUTSET;
        double maxX = be.getMaxX() + PREVIEW_EDGE_OUTSET;
        double maxY = be.getMaxY() + PREVIEW_EDGE_OUTSET;
        double maxZ = be.getMaxZ() + PREVIEW_EDGE_OUTSET;

        Vec3d p000 = toLocalCenter(origin, minX, minY, minZ);
        Vec3d p001 = toLocalCenter(origin, minX, minY, maxZ);
        Vec3d p010 = toLocalCenter(origin, minX, maxY, minZ);
        Vec3d p011 = toLocalCenter(origin, minX, maxY, maxZ);
        Vec3d p100 = toLocalCenter(origin, maxX, minY, minZ);
        Vec3d p101 = toLocalCenter(origin, maxX, minY, maxZ);
        Vec3d p110 = toLocalCenter(origin, maxX, maxY, minZ);
        Vec3d p111 = toLocalCenter(origin, maxX, maxY, maxZ);

        // Bottom ring.
        renderExpandedBeam(p000, p001, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p001, p101, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p101, p100, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p100, p000, matrices, vertexConsumers, shadedLight);

        // Top ring.
        renderExpandedBeam(p010, p011, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p011, p111, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p111, p110, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p110, p010, matrices, vertexConsumers, shadedLight);

        // Vertical edges.
        renderExpandedBeam(p000, p010, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p001, p011, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p100, p110, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p101, p111, matrices, vertexConsumers, shadedLight);
    }

    private void renderInvalidCardinalPreview(QuarryMarkerBlockEntity be, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        int shadedLight = clampShadedLight(light);
        double range = Math.max(1, be.getInvalidCardinalRange());
        Vec3d center = new Vec3d(0.5, 0.5, 0.5);

        renderHelperBeam(center, new Vec3d(1.0, 0.0, 0.0), range, matrices, vertexConsumers, shadedLight);
        renderHelperBeam(center, new Vec3d(-1.0, 0.0, 0.0), range, matrices, vertexConsumers, shadedLight);
        renderHelperBeam(center, new Vec3d(0.0, 0.0, 1.0), range, matrices, vertexConsumers, shadedLight);
        renderHelperBeam(center, new Vec3d(0.0, 0.0, -1.0), range, matrices, vertexConsumers, shadedLight);
        renderHelperBeam(center, new Vec3d(0.0, 1.0, 0.0), range, matrices, vertexConsumers, shadedLight);
        renderHelperBeam(center, new Vec3d(0.0, -1.0, 0.0), range, matrices, vertexConsumers, shadedLight);
    }

    private void renderHelperBeam(Vec3d center, Vec3d direction, double range, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int shadedLight) {
        Vec3d from = center;
        Vec3d to = center.add(direction.multiply(range));
        helperBeamRenderer.render(from, to, matrices, vertexConsumers, shadedLight);
    }

    private void renderExpandedBeam(Vec3d from, Vec3d to, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int shadedLight) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length <= 1.0E-6) {
            return;
        }
        Vec3d unit = delta.multiply(1.0 / length);
        Vec3d expandedFrom = from.subtract(unit.multiply(PREVIEW_BEAM_END_EXTEND));
        Vec3d expandedTo = to.add(unit.multiply(PREVIEW_BEAM_END_EXTEND));
        beamRenderer.render(expandedFrom, expandedTo, matrices, vertexConsumers, shadedLight);
    }

    private Vec3d toLocalCenter(BlockPos origin, double x, double y, double z) {
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
