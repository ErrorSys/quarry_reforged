package com.errorsys.quarry_reforged.client.render.component;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class QuarryRenderPrimitives {
    private final QuarryUvPolicy uvPolicy = new QuarryUvPolicy();

    public void renderBoxTextured(MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers,
                                  RenderLayer renderLayer,
                                  int light,
                                  double minX,
                                  double minY,
                                  double minZ,
                                  double sizeX,
                                  double sizeY,
                                  double sizeZ) {
        renderBoxTextured(matrices, vertexConsumers, renderLayer, light, minX, minY, minZ, sizeX, sizeY, sizeZ, null, 16, 16);
    }

    public void renderBoxTextured(MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers,
                                  RenderLayer renderLayer,
                                  int light,
                                  double minX,
                                  double minY,
                                  double minZ,
                                  double sizeX,
                                  double sizeY,
                                  double sizeZ,
                                  QuarryUvPolicy.FacePixelRects facePixelRects,
                                  int textureWidth,
                                  int textureHeight) {
        renderBoxTextured(matrices, vertexConsumers, renderLayer, light, minX, minY, minZ, sizeX, sizeY, sizeZ, facePixelRects, textureWidth, textureHeight, false);
    }

    public void renderBoxTextured(MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers,
                                  RenderLayer renderLayer,
                                  int light,
                                  double minX,
                                  double minY,
                                  double minZ,
                                  double sizeX,
                                  double sizeY,
                                  double sizeZ,
                                  QuarryUvPolicy.FacePixelRects facePixelRects,
                                  int textureWidth,
                                  int textureHeight,
                                  boolean swapTopBottomUvAxes) {
        if (sizeX <= 0.0 || sizeY <= 0.0 || sizeZ <= 0.0) return;

        VertexConsumer consumer = vertexConsumers.getBuffer(renderLayer);
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        float x1 = (float) minX;
        float y1 = (float) minY;
        float z1 = (float) minZ;
        float x2 = (float) (minX + sizeX);
        float y2 = (float) (minY + sizeY);
        float z2 = (float) (minZ + sizeZ);

        QuarryUvPolicy.FaceUv northUv = (facePixelRects == null)
                ? uvPolicy.north(sizeX, sizeY)
                : uvPolicy.fromPixelRect(facePixelRects.north(), textureWidth, textureHeight, sizeX, sizeY);
        QuarryUvPolicy.FaceUv southUv = (facePixelRects == null)
                ? uvPolicy.south(sizeX, sizeY)
                : uvPolicy.fromPixelRect(facePixelRects.south(), textureWidth, textureHeight, sizeX, sizeY);
        QuarryUvPolicy.FaceUv westUv = (facePixelRects == null)
                ? uvPolicy.west(sizeZ, sizeY)
                : uvPolicy.fromPixelRect(facePixelRects.west(), textureWidth, textureHeight, sizeZ, sizeY);
        QuarryUvPolicy.FaceUv eastUv = (facePixelRects == null)
                ? uvPolicy.east(sizeZ, sizeY)
                : uvPolicy.fromPixelRect(facePixelRects.east(), textureWidth, textureHeight, sizeZ, sizeY);
        QuarryUvPolicy.FaceUv upUv = (facePixelRects == null)
                ? uvPolicy.up(sizeX, sizeZ)
                : uvPolicy.fromPixelRect(
                        facePixelRects.up(),
                        textureWidth,
                        textureHeight,
                        swapTopBottomUvAxes ? sizeZ : sizeX,
                        swapTopBottomUvAxes ? sizeX : sizeZ
                );
        QuarryUvPolicy.FaceUv downUv = (facePixelRects == null)
                ? uvPolicy.down(sizeX, sizeZ)
                : uvPolicy.fromPixelRect(
                        facePixelRects.down(),
                        textureWidth,
                        textureHeight,
                        swapTopBottomUvAxes ? sizeZ : sizeX,
                        swapTopBottomUvAxes ? sizeX : sizeZ
                );

        addFace(consumer, position, normal,
                x1, y1, z1, northUv.u1(), northUv.v1(),
                x2, y1, z1, northUv.u2(), northUv.v2(),
                x2, y2, z1, northUv.u3(), northUv.v3(),
                x1, y2, z1, northUv.u4(), northUv.v4(),
                0.0f, 0.0f, -1.0f, light);

        addFace(consumer, position, normal,
                x2, y1, z2, southUv.u1(), southUv.v1(),
                x1, y1, z2, southUv.u2(), southUv.v2(),
                x1, y2, z2, southUv.u3(), southUv.v3(),
                x2, y2, z2, southUv.u4(), southUv.v4(),
                0.0f, 0.0f, 1.0f, light);

        addFace(consumer, position, normal,
                x1, y1, z2, westUv.u1(), westUv.v1(),
                x1, y1, z1, westUv.u2(), westUv.v2(),
                x1, y2, z1, westUv.u3(), westUv.v3(),
                x1, y2, z2, westUv.u4(), westUv.v4(),
                -1.0f, 0.0f, 0.0f, light);

        addFace(consumer, position, normal,
                x2, y1, z1, eastUv.u1(), eastUv.v1(),
                x2, y1, z2, eastUv.u2(), eastUv.v2(),
                x2, y2, z2, eastUv.u3(), eastUv.v3(),
                x2, y2, z1, eastUv.u4(), eastUv.v4(),
                1.0f, 0.0f, 0.0f, light);

        addFace(consumer, position, normal,
                x1, y2, z1, (swapTopBottomUvAxes ? transpose(upUv).u1() : upUv.u1()), (swapTopBottomUvAxes ? transpose(upUv).v1() : upUv.v1()),
                x2, y2, z1, (swapTopBottomUvAxes ? transpose(upUv).u2() : upUv.u2()), (swapTopBottomUvAxes ? transpose(upUv).v2() : upUv.v2()),
                x2, y2, z2, (swapTopBottomUvAxes ? transpose(upUv).u3() : upUv.u3()), (swapTopBottomUvAxes ? transpose(upUv).v3() : upUv.v3()),
                x1, y2, z2, (swapTopBottomUvAxes ? transpose(upUv).u4() : upUv.u4()), (swapTopBottomUvAxes ? transpose(upUv).v4() : upUv.v4()),
                0.0f, 1.0f, 0.0f, light);

        addFace(consumer, position, normal,
                x1, y1, z2, (swapTopBottomUvAxes ? transpose(downUv).u1() : downUv.u1()), (swapTopBottomUvAxes ? transpose(downUv).v1() : downUv.v1()),
                x2, y1, z2, (swapTopBottomUvAxes ? transpose(downUv).u2() : downUv.u2()), (swapTopBottomUvAxes ? transpose(downUv).v2() : downUv.v2()),
                x2, y1, z1, (swapTopBottomUvAxes ? transpose(downUv).u3() : downUv.u3()), (swapTopBottomUvAxes ? transpose(downUv).v3() : downUv.v3()),
                x1, y1, z1, (swapTopBottomUvAxes ? transpose(downUv).u4() : downUv.u4()), (swapTopBottomUvAxes ? transpose(downUv).v4() : downUv.v4()),
                0.0f, -1.0f, 0.0f, light);
    }

    private QuarryUvPolicy.FaceUv transpose(QuarryUvPolicy.FaceUv uv) {
        return new QuarryUvPolicy.FaceUv(
                uv.u1(), uv.v1(),
                uv.u1(), uv.v4(),
                uv.u3(), uv.v4(),
                uv.u3(), uv.v1()
        );
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
