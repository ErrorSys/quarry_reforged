package com.errorsys.quarry_reforged.client.render;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.client.render.component.BeamRenderer;
import com.errorsys.quarry_reforged.client.render.component.GantryRenderer;
import com.errorsys.quarry_reforged.client.render.component.LaserCubeRenderer;
import com.errorsys.quarry_reforged.client.render.component.PipeRenderer;
import com.errorsys.quarry_reforged.client.render.component.QuarryRenderMaterialPolicy;
import com.errorsys.quarry_reforged.client.render.component.QuarryRenderPrimitives;
import com.errorsys.quarry_reforged.client.render.component.ToolHeadRenderer;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.block.QuarryBlock;
import com.errorsys.quarry_reforged.content.block.QuarryFrameBlock;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuarryBlockEntityRenderer implements BlockEntityRenderer<QuarryBlockEntity> {
    private static final Identifier GANTRY_TEXTURE = new Identifier(QuarryReforged.MOD_ID, "textures/entity/quarry/gantry.png");
    private static final Identifier TOOL_HEAD_TEXTURE = new Identifier(QuarryReforged.MOD_ID, "textures/entity/quarry/tool_head.png");
    private static final Identifier CUBE_TEXTURE = new Identifier(QuarryReforged.MOD_ID, "textures/entity/quarry/cube.png");
    private static final Identifier LASER_TEXTURE = new Identifier(QuarryReforged.MOD_ID, "textures/entity/quarry/laser.png");

    private static final int MIN_SHADED_BLOCK_LIGHT = 10;
    private static final int MIN_SHADED_LIGHT = MIN_SHADED_BLOCK_LIGHT << 4;
    private static final double INTERPOLATION_EPSILON_SQ = 1.0E-6;
    private static final double SNAP_DISTANCE_SQ = 0.0025;
    private static final double FOLLOW_STRENGTH = 0.35;

    private static final double TOOL_HEAD_W = 2.0 / 16.0;
    private static final double TOOL_HEAD_H = 1.0;
    private static final double TOOL_HEAD_Y_OFFSET = 1.0;
    private static final double PIPE_W = 6.0 / 16.0;
    private static final double GANTRY_W = 6.0 / 16.0;
    private static final double GANTRY_H = 6.0 / 16.0;
    private static final double FRAME_CONTACT_EXTENSION = 5.0 / 16.0;
    private static final double LASER_CUBE = 6.0 / 16.0;
    private static final double LASER_BEAM_W = 2.0 / 16.0;
    private static final long CUBE_WAYPOINT_INTERVAL_TICKS = 18L;
    private static final double CUBE_REACHED_TARGET_SQ = 0.02 * 0.02;
    private static final double CUBE_MIN_WANDER_BPS = 0.3;
    private static final double CUBE_MAX_WANDER_BPS = 1.6;
    private static final double CUBE_HOVER_RADIUS_MIN = 0.15;
    private static final double CUBE_HOVER_RADIUS_MAX = 0.6;
    private static final double CUBE_HOVER_Y_MIN = 0.08;
    private static final double CUBE_HOVER_Y_MAX = 0.35;
    private static final double CUBE_TOP_MARGIN = 0.2;
    private static final double TOP_LASER_CUBE_MIN_ABOVE_LAYER = 0.65;
    private static final CubeBehaviorMode TOP_LASER_CUBE_MODE = CubeBehaviorMode.WANDER_INNER_THIRD;
    private static final CubeBehaviorMode DEBUG_PREVIEW_CUBE_MODE = CubeBehaviorMode.STATIC;
    private static final double FRAME_THROW_ARC_SCALE = 0.12;
    private static final double FRAME_THROW_MIN_ARC = 0.35;
    private static final double FRAME_THROW_MIN_TICKS = 2.0;
    private static final double FRAME_THROW_MAX_TICKS = 14.0;
    private static final int FRAME_THROW_PREVIEW_MIN_COUNT = 8;
    private static final double LASER_BEAM_PERSIST_TICKS = 8.0;

    private final Map<InterpolationKey, InterpolationState> interpolationStates = new HashMap<>();
    private final Map<Long, CubeMotionState> cubeMotionStates = new HashMap<>();
    private final Map<Long, Map<Long, FrameThrowGhost>> frameThrowGhosts = new HashMap<>();
    private final Map<Long, BeamHoldState> beamHoldStates = new HashMap<>();
    private final Map<Long, GantryMotionState> gantryMotionStates = new HashMap<>();
    private final Map<Long, StaticGeometryCache> staticGeometryCaches = new HashMap<>();
    private final QuarryRenderMaterialPolicy materialPolicy = new QuarryRenderMaterialPolicy();
    private final QuarryRenderPrimitives primitives = new QuarryRenderPrimitives();
    private final GantryRenderer gantryRenderer = new GantryRenderer(primitives, materialPolicy, GANTRY_TEXTURE, GANTRY_W, GANTRY_H);
    private final ToolHeadRenderer toolHeadRenderer = new ToolHeadRenderer(primitives, materialPolicy, TOOL_HEAD_TEXTURE, TOOL_HEAD_W, TOOL_HEAD_H);
    private final PipeRenderer pipeRenderer = new PipeRenderer(materialPolicy, GANTRY_TEXTURE, PIPE_W);
    private final LaserCubeRenderer cubeRenderer = new LaserCubeRenderer(primitives, materialPolicy, CUBE_TEXTURE, LASER_CUBE);
    private final BeamRenderer beamRenderer = new BeamRenderer(materialPolicy, LASER_TEXTURE, LASER_BEAM_W);

    public QuarryBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(QuarryBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        QuarryRenderContext context = QuarryRenderContext.from(be);
        if (!context.areaLocked()) return;
        pruneInterpolationStates(be);
        pruneCubeMotionStates(be);
        pruneFrameThrowGhostStates(be);
        pruneBeamHoldStates(be);
        pruneGantryMotionStates(be);
        pruneStaticGeometryCaches(be);

        RenderDecision decision = decideRender(context);
        if (!decision.renderAny()) return;
        if (decision.clearFrameGhosts()) {
            frameThrowGhosts.remove(be.getPos().asLong());
        }

        int shadedLight = clampShadedLight(light);
        renderPhase(decision.phase(), context, be, tickDelta, matrices, vertexConsumers, shadedLight, overlay);
    }

    private RenderDecision decideRender(QuarryRenderContext context) {
        RenderPhase phase = switch (context.renderPhase()) {
            case DEBUG_PREVIEW -> RenderPhase.DEBUG_PREVIEW;
            case FRAME_WORK -> RenderPhase.FRAME_WORK;
            case TOP_LASER -> RenderPhase.TOP_LASER;
            case SUPPRESSED_RETURN -> RenderPhase.SUPPRESSED_RETURN;
            case GANTRY -> RenderPhase.GANTRY;
            case NONE -> RenderPhase.NONE;
        };
        boolean renderAny = phase != RenderPhase.NONE;
        boolean clearFrameGhosts = phase != RenderPhase.FRAME_WORK;
        return new RenderDecision(phase, renderAny, clearFrameGhosts);
    }

    private void renderPhase(RenderPhase phase,
                             QuarryRenderContext context,
                             QuarryBlockEntity be,
                             float tickDelta,
                             MatrixStack matrices,
                             VertexConsumerProvider vertexConsumers,
                             int shadedLight,
                             int overlay) {
        switch (phase) {
            case NONE -> {
                resetContinuousGantryMotion(context);
            }
            case DEBUG_PREVIEW -> renderDebugPreview(context, tickDelta, matrices, vertexConsumers, shadedLight, overlay);
            case FRAME_WORK -> {
                resetContinuousGantryMotion(context);
                resetInterpolatedToolHead(context);
                renderFrameBuildMode(context, be, tickDelta, matrices, vertexConsumers, shadedLight, overlay);
            }
            case TOP_LASER -> {
                resetContinuousGantryMotion(context);
                resetInterpolatedToolHead(context);
                renderTopLaserMode(context, tickDelta, matrices, vertexConsumers, shadedLight);
            }
            case SUPPRESSED_RETURN -> {
                resetContinuousGantryMotion(context);
            }
            case GANTRY -> renderGantryMode(context, tickDelta, matrices, vertexConsumers, shadedLight);
        }
    }

    private void renderGantryMode(QuarryRenderContext context,
                                  float tickDelta,
                                  MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers,
                                  int shadedLight) {
        Vec3d headWorld = getContinuousGantryHeadWorld(context, tickDelta);
        Vec3d headPos = QuarryRenderAnchors.toolHeadLocal(context, headWorld, TOOL_HEAD_Y_OFFSET);
        StaticGeometryCache staticCache = getStaticGeometryCache(context);
        QuarryRenderAnchors.GantrySpan gantrySpan = staticCache.gantrySpan;
        gantryRenderer.render(gantrySpan, headPos, matrices, vertexConsumers, shadedLight);
        pipeRenderer.render(headPos.x, headPos.z, toolHeadRenderer.topY(headPos), gantrySpan.minY(), matrices, vertexConsumers, shadedLight);
        toolHeadRenderer.render(headPos, matrices, vertexConsumers, shadedLight);
    }

    private void renderDebugPreview(QuarryRenderContext context,
                                    float tickDelta,
                                    MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    int shadedLight,
                                    int overlay) {
        Vec3d headPos = QuarryRenderAnchors.toolHeadLocal(context, getInterpolatedToolHeadPos(context, tickDelta), TOOL_HEAD_Y_OFFSET);
        StaticGeometryCache staticCache = getStaticGeometryCache(context);
        QuarryRenderAnchors.GantrySpan gantrySpan = staticCache.gantrySpan;
        gantryRenderer.render(gantrySpan, headPos, matrices, vertexConsumers, shadedLight);
        pipeRenderer.render(headPos.x, headPos.z, toolHeadRenderer.topY(headPos), gantrySpan.minY(), matrices, vertexConsumers, shadedLight);
        toolHeadRenderer.render(headPos, matrices, vertexConsumers, shadedLight);

        Vec3d cubePos = getCubeLocalPos(context, DEBUG_PREVIEW_CUBE_MODE);
        cubeRenderer.render(cubePos, matrices, vertexConsumers, shadedLight);
        beamRenderer.render(cubePos, headPos, matrices, vertexConsumers, shadedLight);

        BlockState previewFrame = ModBlocks.FRAME.getDefaultState()
                .with(QuarryFrameBlock.NORTH, true)
                .with(QuarryFrameBlock.SOUTH, true)
                .with(QuarryFrameBlock.EAST, true)
                .with(QuarryFrameBlock.WEST, true)
                .with(QuarryFrameBlock.UP, true)
                .with(QuarryFrameBlock.DOWN, true)
                .with(QuarryFrameBlock.THROW_PREVIEW, true);
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        matrices.push();
        matrices.translate(0.0, 1.0, 0.0);
        blockRenderManager.renderBlockAsEntity(previewFrame, matrices, vertexConsumers, shadedLight, overlay);
        matrices.pop();
    }

    private void renderFrameBuildMode(QuarryRenderContext context, QuarryBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int shadedLight, int overlay) {
        long quarryKey = be.getPos().asLong();
        Map<Long, FrameThrowGhost> ghosts = frameThrowGhosts.computeIfAbsent(quarryKey, k -> new HashMap<>());
        Vec3d start = getFrameThrowStartLocal();
        double worldTime = context.worldTime() + clampTickDelta(tickDelta);
        double placementsPerSecond = Math.max(1.0, context.blocksPerSecond());
        double placementIntervalTicks = Math.max(1.0, 20.0 / placementsPerSecond);
        int previewCount = Math.max(
                FRAME_THROW_PREVIEW_MIN_COUNT,
                MathHelper.ceil(FRAME_THROW_MAX_TICKS / placementIntervalTicks) + 3
        );
        List<BlockPos> targets = be.getClientUpcomingFrameTargets(previewCount);
        if (targets.isEmpty()) return;

        Set<Long> currentTargets = new HashSet<>();
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();

        for (int i = 0; i < targets.size(); i++) {
            BlockPos target = targets.get(i);
            if (be.getWorld() != null && be.getWorld().getBlockState(target).isOf(ModBlocks.FRAME)) {
                ghosts.remove(target.asLong());
                continue;
            }
            long packed = target.asLong();
            currentTargets.add(packed);
            FrameThrowGhost ghost = ghosts.get(packed);
            if (ghost == null) {
                Vec3d end = context.toLocal(Vec3d.ofCenter(target));
                double duration = getFrameThrowDurationTicks(context, start, end);
                // Launch early enough that each ghost reaches target center right when placement is expected.
                double launchTick = (worldTime + (i * placementIntervalTicks)) - duration;
                ghost = new FrameThrowGhost(packed, start, end, launchTick, duration);
                ghosts.put(packed, ghost);
            } else {
                ghost.end = context.toLocal(Vec3d.ofCenter(target));
            }
        }

        Iterator<FrameThrowGhost> iterator = ghosts.values().iterator();
        while (iterator.hasNext()) {
            FrameThrowGhost ghost = iterator.next();
            if (be.getWorld() != null
                    && be.getWorld().getBlockState(BlockPos.fromLong(ghost.targetPacked)).isOf(ModBlocks.FRAME)
                    && (worldTime - ghost.launchTick) >= (ghost.durationTicks * 0.9)) {
                iterator.remove();
                continue;
            }
            double age = worldTime - ghost.launchTick;
            if (age > ghost.durationTicks + 0.01) {
                iterator.remove();
                continue;
            }
            if (age < 0.0) continue;

            double t = MathHelper.clamp(age / Math.max(1.0, ghost.durationTicks), 0.0, 1.0);
            Vec3d base = ghost.start.lerp(ghost.end, t);
            double arcHeight = Math.max(FRAME_THROW_MIN_ARC, ghost.start.distanceTo(ghost.end) * FRAME_THROW_ARC_SCALE);
            Vec3d arcPos = base.add(0.0, arcHeight * 4.0 * t * (1.0 - t), 0.0);

            matrices.push();
            matrices.translate(arcPos.x - 0.25, arcPos.y - 0.25, arcPos.z - 0.25);
            matrices.scale(0.5f, 0.5f, 0.5f);
            BlockPos targetPos = BlockPos.fromLong(ghost.targetPacked);
            blockRenderManager.renderBlockAsEntity(getPredictedFrameState(be, targetPos), matrices, vertexConsumers, shadedLight, overlay);
            matrices.pop();
        }

        if (ghosts.isEmpty() && currentTargets.isEmpty()) {
            frameThrowGhosts.remove(quarryKey);
        }
    }

    private Vec3d getFrameThrowStartLocal() {
        return new Vec3d(0.5, 1.0, 0.5);
    }

    private int getFrameThrowDurationTicks(QuarryRenderContext context, Vec3d start, Vec3d end) {
        double distance = start.distanceTo(end);
        double bps = Math.max(1.0, context.blocksPerSecond());
        double ticks = (distance / bps) * 20.0;
        return MathHelper.ceil(MathHelper.clamp(ticks, FRAME_THROW_MIN_TICKS, FRAME_THROW_MAX_TICKS));
    }

    private BlockState getPredictedFrameState(QuarryBlockEntity be, BlockPos pos) {
        return ModBlocks.FRAME.getDefaultState()
                .with(QuarryFrameBlock.NORTH, predictedConnects(be, pos, Direction.NORTH))
                .with(QuarryFrameBlock.SOUTH, predictedConnects(be, pos, Direction.SOUTH))
                .with(QuarryFrameBlock.EAST, predictedConnects(be, pos, Direction.EAST))
                .with(QuarryFrameBlock.WEST, predictedConnects(be, pos, Direction.WEST))
                .with(QuarryFrameBlock.UP, predictedConnects(be, pos, Direction.UP))
                .with(QuarryFrameBlock.DOWN, predictedConnects(be, pos, Direction.DOWN))
                .with(QuarryFrameBlock.THROW_PREVIEW, true);
    }

    private boolean predictedConnects(QuarryBlockEntity be, BlockPos originPos, Direction directionToNeighbor) {
        BlockPos neighborPos = originPos.offset(directionToNeighbor);
        if (be.getWorld() != null && be.getWorld().getBlockState(neighborPos).isOf(ModBlocks.FRAME)) {
            return true;
        }
        if (be.getWorld() != null) {
            BlockState neighborState = be.getWorld().getBlockState(neighborPos);
            if (neighborState.isOf(ModBlocks.QUARRY) && neighborState.contains(QuarryBlock.FACING)) {
                return directionToNeighbor == neighborState.get(QuarryBlock.FACING);
            }
        }
        return be.isClientFramePlanned(neighborPos);
    }

    private void renderTopLaserMode(QuarryRenderContext context,
                                    float tickDelta,
                                    MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    int shadedLight) {
        long key = context.quarryPos().asLong();
        double now = context.worldTime() + clampTickDelta(tickDelta);
        Vec3d cubePos = getCubeLocalPos(context, TOP_LASER_CUBE_MODE);
        cubeRenderer.render(cubePos, matrices, vertexConsumers, shadedLight);

        BlockPos liveTarget = context.activeMiningTarget();
        BlockPos target = liveTarget;
        boolean hasLiveTarget = liveTarget != null;
        BeamHoldState hold = beamHoldStates.computeIfAbsent(key, k -> new BeamHoldState());
        if (target != null) {
            Vec3d nextTarget = context.toLocal(Vec3d.ofCenter(target));
            if (hold.primaryTargetLocal != null && hold.primaryTargetLocal.squaredDistanceTo(nextTarget) > 1.0E-6) {
                hold.trailingTargetLocal = hold.primaryTargetLocal;
                hold.trailingExpireAtTick = now + LASER_BEAM_PERSIST_TICKS;
            }
            hold.primaryTargetLocal = nextTarget;
            hold.primaryExpireAtTick = now + LASER_BEAM_PERSIST_TICKS;
        }

        if (hold.primaryTargetLocal != null && hold.primaryExpireAtTick >= now) {
            beamRenderer.render(cubePos, hold.primaryTargetLocal, matrices, vertexConsumers, shadedLight);
        }
        if (!hasLiveTarget
                && hold.trailingTargetLocal != null
                && hold.trailingExpireAtTick >= now
                && (hold.primaryTargetLocal == null
                || hold.trailingTargetLocal.squaredDistanceTo(hold.primaryTargetLocal) > 1.0E-6)) {
            beamRenderer.render(cubePos, hold.trailingTargetLocal, matrices, vertexConsumers, shadedLight);
        }

        if ((hold.primaryTargetLocal == null || hold.primaryExpireAtTick < now)
                && (hold.trailingTargetLocal == null || hold.trailingExpireAtTick < now)) {
            beamHoldStates.remove(key);
        }
    }

    private Vec3d getCubeLocalPos(QuarryRenderContext context, CubeBehaviorMode mode) {
        long key = context.quarryPos().asLong();
        QuarryRenderAnchors.InnerThirdBounds bounds = getStaticGeometryCache(context).cubeBounds;
        Vec3d center = bounds.center();
        CubeMotionState state = cubeMotionStates.get(key);
        if (state == null) {
            state = new CubeMotionState(center, center, -1L, 0);
            cubeMotionStates.put(key, state);
        }

        long worldTime = context.worldTime();
        state.target = resolveCubeTarget(context, bounds, state, mode, worldTime);
        state.target = applyTopLaserMinimumHeight(context, bounds, mode, state.target);

        double speedRatio = MathHelper.clamp(context.blocksPerSecond() / 40.0, 0.0, 1.0);
        double wanderBps = MathHelper.lerp(speedRatio, CUBE_MIN_WANDER_BPS, CUBE_MAX_WANDER_BPS);
        double maxMovePerTick = wanderBps / 20.0;
        state.position = moveToward(state.position, state.target, maxMovePerTick);
        return state.position;
    }

    private Vec3d resolveCubeTarget(QuarryRenderContext context,
                                    QuarryRenderAnchors.InnerThirdBounds bounds,
                                    CubeMotionState state,
                                    CubeBehaviorMode mode,
                                    long worldTime) {
        if (mode == CubeBehaviorMode.STATIC) {
            state.lastWaypointTick = worldTime;
            return bounds.center();
        }
        if (mode == CubeBehaviorMode.CENTER_HOVER) {
            double speedRatio = MathHelper.clamp(context.blocksPerSecond() / 40.0, 0.0, 1.0);
            double radius = MathHelper.lerp(speedRatio, CUBE_HOVER_RADIUS_MIN, CUBE_HOVER_RADIUS_MAX);
            double yAmp = MathHelper.lerp(speedRatio, CUBE_HOVER_Y_MIN, CUBE_HOVER_Y_MAX);
            double t = worldTime / 20.0;
            Vec3d center = bounds.center();
            double x = center.x + Math.cos(t * 1.7) * radius;
            double y = center.y + Math.sin(t * 1.3) * yAmp;
            double z = center.z + Math.sin(t * 1.9) * radius;
            state.lastWaypointTick = worldTime;
            return clampToBounds(bounds, new Vec3d(x, y, z));
        }

        if (state.lastWaypointTick < 0L
                || worldTime - state.lastWaypointTick >= CUBE_WAYPOINT_INTERVAL_TICKS
                || state.position.squaredDistanceTo(state.target) <= CUBE_REACHED_TARGET_SQ) {
            state.target = sampleInnerThirdTarget(context, bounds, state.waypointIndex++);
            state.lastWaypointTick = worldTime;
        }
        return state.target;
    }

    private Vec3d applyTopLaserMinimumHeight(QuarryRenderContext context,
                                             QuarryRenderAnchors.InnerThirdBounds bounds,
                                             CubeBehaviorMode mode,
                                             Vec3d proposedTarget) {
        if (mode != TOP_LASER_CUBE_MODE) return proposedTarget;
        BlockPos miningTarget = context.activeMiningTarget();
        if (miningTarget == null) return proposedTarget;

        // Keep cube center above the currently mined layer by at least this offset,
        // but remain inside the precomputed render bounds.
        double miningLayerCenterYLocal = context.toLocal(Vec3d.ofCenter(miningTarget)).y;
        double minAllowedY = miningLayerCenterYLocal + TOP_LASER_CUBE_MIN_ABOVE_LAYER;
        double clampedY = MathHelper.clamp(Math.max(proposedTarget.y, minAllowedY), bounds.minY(), bounds.maxY());
        return new Vec3d(proposedTarget.x, clampedY, proposedTarget.z);
    }

    private Vec3d clampToBounds(QuarryRenderAnchors.InnerThirdBounds bounds, Vec3d pos) {
        return new Vec3d(
                MathHelper.clamp(pos.x, bounds.minX(), bounds.maxX()),
                MathHelper.clamp(pos.y, bounds.minY(), bounds.maxY()),
                MathHelper.clamp(pos.z, bounds.minZ(), bounds.maxZ())
        );
    }

    private StaticGeometryCache getStaticGeometryCache(QuarryRenderContext context) {
        long key = context.quarryPos().asLong();
        StaticGeometryCache cache = staticGeometryCaches.get(key);
        if (cache == null || !cache.matches(context)) {
            cache = new StaticGeometryCache(
                    context.frameTopY(),
                    context.innerMinX(),
                    context.innerMaxX(),
                    context.innerMinY(),
                    context.innerMaxY(),
                    context.innerMinZ(),
                    context.innerMaxZ(),
                    QuarryRenderAnchors.gantrySpan(context, GANTRY_H, FRAME_CONTACT_EXTENSION),
                    QuarryRenderAnchors.innerThirdBounds(context, CUBE_TOP_MARGIN)
            );
            staticGeometryCaches.put(key, cache);
        }
        return cache;
    }

    private Vec3d sampleInnerThirdTarget(QuarryRenderContext context, QuarryRenderAnchors.InnerThirdBounds bounds, int waypointIndex) {
        long seedBase = context.quarryPos().asLong() ^ (waypointIndex * 341873128712L);
        double rx = hashUnit(seedBase ^ 0x1234ABCDL);
        double ry = hashUnit(seedBase ^ 0x9E3779B97F4A7C15L);
        double rz = hashUnit(seedBase ^ 0xC2B2AE3D27D4EB4FL);
        return new Vec3d(
                MathHelper.lerp(rx, bounds.minX(), bounds.maxX()),
                MathHelper.lerp(ry, bounds.minY(), bounds.maxY()),
                MathHelper.lerp(rz, bounds.minZ(), bounds.maxZ())
        ).lerp(bounds.center(), 0.15);
    }

    private double hashUnit(long value) {
        long x = value;
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (x & 0xFFFFFFFFFFFFL) / (double) 0x1000000000000L;
    }

    private Vec3d moveToward(Vec3d current, Vec3d target, double maxDistance) {
        Vec3d delta = target.subtract(current);
        double distance = delta.length();
        if (distance <= 1.0E-6 || distance <= maxDistance) return target;
        return current.add(delta.multiply(maxDistance / distance));
    }

    private int clampShadedLight(int light) {
        int block = Math.max(light & 0xFFFF, MIN_SHADED_LIGHT);
        int sky = Math.max((light >> 16) & 0xFFFF, MIN_SHADED_LIGHT);
        return block | (sky << 16);
    }

    private Vec3d getInterpolatedToolHeadPos(QuarryRenderContext context, float tickDelta) {
        return getInterpolatedComponentPos(context, InterpolatedComponent.TOOL_HEAD, context.toolHeadPos(), tickDelta);
    }

    private Vec3d getContinuousGantryHeadWorld(QuarryRenderContext context, float tickDelta) {
        long key = context.quarryPos().asLong();
        double now = context.worldTime() + clampTickDelta(tickDelta);
        GantryMotionState state = gantryMotionStates.get(key);
        if (state == null) {
            state = new GantryMotionState(context.toolHeadPos(), now);
            gantryMotionStates.put(key, state);
        }

        double deltaTicks = Math.max(0.0, Math.min(2.0, now - state.lastTick));
        state.lastTick = now;

        double movePerTick = Math.max(0.05, context.blocksPerSecond() / 20.0);
        Vec3d target = resolveContinuousGantryTargetWorld(context, movePerTick, context.forceHomeGantry());
        double maxDistance = movePerTick * deltaTicks;
        state.renderedWorld = moveToward(state.renderedWorld, target, maxDistance);
        return state.renderedWorld;
    }

    private Vec3d resolveContinuousGantryTargetWorld(QuarryRenderContext context, double movePerTick, boolean forceHome) {
        if (forceHome) return context.toolHeadOriginPos();

        BlockPos active = context.activeMiningTarget();
        if (active != null) return Vec3d.ofCenter(active);

        BlockPos next = context.waypointNext();
        if (next != null) {
            return Vec3d.ofCenter(next);
        }
        BlockPos current = context.waypointCurrent();
        if (current != null) return Vec3d.ofCenter(current);
        return context.toolHeadOriginPos();
    }

    private void resetContinuousGantryMotion(QuarryRenderContext context) {
        long key = context.quarryPos().asLong();
        gantryMotionStates.remove(key);
    }

    private Vec3d getInterpolatedComponentPos(QuarryRenderContext context,
                                              InterpolatedComponent component,
                                              Vec3d rawPos,
                                              float tickDelta) {
        if (!context.interpolationEnabled()) {
            InterpolationKey bypassKey = new InterpolationKey(context.quarryPos().asLong(), component);
            interpolationStates.remove(bypassKey);
            return rawPos;
        }
        InterpolationKey key = new InterpolationKey(context.quarryPos().asLong(), component);
        InterpolationState state = interpolationStates.get(key);
        if (state == null) {
            state = new InterpolationState(rawPos, rawPos);
            interpolationStates.put(key, state);
            return rawPos;
        }

        if (!approximatelyEquals(state.target, rawPos)) {
            state.target = rawPos;
        }

        if (state.rendered.squaredDistanceTo(state.target) <= SNAP_DISTANCE_SQ) {
            state.rendered = state.target;
            return state.target;
        }

        double alpha = 1.0 - Math.pow(1.0 - FOLLOW_STRENGTH, clampTickDelta(tickDelta) * 20.0);
        Vec3d interpolated = state.rendered.lerp(state.target, alpha);
        if (interpolated.squaredDistanceTo(state.target) <= INTERPOLATION_EPSILON_SQ) {
            state.rendered = state.target;
            return state.target;
        }

        state.rendered = interpolated;
        return interpolated;
    }

    private void resetInterpolatedToolHead(QuarryRenderContext context) {
        InterpolationKey key = new InterpolationKey(context.quarryPos().asLong(), InterpolatedComponent.TOOL_HEAD);
        Vec3d rawPos = context.toolHeadPos();
        InterpolationState state = interpolationStates.get(key);
        if (state == null) {
            interpolationStates.put(key, new InterpolationState(rawPos, rawPos));
            return;
        }
        state.target = rawPos;
        state.rendered = rawPos;
    }

    private float clampTickDelta(float tickDelta) {
        if (tickDelta < 0.0f) return 0.0f;
        return Math.min(tickDelta, 1.0f);
    }

    private boolean approximatelyEquals(Vec3d left, Vec3d right) {
        return left.squaredDistanceTo(right) < 1.0E-8;
    }

    private void pruneInterpolationStates(QuarryBlockEntity activeBe) {
        if (interpolationStates.size() < 32) return;

        long activeKey = activeBe.getPos().asLong();
        Iterator<InterpolationKey> iterator = interpolationStates.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().quarryPackedPos != activeKey) {
                iterator.remove();
            }
        }
    }

    private void pruneCubeMotionStates(QuarryBlockEntity activeBe) {
        if (cubeMotionStates.size() < 32) return;

        long activeKey = activeBe.getPos().asLong();
        Iterator<Long> iterator = cubeMotionStates.keySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().equals(activeKey)) {
                iterator.remove();
            }
        }
    }

    private void pruneFrameThrowGhostStates(QuarryBlockEntity activeBe) {
        if (frameThrowGhosts.size() < 32) return;

        long activeKey = activeBe.getPos().asLong();
        Iterator<Long> iterator = frameThrowGhosts.keySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().equals(activeKey)) {
                iterator.remove();
            }
        }
    }

    private void pruneBeamHoldStates(QuarryBlockEntity activeBe) {
        if (beamHoldStates.size() < 32) return;

        long activeKey = activeBe.getPos().asLong();
        Iterator<Long> iterator = beamHoldStates.keySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().equals(activeKey)) {
                iterator.remove();
            }
        }
    }

    private void pruneGantryMotionStates(QuarryBlockEntity activeBe) {
        if (gantryMotionStates.size() < 32) return;

        long activeKey = activeBe.getPos().asLong();
        Iterator<Long> iterator = gantryMotionStates.keySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().equals(activeKey)) {
                iterator.remove();
            }
        }
    }

    private void pruneStaticGeometryCaches(QuarryBlockEntity activeBe) {
        if (staticGeometryCaches.size() < 32) return;

        long activeKey = activeBe.getPos().asLong();
        Iterator<Long> iterator = staticGeometryCaches.keySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().equals(activeKey)) {
                iterator.remove();
            }
        }
    }

    private static final class InterpolationState {
        private Vec3d target;
        private Vec3d rendered;

        private InterpolationState(Vec3d target, Vec3d rendered) {
            this.target = target;
            this.rendered = rendered;
        }
    }

    private enum InterpolatedComponent {
        TOOL_HEAD
    }

    private record InterpolationKey(long quarryPackedPos, InterpolatedComponent component) {

        @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof InterpolationKey other)) return false;
                return quarryPackedPos == other.quarryPackedPos && component == other.component;
            }

            @Override
            public int hashCode() {
                int result = Long.hashCode(quarryPackedPos);
                result = 31 * result + component.hashCode();
                return result;
            }
        }

    private static final class CubeMotionState {
        private Vec3d position;
        private Vec3d target;
        private long lastWaypointTick;
        private int waypointIndex;

        private CubeMotionState(Vec3d position, Vec3d target, long lastWaypointTick, int waypointIndex) {
            this.position = position;
            this.target = target;
            this.lastWaypointTick = lastWaypointTick;
            this.waypointIndex = waypointIndex;
        }
    }

    private static final class FrameThrowGhost {
        private final long targetPacked;
        private final Vec3d start;
        private Vec3d end;
        private final double launchTick;
        private final double durationTicks;

        private FrameThrowGhost(long targetPacked, Vec3d start, Vec3d end, double launchTick, double durationTicks) {
            this.targetPacked = targetPacked;
            this.start = start;
            this.end = end;
            this.launchTick = launchTick;
            this.durationTicks = durationTicks;
        }
    }

    private static final class BeamHoldState {
        private Vec3d primaryTargetLocal;
        private double primaryExpireAtTick = Double.NEGATIVE_INFINITY;
        private Vec3d trailingTargetLocal;
        private double trailingExpireAtTick = Double.NEGATIVE_INFINITY;
    }

    private static final class GantryMotionState {
        private Vec3d renderedWorld;
        private double lastTick;

        private GantryMotionState(Vec3d renderedWorld, double lastTick) {
            this.renderedWorld = renderedWorld;
            this.lastTick = lastTick;
        }
    }

    private record StaticGeometryCache(int frameTopY, int innerMinX, int innerMaxX, int innerMinY, int innerMaxY,
                                       int innerMinZ, int innerMaxZ, QuarryRenderAnchors.GantrySpan gantrySpan,
                                       QuarryRenderAnchors.InnerThirdBounds cubeBounds) {

        private boolean matches(QuarryRenderContext context) {
                return frameTopY == context.frameTopY()
                        && innerMinX == context.innerMinX()
                        && innerMaxX == context.innerMaxX()
                        && innerMinY == context.innerMinY()
                        && innerMaxY == context.innerMaxY()
                        && innerMinZ == context.innerMinZ()
                        && innerMaxZ == context.innerMaxZ();
            }
        }

    private enum RenderPhase {
        NONE,
        DEBUG_PREVIEW,
        FRAME_WORK,
        TOP_LASER,
        SUPPRESSED_RETURN,
        GANTRY
    }

    private enum CubeBehaviorMode {
        STATIC,
        CENTER_HOVER,
        WANDER_INNER_THIRD
    }

    private record RenderDecision(RenderPhase phase, boolean renderAny, boolean clearFrameGhosts) {}

    @Override
    public boolean rendersOutsideBoundingBox(QuarryBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 256;
    }

}
