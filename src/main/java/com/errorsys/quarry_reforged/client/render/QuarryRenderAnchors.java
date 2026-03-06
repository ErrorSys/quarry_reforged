package com.errorsys.quarry_reforged.client.render;

import net.minecraft.util.math.Vec3d;

public final class QuarryRenderAnchors {
    private QuarryRenderAnchors() {}

    public static Vec3d toolHeadLocal(QuarryRenderContext context, Vec3d toolHeadWorld, double yOffset) {
        return context.toLocal(toolHeadWorld).add(0.0, yOffset, 0.0);
    }

    public static GantrySpan gantrySpan(QuarryRenderContext context, double gantryHeight, double frameContactExtension) {
        double minY = context.frameTopY() - context.quarryPos().getY() + 0.5 - gantryHeight / 2.0;
        double minX = context.innerMinX() - context.quarryPos().getX() - frameContactExtension;
        double maxX = context.innerMaxX() - context.quarryPos().getX() + 1.0 + frameContactExtension;
        double minZ = context.innerMinZ() - context.quarryPos().getZ() - frameContactExtension;
        double maxZ = context.innerMaxZ() - context.quarryPos().getZ() + 1.0 + frameContactExtension;
        return new GantrySpan(minX, maxX, minZ, maxZ, minY);
    }

    public static InnerThirdBounds innerThirdBounds(QuarryRenderContext context, double topMargin) {
        double minX = innerThirdMin(context.innerMinX(), context.innerMaxX()) - context.quarryPos().getX();
        double maxX = innerThirdMax(context.innerMinX(), context.innerMaxX()) - context.quarryPos().getX();
        double minY = context.innerMinY() - context.quarryPos().getY() + 0.5;
        double maxY = context.frameTopY() - context.quarryPos().getY() - topMargin;
        if (maxY < minY) maxY = minY;
        double minZ = innerThirdMin(context.innerMinZ(), context.innerMaxZ()) - context.quarryPos().getZ();
        double maxZ = innerThirdMax(context.innerMinZ(), context.innerMaxZ()) - context.quarryPos().getZ();
        Vec3d center = new Vec3d(
                (context.innerMinX() + context.innerMaxX() + 1) * 0.5 - context.quarryPos().getX(),
                (context.innerMinY() + context.innerMaxY() + 1) * 0.5 - context.quarryPos().getY(),
                (context.innerMinZ() + context.innerMaxZ() + 1) * 0.5 - context.quarryPos().getZ()
        );
        return new InnerThirdBounds(minX, maxX, minY, maxY, minZ, maxZ, center);
    }

    private static double innerThirdMin(int minInclusive, int maxInclusive) {
        double size = maxInclusive - minInclusive + 1.0;
        double center = (minInclusive + maxInclusive + 1.0) * 0.5;
        double halfThird = Math.max(0.2, size / 6.0);
        return center - halfThird;
    }

    private static double innerThirdMax(int minInclusive, int maxInclusive) {
        double size = maxInclusive - minInclusive + 1.0;
        double center = (minInclusive + maxInclusive + 1.0) * 0.5;
        double halfThird = Math.max(0.2, size / 6.0);
        return center + halfThird;
    }

    public record GantrySpan(double minX, double maxX, double minZ, double maxZ, double minY) {}

    public record InnerThirdBounds(double minX, double maxX, double minY, double maxY, double minZ, double maxZ, Vec3d center) {}
}
