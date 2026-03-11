package com.errorsys.quarry_reforged.content.block;

import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.blockentity.QuarryMarkerBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class MarkerAreaResolver {
    private MarkerAreaResolver() {}

    @Nullable
    public static MarkerArea resolveFromOriginMarker(ServerWorld world, BlockPos originMarkerPos) {
        if (!world.getBlockState(originMarkerPos).isOf(ModBlocks.MARKER)) return null;
        if (!(world.getBlockEntity(originMarkerPos) instanceof QuarryMarkerBlockEntity markerBe)) return null;
        if (!markerBe.hasPreview()) return null;
        BlockPos markerOrigin = markerBe.getOriginPos();
        if (markerOrigin == null || !markerOrigin.equals(originMarkerPos)) return null;

        return new MarkerArea(
                markerBe.getMinX(), markerBe.getMaxX(),
                markerBe.getMinY(), markerBe.getMaxY(),
                markerBe.getMinZ(), markerBe.getMaxZ()
        );
    }

    public record MarkerArea(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
}
