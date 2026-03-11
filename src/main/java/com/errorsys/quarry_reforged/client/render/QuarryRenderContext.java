package com.errorsys.quarry_reforged.client.render;

import com.errorsys.quarry_reforged.client.net.QuarryMotionClientState;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity.RenderChannelPhase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class QuarryRenderContext {
    private final BlockPos quarryPos;
    private final boolean areaLocked;
    private final RenderChannelPhase renderPhase;
    private final long renderPhaseStartedTick;
    private final long renderStateTick;
    private final long toolHeadStateTick;
    private final int frameTopY;
    private final int innerMinX;
    private final int innerMaxX;
    private final int innerMinY;
    private final int innerMaxY;
    private final int innerMinZ;
    private final int innerMaxZ;
    @Nullable
    private final BlockPos activeMiningTarget;
    @Nullable
    private final BlockPos waypointCurrent;
    @Nullable
    private final BlockPos waypointNext;
    private final Vec3d toolHeadPos;
    private final Vec3d toolHeadOriginPos;
    private final Vec3d laserCubeWorldPos;
    private final boolean noPower;
    private final boolean forceHomeGantry;
    private final boolean rediscoveryDraining;
    private final boolean rediscoveryLaserVerticalTravelActive;
    private final boolean interpolationEnabled;
    private final double blocksPerSecond;
    private final long worldTime;

    private QuarryRenderContext(
            BlockPos quarryPos,
            boolean areaLocked,
            RenderChannelPhase renderPhase,
            long renderPhaseStartedTick,
            long renderStateTick,
            long toolHeadStateTick,
            int frameTopY,
            int innerMinX,
            int innerMaxX,
            int innerMinY,
            int innerMaxY,
            int innerMinZ,
            int innerMaxZ,
            @Nullable BlockPos activeMiningTarget,
            @Nullable BlockPos waypointCurrent,
            @Nullable BlockPos waypointNext,
            Vec3d toolHeadPos,
            Vec3d toolHeadOriginPos,
            Vec3d laserCubeWorldPos,
            boolean noPower,
            boolean forceHomeGantry,
            boolean rediscoveryDraining,
            boolean rediscoveryLaserVerticalTravelActive,
            boolean interpolationEnabled,
            double blocksPerSecond,
            long worldTime
    ) {
        this.quarryPos = quarryPos;
        this.areaLocked = areaLocked;
        this.renderPhase = renderPhase;
        this.renderPhaseStartedTick = renderPhaseStartedTick;
        this.renderStateTick = renderStateTick;
        this.toolHeadStateTick = toolHeadStateTick;
        this.frameTopY = frameTopY;
        this.innerMinX = innerMinX;
        this.innerMaxX = innerMaxX;
        this.innerMinY = innerMinY;
        this.innerMaxY = innerMaxY;
        this.innerMinZ = innerMinZ;
        this.innerMaxZ = innerMaxZ;
        this.activeMiningTarget = activeMiningTarget;
        this.waypointCurrent = waypointCurrent;
        this.waypointNext = waypointNext;
        this.toolHeadPos = toolHeadPos;
        this.toolHeadOriginPos = toolHeadOriginPos;
        this.laserCubeWorldPos = laserCubeWorldPos;
        this.noPower = noPower;
        this.forceHomeGantry = forceHomeGantry;
        this.rediscoveryDraining = rediscoveryDraining;
        this.rediscoveryLaserVerticalTravelActive = rediscoveryLaserVerticalTravelActive;
        this.interpolationEnabled = interpolationEnabled;
        this.blocksPerSecond = blocksPerSecond;
        this.worldTime = worldTime;
    }

    public static QuarryRenderContext from(QuarryBlockEntity be) {
        long serverSyncedTick = be.getWorld() == null ? 0L : be.getWorld().getTime();
        long animationTick = be.getDebugAnimationTickClient(serverSyncedTick);
        long beRenderStateTick = be.getRenderChannelStateTickClient();
        Vec3d beToolHeadPos = be.getClientToolHeadPos();
        QuarryMotionClientState.ResolvedMotion resolvedMotion = QuarryMotionClientState.resolveToolHeadPos(
                be,
                serverSyncedTick,
                beToolHeadPos,
                beRenderStateTick
        );
        return new QuarryRenderContext(
                be.getPos(),
                be.hasLockedAreaClient(),
                be.getRenderChannelPhaseClient(),
                be.getRenderChannelPhaseStartedTickClient(),
                beRenderStateTick,
                resolvedMotion.stateTick(),
                be.getFrameTopY(),
                be.getInnerMinX(),
                be.getInnerMaxX(),
                be.getInnerMinY(),
                be.getInnerMaxY(),
                be.getInnerMinZ(),
                be.getInnerMaxZ(),
                be.getRenderChannelActiveTargetClient(),
                be.getRenderChannelWaypointCurrentClient(),
                be.getRenderChannelWaypointNextClient(),
                resolvedMotion.toolHeadPos(),
                be.getClientToolHeadOriginPos(),
                be.getClientLaserCubeWorldPos(),
                be.isNoPowerClient(),
                be.shouldForceHomeGantryClient(),
                be.isRediscoveryDrainActiveClient(),
                be.isRediscoveryLaserVerticalTravelActiveClient(),
                be.isDebugInterpolationEnabledClient(),
                be.getBlocksPerSecondClient(),
                animationTick
        );
    }

    public BlockPos quarryPos() { return quarryPos; }
    public boolean areaLocked() { return areaLocked; }
    public RenderChannelPhase renderPhase() { return renderPhase; }
    public long renderPhaseStartedTick() { return renderPhaseStartedTick; }
    public long renderStateTick() { return renderStateTick; }
    public long toolHeadStateTick() { return toolHeadStateTick; }
    public int frameTopY() { return frameTopY; }
    public int innerMinX() { return innerMinX; }
    public int innerMaxX() { return innerMaxX; }
    public int innerMinY() { return innerMinY; }
    public int innerMaxY() { return innerMaxY; }
    public int innerMinZ() { return innerMinZ; }
    public int innerMaxZ() { return innerMaxZ; }
    @Nullable public BlockPos activeMiningTarget() { return activeMiningTarget; }
    @Nullable public BlockPos waypointCurrent() { return waypointCurrent; }
    @Nullable public BlockPos waypointNext() { return waypointNext; }
    public Vec3d toolHeadPos() { return toolHeadPos; }
    public Vec3d toolHeadOriginPos() { return toolHeadOriginPos; }
    public Vec3d laserCubeWorldPos() { return laserCubeWorldPos; }
    public boolean noPower() { return noPower; }
    public boolean forceHomeGantry() { return forceHomeGantry; }
    public boolean rediscoveryDraining() { return rediscoveryDraining; }
    public boolean rediscoveryLaserVerticalTravelActive() { return rediscoveryLaserVerticalTravelActive; }
    public boolean interpolationEnabled() { return interpolationEnabled; }
    public double blocksPerSecond() { return blocksPerSecond; }
    public long worldTime() { return worldTime; }

    public Vec3d toLocal(Vec3d worldPos) {
        return new Vec3d(
                worldPos.x - quarryPos.getX(),
                worldPos.y - quarryPos.getY(),
                worldPos.z - quarryPos.getZ()
        );
    }
}
