package com.errorsys.quarry_reforged.client.render.component;

public final class QuarryUvPolicy {
    public FaceUv north(double sizeX, double sizeY) {
        float u = span(sizeX);
        float v = span(sizeY);
        return new FaceUv(0.0f, 0.0f, u, 0.0f, u, v, 0.0f, v);
    }

    public FaceUv south(double sizeX, double sizeY) {
        float u = span(sizeX);
        float v = span(sizeY);
        return new FaceUv(0.0f, 0.0f, u, 0.0f, u, v, 0.0f, v);
    }

    public FaceUv west(double sizeZ, double sizeY) {
        float u = span(sizeZ);
        float v = span(sizeY);
        return new FaceUv(0.0f, 0.0f, u, 0.0f, u, v, 0.0f, v);
    }

    public FaceUv east(double sizeZ, double sizeY) {
        float u = span(sizeZ);
        float v = span(sizeY);
        return new FaceUv(0.0f, 0.0f, u, 0.0f, u, v, 0.0f, v);
    }

    public FaceUv up(double sizeX, double sizeZ) {
        float u = span(sizeX);
        float v = span(sizeZ);
        return new FaceUv(0.0f, 0.0f, u, 0.0f, u, v, 0.0f, v);
    }

    public FaceUv down(double sizeX, double sizeZ) {
        float u = span(sizeX);
        float v = span(sizeZ);
        return new FaceUv(0.0f, 0.0f, u, 0.0f, u, v, 0.0f, v);
    }

    private float span(double blocks) {
        return Math.max(1.0f / 16.0f, (float) blocks);
    }

    public FaceUv fromPixelRect(PixelFaceRect rect, int texWidth, int texHeight, double uScale, double vScale) {
        float safeW = Math.max(1.0f, texWidth);
        float safeH = Math.max(1.0f, texHeight);
        float u1 = rect.u1 / safeW;
        float v1 = rect.v1 / safeH;
        float uSpan = (rect.u2 - rect.u1) / safeW;
        float vSpan = (rect.v2 - rect.v1) / safeH;
        // Match block-model-style UV behavior for sub-block faces:
        // keep authored UV span intact for faces <= 1 block, and only tile when > 1 block.
        float uTiles = rect.tileU ? Math.max(1.0f, (float) uScale) : 1.0f;
        float vTiles = rect.tileV ? Math.max(1.0f, (float) vScale) : 1.0f;
        float u2 = u1 + uSpan * uTiles;
        float v2 = v1 + vSpan * vTiles;
        return new FaceUv(u1, v1, u2, v1, u2, v2, u1, v2);
    }

    public record PixelFaceRect(float u1, float v1, float u2, float v2, boolean tileU, boolean tileV) {
        public static PixelFaceRect tiled(float u1, float v1, float u2, float v2) {
            return new PixelFaceRect(u1, v1, u2, v2, true, true);
        }

        public static PixelFaceRect fixed(float u1, float v1, float u2, float v2) {
            return new PixelFaceRect(u1, v1, u2, v2, false, false);
        }
    }

    public record FacePixelRects(
            PixelFaceRect north,
            PixelFaceRect south,
            PixelFaceRect west,
            PixelFaceRect east,
            PixelFaceRect up,
            PixelFaceRect down
    ) {
        public static FacePixelRects uniformTiled(float u1, float v1, float u2, float v2) {
            PixelFaceRect rect = PixelFaceRect.tiled(u1, v1, u2, v2);
            return new FacePixelRects(rect, rect, rect, rect, rect, rect);
        }
    }

    public record FaceUv(
            float u1, float v1,
            float u2, float v2,
            float u3, float v3,
            float u4, float v4
    ) {}
}
