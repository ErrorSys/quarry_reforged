package com.errorsys.quarry_reforged.client.render.component;

public final class QuarryComponentUvMaps {
    public static final int TEX_W = 16;
    public static final int TEX_H = 16;

    // Edit these per-face rects like block model JSON UVs: [u1, v1, u2, v2].
    // tiled(...) repeats by face size in blocks. fixed(...) stretches once.

    public static final QuarryUvPolicy.FacePixelRects GANTRY = new QuarryUvPolicy.FacePixelRects(
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // north
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // south
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // west
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // east
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),  // up
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6)   // down
    );

    public static final QuarryUvPolicy.FacePixelRects PIPE = new QuarryUvPolicy.FacePixelRects(
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // north
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // south
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // west
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),   // east
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6),  // up
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 16, 6)   // down
    );

    public static final QuarryUvPolicy.FacePixelRects TOOL_HEAD = new QuarryUvPolicy.FacePixelRects(
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // north
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // south
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // west
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // east
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // up
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16)   // down
    );

    public static final QuarryUvPolicy.FacePixelRects CUBE = new QuarryUvPolicy.FacePixelRects(
            QuarryUvPolicy.PixelFaceRect.fixed(0, 0, 16, 16),  // north
            QuarryUvPolicy.PixelFaceRect.fixed(0, 0, 16, 16),  // south
            QuarryUvPolicy.PixelFaceRect.fixed(0, 0, 16, 16),  // west
            QuarryUvPolicy.PixelFaceRect.fixed(0, 0, 16, 16),  // east
            QuarryUvPolicy.PixelFaceRect.fixed(0, 0, 16, 16),  // up
            QuarryUvPolicy.PixelFaceRect.fixed(0, 0, 16, 16)   // down
    );

    public static final QuarryUvPolicy.FacePixelRects BEAM = new QuarryUvPolicy.FacePixelRects(
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // north
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // south
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // west
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // east
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16),  // up (unused for beam prism)
            QuarryUvPolicy.PixelFaceRect.tiled(0, 0, 2, 16)   // down (unused for beam prism)
    );

    private QuarryComponentUvMaps() {
    }
}
