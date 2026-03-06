package com.errorsys.quarry_reforged.client.render.component;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class PipeRenderer {
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

    private final QuarryRenderMaterialPolicy materialPolicy;
    private final QuarryUvPolicy uvPolicy = new QuarryUvPolicy();
    private final Identifier texture;
    private final double pipeW;
    private static final double JOIN_EPSILON = 1.0E-3;

    public PipeRenderer(QuarryRenderMaterialPolicy materialPolicy, Identifier texture, double pipeW) {
        this.materialPolicy = materialPolicy;
        this.texture = texture;
        this.pipeW = pipeW;
    }

    public void render(double centerX,
                       double centerZ,
                       double minY,
                       double maxY,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int shadedLight) {
        double y1 = minY + JOIN_EPSILON;
        double y2 = maxY - JOIN_EPSILON;
        if (y2 <= y1) return;

        float x1 = (float) (centerX - pipeW / 2.0);
        float x2 = (float) (centerX + pipeW / 2.0);
        float z1 = (float) (centerZ - pipeW / 2.0);
        float z2 = (float) (centerZ + pipeW / 2.0);

        float fy1 = (float) y1;
        float fy2 = (float) y2;

        RenderLayer layer = materialPolicy.layerFor(QuarryRenderMaterialPolicy.ComponentKind.PIPE, texture);
        int light = materialPolicy.lightFor(QuarryRenderMaterialPolicy.ComponentKind.PIPE, shadedLight);
        VertexConsumer consumer = vertexConsumers.getBuffer(layer);
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        QuarryUvPolicy.FaceUv northUv = uvPolicy.fromPixelRect(UV_RECTS.north(), TEX_W, TEX_H, pipeW, y2 - y1);
        QuarryUvPolicy.FaceUv southUv = uvPolicy.fromPixelRect(UV_RECTS.south(), TEX_W, TEX_H, pipeW, y2 - y1);
        QuarryUvPolicy.FaceUv westUv = uvPolicy.fromPixelRect(UV_RECTS.west(), TEX_W, TEX_H, pipeW, y2 - y1);
        QuarryUvPolicy.FaceUv eastUv = uvPolicy.fromPixelRect(UV_RECTS.east(), TEX_W, TEX_H, pipeW, y2 - y1);
        QuarryUvPolicy.FaceUv upUv = uvPolicy.fromPixelRect(UV_RECTS.up(), TEX_W, TEX_H, pipeW, pipeW);
        QuarryUvPolicy.FaceUv downUv = uvPolicy.fromPixelRect(UV_RECTS.down(), TEX_W, TEX_H, pipeW, pipeW);

        addFace(consumer, position, normal, x1, fy1, z1, northUv.u1(), northUv.v1(), x2, fy1, z1, northUv.u2(), northUv.v2(), x2, fy2, z1, northUv.u3(), northUv.v3(), x1, fy2, z1, northUv.u4(), northUv.v4(), 0.0f, 0.0f, -1.0f, light);
        addFace(consumer, position, normal, x2, fy1, z2, southUv.u1(), southUv.v1(), x1, fy1, z2, southUv.u2(), southUv.v2(), x1, fy2, z2, southUv.u3(), southUv.v3(), x2, fy2, z2, southUv.u4(), southUv.v4(), 0.0f, 0.0f, 1.0f, light);
        addFace(consumer, position, normal, x1, fy1, z2, westUv.u1(), westUv.v1(), x1, fy1, z1, westUv.u2(), westUv.v2(), x1, fy2, z1, westUv.u3(), westUv.v3(), x1, fy2, z2, westUv.u4(), westUv.v4(), -1.0f, 0.0f, 0.0f, light);
        addFace(consumer, position, normal, x2, fy1, z1, eastUv.u1(), eastUv.v1(), x2, fy1, z2, eastUv.u2(), eastUv.v2(), x2, fy2, z2, eastUv.u3(), eastUv.v3(), x2, fy2, z1, eastUv.u4(), eastUv.v4(), 1.0f, 0.0f, 0.0f, light);
        addFace(consumer, position, normal, x1, fy2, z1, upUv.u1(), upUv.v1(), x2, fy2, z1, upUv.u2(), upUv.v2(), x2, fy2, z2, upUv.u3(), upUv.v3(), x1, fy2, z2, upUv.u4(), upUv.v4(), 0.0f, 1.0f, 0.0f, light);
        addFace(consumer, position, normal, x1, fy1, z2, downUv.u1(), downUv.v1(), x2, fy1, z2, downUv.u2(), downUv.v2(), x2, fy1, z1, downUv.u3(), downUv.v3(), x1, fy1, z1, downUv.u4(), downUv.v4(), 0.0f, -1.0f, 0.0f, light);
    }

    private void addFace(VertexConsumer consumer, Matrix4f position, Matrix3f normalMatrix,
                         float x1, float y1, float z1, float u1, float v1,
                         float x2, float y2, float z2, float u2, float v2,
                         float x3, float y3, float z3, float u3, float v3,
                         float x4, float y4, float z4, float u4, float v4,
                         float nx, float ny, float nz, int light) {
        addVertex(consumer, position, normalMatrix, x1, y1, z1, u1, v1, nx, ny, nz, light);
        addVertex(consumer, position, normalMatrix, x2, y2, z2, u2, v2, nx, ny, nz, light);
        addVertex(consumer, position, normalMatrix, x3, y3, z3, u3, v3, nx, ny, nz, light);
        addVertex(consumer, position, normalMatrix, x4, y4, z4, u4, v4, nx, ny, nz, light);
    }

    private void addVertex(VertexConsumer consumer, Matrix4f position, Matrix3f normalMatrix,
                           float x, float y, float z, float u, float v,
                           float nx, float ny, float nz, int light) {
        consumer.vertex(position, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normalMatrix, nx, ny, nz)
                .next();
    }
}
