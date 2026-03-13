package com.errorsys.quarry_reforged.client.render;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.client.debug.QuarryRenderTraceCapture;
import com.errorsys.quarry_reforged.client.net.QuarryMotionClientState;
import com.errorsys.quarry_reforged.client.render.component.BeamRenderer;
import com.errorsys.quarry_reforged.client.render.component.GantryRenderer;
import com.errorsys.quarry_reforged.client.render.component.LaserCubeRenderer;
import com.errorsys.quarry_reforged.client.render.component.PipeRenderer;
import com.errorsys.quarry_reforged.client.render.component.QuarryRenderMaterialPolicy;
import com.errorsys.quarry_reforged.client.render.component.QuarryRenderPrimitives;
import com.errorsys.quarry_reforged.client.render.component.ToolHeadRenderer;
import com.errorsys.quarry_reforged.config.ModConfig;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.block.QuarryBlock;
import com.errorsys.quarry_reforged.content.block.QuarryFrameBlock;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.util.QuarryAreaRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

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
    private static final double CUBE_MIN_WANDER_BPS = 0.8;
    private static final double CUBE_MAX_WANDER_BPS = 1.2;
    private static final double CUBE_TOP_MARGIN = 0.2;
    private static final double TOP_LASER_CUBE_MIN_ABOVE_LAYER = 0.65;
    private static final double REDISCOVERY_VOLUME_WIDTH_RATIO = 2.0 / 3.0;
    private static final double REDISCOVERY_VOLUME_HEIGHT = 5.0;
    private static final double REDISCOVERY_VERTICAL_SHIFT_TRIGGER = 15.0;
    private static final int CUBE_TELEPORT_PARTICLES = 20;
    private static final CubeBehaviorMode TOP_LASER_CUBE_MODE = CubeBehaviorMode.WANDER_INNER_THIRD;
    private static final CubeBehaviorMode DEBUG_PREVIEW_CUBE_MODE = CubeBehaviorMode.STATIC;
    private static final double FRAME_THROW_ARC_SCALE = 0.12;
    private static final double FRAME_THROW_MIN_ARC = 0.35;
    private static final double FRAME_THROW_MIN_TICKS = 4.0;
    private static final double FRAME_THROW_MAX_TICKS = 14.0;
    private static final long FRAME_THROW_MAX_ARRIVAL_WAIT_MS = 3000L;
    private static final int FRAME_THROW_PREVIEW_MIN_COUNT = 8;
    private static final double LASER_BEAM_PERSIST_TICKS = 8.0;
    private static final double INTERPOLATION_MIN_DURATION_TICKS = 1.0;
    private static final double INTERPOLATION_MAX_DURATION_TICKS = 4.0;
    private static final double GANTRY_RENDER_SMOOTH_TICKS = 0.6;
    private static final double PREVIEW_BEAM_END_EXTEND = 1.0 / 16.0;
    private static final double PREVIEW_EDGE_OUTSET = 1.0 / 1024.0;

    private final Map<InterpolationKey, InterpolationState> interpolationStates = new HashMap<>();
    private final Map<Long, CubeMotionState> cubeMotionStates = new HashMap<>();
    private final Map<Long, Map<Long, FrameThrowGhost>> frameThrowGhosts = new HashMap<>();
    private final Map<Long, BeamHoldState> beamHoldStates = new HashMap<>();
    private final Map<Long, Vec3d> rediscoveryFrozenGantryHeads = new HashMap<>();
    private final Map<Long, GantryRenderSmoothingState> gantryRenderSmoothingStates = new HashMap<>();
    private final Map<Long, CubePhaseFxState> cubePhaseFxStates = new HashMap<>();
    private final Map<Long, StaticGeometryCache> staticGeometryCaches = new HashMap<>();
    private final QuarryRenderMaterialPolicy materialPolicy = new QuarryRenderMaterialPolicy();
    private final QuarryRenderPrimitives primitives = new QuarryRenderPrimitives();
    private final GantryRenderer gantryRenderer = new GantryRenderer(primitives, materialPolicy, GANTRY_TEXTURE, GANTRY_W, GANTRY_H);
    private final ToolHeadRenderer toolHeadRenderer = new ToolHeadRenderer(primitives, materialPolicy, TOOL_HEAD_TEXTURE, TOOL_HEAD_W, TOOL_HEAD_H);
    private final PipeRenderer pipeRenderer = new PipeRenderer(materialPolicy, GANTRY_TEXTURE, PIPE_W);
    private final LaserCubeRenderer cubeRenderer = new LaserCubeRenderer(primitives, materialPolicy, CUBE_TEXTURE, LASER_CUBE);
    private final BeamRenderer beamRenderer = new BeamRenderer(materialPolicy, LASER_TEXTURE, LASER_BEAM_W);

    public QuarryBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    private static boolean isRenderDebugLoggingEnabled() {
        return ModConfig.DATA.enableStateDebugLogging;
    }

    private static void debugRenderLog(QuarryRenderContext context, String format, Object... args) {
        if (!isRenderDebugLoggingEnabled()) return;
        String rediscoveryId = context.quarryPos().toShortString() + "@" + context.worldTime();
        String prefix = "[quarry-render {} rid={}] ";
        Object[] withContext = new Object[args.length + 2];
        withContext[0] = context.quarryPos();
        withContext[1] = rediscoveryId;
        System.arraycopy(args, 0, withContext, 2, args.length);
        QuarryReforged.LOGGER.info(prefix + format, withContext);
    }

    @Override
    public void render(QuarryBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        QuarryRenderContext context = QuarryRenderContext.from(be);
        if (!context.areaLocked()) {
            if (be.hasPlacementAreaPreviewClient()) {
                renderPlacementAreaPreview(be, matrices, vertexConsumers, light);
            }
            return;
        }
        pruneInterpolationStates(be);
        pruneCubeMotionStates(be);
        pruneFrameThrowGhostStates(be);
        pruneBeamHoldStates(be);
        pruneStaticGeometryCaches(be);
        pruneAuxRendererStates(be);

        RenderDecision decision = decideRender(context);
        updateCubePhaseFx(context, decision.phase());
        if (decision.phase() != RenderPhase.GANTRY) {
            gantryRenderSmoothingStates.remove(be.getPos().asLong());
        }
        if (decision.phase() != RenderPhase.LASER_ACTIVE && decision.phase() != RenderPhase.LASER_IDLE) {
            rediscoveryFrozenGantryHeads.remove(be.getPos().asLong());
        }
        if (!decision.renderAny()) {
            if (be.hasPlacementAreaPreviewClient()) {
                renderPlacementAreaPreview(be, matrices, vertexConsumers, light);
            }
            return;
        }
        if (decision.clearFrameGhosts()) {
            frameThrowGhosts.remove(be.getPos().asLong());
        }

        int shadedLight = clampShadedLight(light);
        renderPhase(decision.phase(), context, be, tickDelta, matrices, vertexConsumers, shadedLight, overlay);
    }

    private void renderPlacementAreaPreview(QuarryBlockEntity be,
                                            MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers,
                                            int light) {
        QuarryAreaRegistry.AreaBounds bounds = be.getPlacementAreaPreviewBoundsClient();
        if (bounds == null) return;

        BlockPos origin = be.getPos();
        int shadedLight = clampShadedLight(light);

        double minX = bounds.minX() - PREVIEW_EDGE_OUTSET;
        double minY = bounds.minY() - PREVIEW_EDGE_OUTSET;
        double minZ = bounds.minZ() - PREVIEW_EDGE_OUTSET;
        double maxX = bounds.maxX() + PREVIEW_EDGE_OUTSET;
        double maxY = bounds.maxY() + PREVIEW_EDGE_OUTSET;
        double maxZ = bounds.maxZ() + PREVIEW_EDGE_OUTSET;

        Vec3d p000 = toLocalCenter(origin, minX, minY, minZ);
        Vec3d p001 = toLocalCenter(origin, minX, minY, maxZ);
        Vec3d p010 = toLocalCenter(origin, minX, maxY, minZ);
        Vec3d p011 = toLocalCenter(origin, minX, maxY, maxZ);
        Vec3d p100 = toLocalCenter(origin, maxX, minY, minZ);
        Vec3d p101 = toLocalCenter(origin, maxX, minY, maxZ);
        Vec3d p110 = toLocalCenter(origin, maxX, maxY, minZ);
        Vec3d p111 = toLocalCenter(origin, maxX, maxY, maxZ);

        renderExpandedBeam(p000, p001, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p001, p101, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p101, p100, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p100, p000, matrices, vertexConsumers, shadedLight);

        renderExpandedBeam(p010, p011, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p011, p111, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p111, p110, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p110, p010, matrices, vertexConsumers, shadedLight);

        renderExpandedBeam(p000, p010, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p001, p011, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p100, p110, matrices, vertexConsumers, shadedLight);
        renderExpandedBeam(p101, p111, matrices, vertexConsumers, shadedLight);
    }

    private void renderExpandedBeam(Vec3d from,
                                    Vec3d to,
                                    MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    int shadedLight) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length <= 1.0E-6) return;
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

    private RenderDecision decideRender(QuarryRenderContext context) {
        RenderPhase phase = switch (context.renderPhase()) {
            case DEBUG_PREVIEW -> RenderPhase.DEBUG_PREVIEW;
            case RENDER_FRAME_WORK -> RenderPhase.FRAME_WORK;
            case RENDER_LASER_ACTIVE, RENDER_REMOVE_FRAME -> RenderPhase.LASER_ACTIVE;
            case RENDER_LASER_IDLE -> RenderPhase.LASER_IDLE;
            case RENDER_GANTRY_MINE, RENDER_GANTRY_TRAVEL, RENDER_GANTRY_FREEZE, RENDER_FINISH_HOME -> RenderPhase.GANTRY;
            case RENDER_NONE -> RenderPhase.NONE;
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
            }
            case DEBUG_PREVIEW -> renderDebugPreview(context, tickDelta, matrices, vertexConsumers, shadedLight, overlay);
            case FRAME_WORK -> {
                resetInterpolatedToolHead(context);
                renderFrameBuildMode(context, be, tickDelta, matrices, vertexConsumers, shadedLight, overlay);
            }
            case LASER_ACTIVE -> {
                resetInterpolatedToolHead(context);
                renderTopLaserMode(context, tickDelta, matrices, vertexConsumers, shadedLight, true);
                if (context.rediscoveryDraining()) {
                    renderFrozenRediscoveryGantry(context, matrices, vertexConsumers, shadedLight);
                } else {
                    rediscoveryFrozenGantryHeads.remove(context.quarryPos().asLong());
                }
            }
            case LASER_IDLE -> {
                resetInterpolatedToolHead(context);
                renderTopLaserMode(context, tickDelta, matrices, vertexConsumers, shadedLight, false);
                rediscoveryFrozenGantryHeads.remove(context.quarryPos().asLong());
            }
            case GANTRY -> renderGantryMode(context, tickDelta, matrices, vertexConsumers, shadedLight);
        }
    }

    private void renderFrozenRediscoveryGantry(QuarryRenderContext context,
                                               MatrixStack matrices,
                                               VertexConsumerProvider vertexConsumers,
                                               int shadedLight) {
        long key = context.quarryPos().asLong();
        Vec3d frozenHeadWorld = rediscoveryFrozenGantryHeads.computeIfAbsent(key, ignored -> context.toolHeadPos());
        Vec3d headPos = QuarryRenderAnchors.toolHeadLocal(context, frozenHeadWorld, TOOL_HEAD_Y_OFFSET);
        StaticGeometryCache staticCache = getStaticGeometryCache(context);
        QuarryRenderAnchors.GantrySpan gantrySpan = staticCache.gantrySpan;
        gantryRenderer.render(gantrySpan, headPos, matrices, vertexConsumers, shadedLight);
        pipeRenderer.render(headPos.x, headPos.z, toolHeadRenderer.topY(headPos), gantrySpan.minY(), matrices, vertexConsumers, shadedLight);
        toolHeadRenderer.render(headPos, matrices, vertexConsumers, shadedLight);
    }

    private void renderGantryMode(QuarryRenderContext context,
                                  float tickDelta,
                                  MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers,
                                  int shadedLight) {
        Vec3d rawHeadWorld = context.forceHomeGantry()
                ? context.toolHeadOriginPos()
                : getInterpolatedToolHeadPos(context, tickDelta);
        Vec3d headWorld = smoothGantryHeadRender(context, rawHeadWorld, tickDelta);
        Vec3d headPos = QuarryRenderAnchors.toolHeadLocal(context, headWorld, TOOL_HEAD_Y_OFFSET);
        StaticGeometryCache staticCache = getStaticGeometryCache(context);
        QuarryRenderAnchors.GantrySpan gantrySpan = staticCache.gantrySpan;
        gantryRenderer.render(gantrySpan, headPos, matrices, vertexConsumers, shadedLight);
        pipeRenderer.render(headPos.x, headPos.z, toolHeadRenderer.topY(headPos), gantrySpan.minY(), matrices, vertexConsumers, shadedLight);
        toolHeadRenderer.render(headPos, matrices, vertexConsumers, shadedLight);
    }

    private Vec3d smoothGantryHeadRender(QuarryRenderContext context, Vec3d rawHeadWorld, float tickDelta) {
        long key = context.quarryPos().asLong();
        double nowTick = ((double) context.worldTime()) + (double) clampTickDelta(tickDelta);
        GantryRenderSmoothingState state = gantryRenderSmoothingStates.get(key);
        if (state == null) {
            state = new GantryRenderSmoothingState(rawHeadWorld, nowTick);
            gantryRenderSmoothingStates.put(key, state);
            return rawHeadWorld;
        }

        double elapsedTicks = Math.max(0.0, nowTick - state.lastRenderTick);
        if (elapsedTicks <= 1.0E-6) {
            return state.lastHeadWorld;
        }

        double alpha = 1.0 - Math.exp(-elapsedTicks / GANTRY_RENDER_SMOOTH_TICKS);
        Vec3d smoothed = state.lastHeadWorld.lerp(rawHeadWorld, MathHelper.clamp(alpha, 0.0, 1.0));
        state.lastHeadWorld = smoothed;
        state.lastRenderTick = nowTick;
        return smoothed;
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

        Vec3d cubePos = getCubeLocalPos(context, DEBUG_PREVIEW_CUBE_MODE, tickDelta);
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
        double worldTime = ((double) context.worldTime()) + (double) clampTickDelta(tickDelta);
        long renderNowMs = Util.getMeasuringTimeMs();
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
                double scheduledLaunchTick = (worldTime + (i * placementIntervalTicks)) - duration;
                double initialAgeTicks = MathHelper.clamp(worldTime - scheduledLaunchTick, 0.0, duration);
                ghost = new FrameThrowGhost(packed, start, end, duration, initialAgeTicks, renderNowMs);
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
                    && ghost.ageTicks >= (ghost.durationTicks * 0.9)) {
                iterator.remove();
                continue;
            }
            long deltaMs = Math.max(0L, Math.min(250L, renderNowMs - ghost.lastRenderMs));
            ghost.lastRenderMs = renderNowMs;
            ghost.ageTicks += deltaMs / 50.0;

            if (ghost.ageTicks >= ghost.durationTicks) {
                ghost.ageTicks = ghost.durationTicks;
                if (ghost.arrivedAtMs < 0L) {
                    ghost.arrivedAtMs = renderNowMs;
                } else if (renderNowMs - ghost.arrivedAtMs > FRAME_THROW_MAX_ARRIVAL_WAIT_MS) {
                    iterator.remove();
                    continue;
                }
            }
            if (ghost.ageTicks < 0.0) continue;

            double t = MathHelper.clamp(ghost.ageTicks / Math.max(1.0, ghost.durationTicks), 0.0, 1.0);
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
                                    int shadedLight,
                                    boolean beamEnabled) {
        long key = context.quarryPos().asLong();
        double now = ((double) context.worldTime()) + (double) clampTickDelta(tickDelta);
        Vec3d cubePos = getCubeLocalPos(context, TOP_LASER_CUBE_MODE, tickDelta);
        CubeMotionState motionState = cubeMotionStates.get(key);
        if (context.rediscoveryVolumeDebugRenderEnabled()
                && context.rediscoveryDraining()
                && motionState != null
                && motionState.rediscoveryVolume != null) {
            renderRediscoveryVolumeDebug(motionState.rediscoveryVolume, motionState.target, matrices, vertexConsumers);
        }
        CubePhaseFxState fx = cubePhaseFxStates.get(key);
        if (fx != null) {
            fx.lastLocalPos = cubePos;
        }
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
        if (!beamEnabled || (context.rediscoveryDraining() && motionState != null && motionState.rediscoveryTravelActive)) {
            beamHoldStates.remove(key);
            return;
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

    private Vec3d getCubeLocalPos(QuarryRenderContext context, CubeBehaviorMode mode, float tickDelta) {
        long key = context.quarryPos().asLong();
        QuarryRenderAnchors.InnerThirdBounds bounds = getStaticGeometryCache(context).cubeBounds;
        Vec3d center = bounds.center();
        CubeMotionState state = cubeMotionStates.get(key);
        if (state == null) {
            state = new CubeMotionState(center, center, -1L, 0);
            cubeMotionStates.put(key, state);
        }

        long worldTime = context.worldTime();
        if (mode == TOP_LASER_CUBE_MODE && context.rediscoveryDraining()) {
            if (state.lastRediscoveryUpdateTick != worldTime) {
                state.previousPosition = state.position;
                long elapsedTicks = state.lastRediscoveryUpdateTick == Long.MIN_VALUE
                        ? 1L
                        : Math.max(1L, worldTime - state.lastRediscoveryUpdateTick);
                updateRediscoveryCubeState(context, state, worldTime, elapsedTicks);
                state.lastRediscoveryUpdateTick = worldTime;
            }
            return interpolateCubePosition(state, tickDelta);
        }
        if (state.rediscoveryDraining) {
            debugRenderLog(context, "rediscovery render exit at tick={} cubePos={}", worldTime, state.position);
        }
        state.rediscoveryDraining = false;
        state.lastRediscoveryUpdateTick = Long.MIN_VALUE;
        state.rediscoveryVolumeOriginY = Double.NaN;
        state.lastVolumeOriginY = Double.NaN;
        state.target = resolveCubeTarget(context, bounds, state, mode, worldTime);
        state.target = applyTopLaserMinimumHeight(context, bounds, mode, state.target);

        if (mode != CubeBehaviorMode.STATIC && state.lastCubeMotionUpdateTick != worldTime) {
            state.previousPosition = state.position;
            long elapsedTicks = state.lastCubeMotionUpdateTick == Long.MIN_VALUE
                    ? 1L
                    : Math.max(1L, worldTime - state.lastCubeMotionUpdateTick);
            double speedRatio = MathHelper.clamp(context.blocksPerSecond() / Math.max(1.0, ModConfig.DATA.maxBlocksPerSecond), 0.0, 1.0);
            double wanderBps = MathHelper.lerp(speedRatio, CUBE_MIN_WANDER_BPS, CUBE_MAX_WANDER_BPS);
            double maxMovePerTick = wanderBps / 20.0;
            state.position = moveToward(state.position, state.target, maxMovePerTick * elapsedTicks);
            state.lastCubeMotionUpdateTick = worldTime;
        }
        return interpolateCubePosition(state, tickDelta);
    }

    private Vec3d interpolateCubePosition(CubeMotionState state, float tickDelta) {
        double alpha = clampTickDelta(tickDelta);
        return state.previousPosition.lerp(state.position, alpha);
    }

    private void updateRediscoveryCubeState(QuarryRenderContext context, CubeMotionState state, long worldTime, long elapsedTicks) {
        RediscoveryCubeVolume volume = computeRediscoveryCubeVolume(context, state);
        state.rediscoveryVolume = volume;
        BlockPos miningTarget = context.activeMiningTarget();
        Vec3d serverCubePos = context.toLocal(context.laserCubeWorldPos());
        Vec3d desiredPos = miningTarget == null
                ? volume.origin
                : clampToRediscoveryVolume(volume, context.toLocal(Vec3d.ofCenter(miningTarget)));
        boolean enteringRediscovery = !state.rediscoveryDraining;
        state.rediscoveryDraining = true;
        if (enteringRediscovery) {
            // Enter rediscovery at the authoritative server cube position.
            state.position = serverCubePos;
            state.target = serverCubePos;
            state.rediscoveryTravelActive = false;
            state.lastWaypointTick = worldTime;
            debugRenderLog(context, "rediscovery render enter tick={} cube forced in-volume at {} target={}",
                    worldTime, state.position, miningTarget);
        }
        if (Double.isNaN(state.lastVolumeOriginY) || Math.abs(state.lastVolumeOriginY - volume.origin.y) > 1.0E-6) {
            debugRenderLog(context, "rediscovery volume origin shift tick={} y:{}->{} target={}",
                    worldTime, state.lastVolumeOriginY, volume.origin.y, miningTarget);
            state.lastVolumeOriginY = volume.origin.y;
        }
        if (state.lastVerticalTravelFlag != context.rediscoveryLaserVerticalTravelActive()) {
            debugRenderLog(context, "rediscovery vertical travel flag tick={} {}->{}",
                    worldTime, state.lastVerticalTravelFlag, context.rediscoveryLaserVerticalTravelActive());
            state.lastVerticalTravelFlag = context.rediscoveryLaserVerticalTravelActive();
        }

        if (context.rediscoveryLaserVerticalTravelActive()) {
            // During vertical travel, render follows server-auth cube position.
            state.position = serverCubePos;
            state.target = serverCubePos;
            state.rediscoveryTravelActive = true;
            return;
        }

        boolean wasTraveling = state.rediscoveryTravelActive;
        boolean inside = isInsideRediscoveryVolume(volume, state.position);
        double distanceToDesiredSq = state.position.squaredDistanceTo(desiredPos);
        boolean shouldTravelToVolume = !inside && distanceToDesiredSq > CUBE_REACHED_TARGET_SQ;
        boolean shouldTravelForServerVertical = !inside
                && context.rediscoveryLaserVerticalTravelActive()
                && distanceToDesiredSq > CUBE_REACHED_TARGET_SQ;
        state.rediscoveryTravelActive = shouldTravelToVolume || shouldTravelForServerVertical;
        if (!wasTraveling && state.rediscoveryTravelActive) {
            String reason = context.rediscoveryLaserVerticalTravelActive() ? "server_vertical_travel" : "outside_volume";
            debugRenderLog(context, "rediscovery cube travel start tick={} reason={} cubePos={} targetPos={} volumeY=[{},{}]",
                    worldTime, reason, state.position, desiredPos, volume.minY, volume.maxY);
        }

        if (state.rediscoveryTravelActive) {
            double speedRatio = MathHelper.clamp(context.blocksPerSecond() / Math.max(1.0, ModConfig.DATA.maxBlocksPerSecond), 0.0, 1.0);
            double travelBps = MathHelper.lerp(speedRatio, CUBE_MIN_WANDER_BPS, CUBE_MAX_WANDER_BPS);
            double maxMove = (travelBps * 1.5) / 20.0;
            state.position = moveToward(state.position, desiredPos, maxMove * elapsedTicks);
            if (isInsideRediscoveryVolume(volume, state.position)
                    || state.position.squaredDistanceTo(desiredPos) <= CUBE_REACHED_TARGET_SQ) {
                state.rediscoveryTravelActive = false;
                state.lastWaypointTick = worldTime;
                state.target = state.position;
                debugRenderLog(context, "rediscovery cube travel stop tick={} cubePos={} volumeY=[{},{}]",
                        worldTime, state.position, volume.minY, volume.maxY);
            }
            return;
        }

        if (state.lastWaypointTick < 0L
                || worldTime - state.lastWaypointTick >= CUBE_WAYPOINT_INTERVAL_TICKS
                || state.position.squaredDistanceTo(state.target) <= CUBE_REACHED_TARGET_SQ) {
            state.target = sampleRediscoveryVolumeTarget(context, volume, state.waypointIndex++);
            state.lastWaypointTick = worldTime;
        }
        double speedRatio = MathHelper.clamp(context.blocksPerSecond() / Math.max(1.0, ModConfig.DATA.maxBlocksPerSecond), 0.0, 1.0);
        double wanderBps = MathHelper.lerp(speedRatio, CUBE_MIN_WANDER_BPS, CUBE_MAX_WANDER_BPS);
        state.position = moveToward(state.position, state.target, (wanderBps / 20.0) * elapsedTicks);
    }

    private RediscoveryCubeVolume computeRediscoveryCubeVolume(QuarryRenderContext context, CubeMotionState state) {
        double centerX = (context.innerMinX() + context.innerMaxX() + 1) * 0.5 - context.quarryPos().getX();
        double centerY = (context.innerMinY() + context.innerMaxY() + 1) * 0.5 - context.quarryPos().getY();
        double centerZ = (context.innerMinZ() + context.innerMaxZ() + 1) * 0.5 - context.quarryPos().getZ();
        double originY = Double.isNaN(state.rediscoveryVolumeOriginY) ? centerY : state.rediscoveryVolumeOriginY;

        BlockPos miningTarget = context.activeMiningTarget();
        if (miningTarget != null) {
            double targetY = context.toLocal(Vec3d.ofCenter(miningTarget)).y;
            if (Math.abs(targetY - originY) > REDISCOVERY_VERTICAL_SHIFT_TRIGGER) {
                originY = targetY;
            }
        }
        state.rediscoveryVolumeOriginY = originY;

        double miningWidthX = context.innerMaxX() - context.innerMinX() + 1.0;
        double miningWidthZ = context.innerMaxZ() - context.innerMinZ() + 1.0;
        double halfX = Math.max(0.2, (miningWidthX * REDISCOVERY_VOLUME_WIDTH_RATIO) * 0.5);
        double halfZ = Math.max(0.2, (miningWidthZ * REDISCOVERY_VOLUME_WIDTH_RATIO) * 0.5);
        double halfY = REDISCOVERY_VOLUME_HEIGHT * 0.5;

        Vec3d origin = new Vec3d(centerX, originY, centerZ);
        return new RediscoveryCubeVolume(
                origin,
                centerX - halfX, centerX + halfX,
                originY - halfY, originY + halfY,
                centerZ - halfZ, centerZ + halfZ
        );
    }

    private boolean isInsideRediscoveryVolume(RediscoveryCubeVolume volume, Vec3d pos) {
        return pos.x >= volume.minX && pos.x <= volume.maxX
                && pos.y >= volume.minY && pos.y <= volume.maxY
                && pos.z >= volume.minZ && pos.z <= volume.maxZ;
    }

    private Vec3d clampToRediscoveryVolume(RediscoveryCubeVolume volume, Vec3d pos) {
        return new Vec3d(
                MathHelper.clamp(pos.x, volume.minX, volume.maxX),
                MathHelper.clamp(pos.y, volume.minY, volume.maxY),
                MathHelper.clamp(pos.z, volume.minZ, volume.maxZ)
        );
    }

    private Vec3d sampleRediscoveryVolumeTarget(QuarryRenderContext context, RediscoveryCubeVolume volume, int waypointIndex) {
        long seedBase = context.quarryPos().asLong() ^ (waypointIndex * 341873128712L);
        double rx = hashUnit(seedBase ^ 0x1234ABCDL);
        double ry = hashUnit(seedBase ^ 0x9E3779B97F4A7C15L);
        double rz = hashUnit(seedBase ^ 0xC2B2AE3D27D4EB4FL);
        return new Vec3d(
                MathHelper.lerp(rx, volume.minX, volume.maxX),
                MathHelper.lerp(ry, volume.minY, volume.maxY),
                MathHelper.lerp(rz, volume.minZ, volume.maxZ)
        ).lerp(volume.origin, 0.12);
    }

    private void renderRediscoveryVolumeDebug(RediscoveryCubeVolume volume,
                                              @Nullable Vec3d target,
                                              MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();
        float r = 1.0f;
        float g = 0.1f;
        float b = 0.1f;
        float a = 0.45f;

        addDebugLine(consumer, position, normal, volume.minX, volume.minY, volume.minZ, volume.maxX, volume.minY, volume.minZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.maxX, volume.minY, volume.minZ, volume.maxX, volume.minY, volume.maxZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.maxX, volume.minY, volume.maxZ, volume.minX, volume.minY, volume.maxZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.minX, volume.minY, volume.maxZ, volume.minX, volume.minY, volume.minZ, r, g, b, a);

        addDebugLine(consumer, position, normal, volume.minX, volume.maxY, volume.minZ, volume.maxX, volume.maxY, volume.minZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.maxX, volume.maxY, volume.minZ, volume.maxX, volume.maxY, volume.maxZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.maxX, volume.maxY, volume.maxZ, volume.minX, volume.maxY, volume.maxZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.minX, volume.maxY, volume.maxZ, volume.minX, volume.maxY, volume.minZ, r, g, b, a);

        addDebugLine(consumer, position, normal, volume.minX, volume.minY, volume.minZ, volume.minX, volume.maxY, volume.minZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.maxX, volume.minY, volume.minZ, volume.maxX, volume.maxY, volume.minZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.maxX, volume.minY, volume.maxZ, volume.maxX, volume.maxY, volume.maxZ, r, g, b, a);
        addDebugLine(consumer, position, normal, volume.minX, volume.minY, volume.maxZ, volume.minX, volume.maxY, volume.maxZ, r, g, b, a);

        double dot = 0.08;
        addDebugLine(consumer, position, normal, volume.origin.x - dot, volume.origin.y, volume.origin.z, volume.origin.x + dot, volume.origin.y, volume.origin.z, 1.0f, 0.0f, 0.0f, 1.0f);
        addDebugLine(consumer, position, normal, volume.origin.x, volume.origin.y - dot, volume.origin.z, volume.origin.x, volume.origin.y + dot, volume.origin.z, 1.0f, 0.0f, 0.0f, 1.0f);
        addDebugLine(consumer, position, normal, volume.origin.x, volume.origin.y, volume.origin.z - dot, volume.origin.x, volume.origin.y, volume.origin.z + dot, 1.0f, 0.0f, 0.0f, 1.0f);
        if (target != null) {
            double targetDot = 0.11;
            addDebugLine(consumer, position, normal, target.x - targetDot, target.y, target.z, target.x + targetDot, target.y, target.z, 1.0f, 0.95f, 0.25f, 1.0f);
            addDebugLine(consumer, position, normal, target.x, target.y - targetDot, target.z, target.x, target.y + targetDot, target.z, 1.0f, 0.95f, 0.25f, 1.0f);
            addDebugLine(consumer, position, normal, target.x, target.y, target.z - targetDot, target.x, target.y, target.z + targetDot, 1.0f, 0.95f, 0.25f, 1.0f);
        }
    }

    private void addDebugLine(VertexConsumer consumer,
                              Matrix4f position,
                              Matrix3f normal,
                              double x1, double y1, double z1,
                              double x2, double y2, double z2,
                              float r, float g, float b, float a) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float len = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0.0f;
            ny = 1.0f;
            nz = 0.0f;
        }
        consumer.vertex(position, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(normal, nx, ny, nz).next();
        consumer.vertex(position, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(normal, nx, ny, nz).next();
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
        // During rediscovery drain, allow the cube to descend below the frame-inner floor bounds
        // so vertical travel toward deep targets remains visible.
        double minYBound = context.rediscoveryDraining() ? Math.min(bounds.minY(), minAllowedY) : bounds.minY();
        double clampedY = MathHelper.clamp(Math.max(proposedTarget.y, minAllowedY), minYBound, bounds.maxY());
        return new Vec3d(proposedTarget.x, clampedY, proposedTarget.z);
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
        Vec3d rawPos = context.toolHeadPos();
        double renderTick = ((double) context.worldTime()) + (double) clampTickDelta(tickDelta);
        QuarryMotionClientState.InterpolatedMotionSample motionSample =
                QuarryMotionClientState.sampleInterpolatedToolHeadPos(context.quarryPos(), renderTick, rawPos);
        Vec3d renderedPos = rawPos;
        if (!context.interpolationEnabled()) {
            interpolationStates.remove(new InterpolationKey(context.quarryPos().asLong(), InterpolatedComponent.TOOL_HEAD));
        } else {
            renderedPos = motionSample.toolHeadPos();
        }
        QuarryRenderTraceCapture.onRenderSample(
                context,
                tickDelta,
                rawPos,
                renderedPos,
                context.interpolationEnabled(),
                motionSample
        );
        return renderedPos;
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
        double nowTick = ((double) context.worldTime()) + (double) clampTickDelta(tickDelta);
        long serverStateTick = component == InterpolatedComponent.TOOL_HEAD
                ? context.toolHeadStateTick()
                : context.renderStateTick();
        InterpolationState state = interpolationStates.get(key);
        if (state == null) {
            state = new InterpolationState(rawPos, rawPos, nowTick, INTERPOLATION_MIN_DURATION_TICKS, nowTick, serverStateTick);
            interpolationStates.put(key, state);
            return rawPos;
        }

        // If server tick source ever regresses (dimension swap/reload), reset interpolation baseline.
        if (serverStateTick < state.lastServerStateTick) {
            state.from = rawPos;
            state.to = rawPos;
            state.startTick = nowTick;
            state.durationTicks = INTERPOLATION_MIN_DURATION_TICKS;
            state.lastUpdateTick = nowTick;
            state.lastServerStateTick = serverStateTick;
            return rawPos;
        }

        boolean targetChanged = !approximatelyEquals(state.to, rawPos);
        if (serverStateTick > state.lastServerStateTick && targetChanged) {
            Vec3d renderedNow = interpolateState(state, nowTick);
            long elapsedServerTicks = Math.max(1L, serverStateTick - state.lastServerStateTick);
            double duration = MathHelper.clamp(
                    (double) elapsedServerTicks,
                    INTERPOLATION_MIN_DURATION_TICKS,
                    INTERPOLATION_MAX_DURATION_TICKS
            );
            state.from = renderedNow;
            state.to = rawPos;
            state.startTick = nowTick;
            state.durationTicks = duration;
            state.lastUpdateTick = nowTick;
            state.lastServerStateTick = serverStateTick;
        } else if (targetChanged) {
            // Multiple updates landing in the same server tick can oscillate around a point.
            // Snap intra-tick changes to avoid visible stutter loops.
            state.from = rawPos;
            state.to = rawPos;
            state.startTick = nowTick;
            state.durationTicks = INTERPOLATION_MIN_DURATION_TICKS;
            state.lastUpdateTick = nowTick;
        }
        return interpolateState(state, nowTick);
    }

    private void resetInterpolatedToolHead(QuarryRenderContext context) {
        InterpolationKey key = new InterpolationKey(context.quarryPos().asLong(), InterpolatedComponent.TOOL_HEAD);
        Vec3d rawPos = context.toolHeadPos();
        double nowTick = context.worldTime();
        long serverStateTick = context.renderStateTick();
        InterpolationState state = interpolationStates.get(key);
        if (state == null) {
            interpolationStates.put(key, new InterpolationState(rawPos, rawPos, nowTick, INTERPOLATION_MIN_DURATION_TICKS, nowTick, serverStateTick));
            return;
        }
        state.from = rawPos;
        state.to = rawPos;
        state.startTick = nowTick;
        state.durationTicks = INTERPOLATION_MIN_DURATION_TICKS;
        state.lastUpdateTick = nowTick;
        state.lastServerStateTick = serverStateTick;
    }

    private Vec3d interpolateState(InterpolationState state, double nowTick) {
        if (state.durationTicks <= 1.0E-6) {
            return state.to;
        }
        double alpha = MathHelper.clamp((nowTick - state.startTick) / state.durationTicks, 0.0, 1.0);
        return state.from.lerp(state.to, alpha);
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

    private void updateCubePhaseFx(QuarryRenderContext context, RenderPhase phase) {
        long key = context.quarryPos().asLong();
        boolean cubeVisible = phase == RenderPhase.LASER_ACTIVE || phase == RenderPhase.LASER_IDLE;
        CubePhaseFxState fx = cubePhaseFxStates.get(key);
        if (fx == null) {
            fx = new CubePhaseFxState(false, context.toLocal(context.toolHeadOriginPos()));
            cubePhaseFxStates.put(key, fx);
        }

        if (cubeVisible) {
            if (!fx.visible) {
                emitCubeTeleportFx(context, getStaticGeometryCache(context).cubeBounds.center());
            }
            fx.visible = true;
            return;
        }

        if (fx.visible) {
            emitCubeTeleportFx(context, fx.lastLocalPos);
        }
        fx.visible = false;
    }

    private void emitCubeTeleportFx(QuarryRenderContext context, Vec3d cubeLocalPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) return;

        Vec3d worldPos = cubeLocalPos.add(
                context.quarryPos().getX(),
                context.quarryPos().getY(),
                context.quarryPos().getZ()
        );

        for (int i = 0; i < CUBE_TELEPORT_PARTICLES; i++) {
            double angle = (Math.PI * 2.0 * i) / CUBE_TELEPORT_PARTICLES;
            double radius = 0.18 + world.random.nextDouble() * 0.22;
            double px = worldPos.x + Math.cos(angle) * radius;
            double py = worldPos.y - 0.15 + world.random.nextDouble() * 0.5;
            double pz = worldPos.z + Math.sin(angle) * radius;
            double vx = (world.random.nextDouble() - 0.5) * 0.03;
            double vy = 0.01 + world.random.nextDouble() * 0.04;
            double vz = (world.random.nextDouble() - 0.5) * 0.03;
            world.addParticle(ParticleTypes.PORTAL, px, py, pz, vx, vy, vz);
        }
        world.playSound(
                worldPos.x, worldPos.y, worldPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.BLOCKS,
                0.35f,
                1.2f + (world.random.nextFloat() - 0.5f) * 0.2f,
                false
        );
    }

    private void pruneAuxRendererStates(QuarryBlockEntity activeBe) {
        long activeKey = activeBe.getPos().asLong();
        if (rediscoveryFrozenGantryHeads.size() >= 32) {
            rediscoveryFrozenGantryHeads.keySet().removeIf(key -> key != activeKey);
        }
        if (gantryRenderSmoothingStates.size() >= 32) {
            gantryRenderSmoothingStates.keySet().removeIf(key -> key != activeKey);
        }
        if (cubePhaseFxStates.size() >= 32) {
            cubePhaseFxStates.keySet().removeIf(key -> key != activeKey);
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
        private Vec3d from;
        private Vec3d to;
        private double startTick;
        private double durationTicks;
        private double lastUpdateTick;
        private long lastServerStateTick;

        private InterpolationState(Vec3d from, Vec3d to, double startTick, double durationTicks, double lastUpdateTick, long lastServerStateTick) {
            this.from = from;
            this.to = to;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.lastUpdateTick = lastUpdateTick;
            this.lastServerStateTick = lastServerStateTick;
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
        private Vec3d previousPosition;
        private Vec3d position;
        private Vec3d target;
        private long lastWaypointTick;
        private int waypointIndex;
        private boolean rediscoveryTravelActive;
        private boolean rediscoveryDraining;
        private long lastRediscoveryUpdateTick;
        private long lastCubeMotionUpdateTick;
        private double rediscoveryVolumeOriginY;
        private boolean lastVerticalTravelFlag;
        private double lastVolumeOriginY;
        @Nullable
        private RediscoveryCubeVolume rediscoveryVolume;

        private CubeMotionState(Vec3d position, Vec3d target, long lastWaypointTick, int waypointIndex) {
            this.previousPosition = position;
            this.position = position;
            this.target = target;
            this.lastWaypointTick = lastWaypointTick;
            this.waypointIndex = waypointIndex;
            this.rediscoveryTravelActive = false;
            this.rediscoveryDraining = false;
            this.lastRediscoveryUpdateTick = Long.MIN_VALUE;
            this.lastCubeMotionUpdateTick = Long.MIN_VALUE;
            this.rediscoveryVolumeOriginY = Double.NaN;
            this.lastVerticalTravelFlag = false;
            this.lastVolumeOriginY = Double.NaN;
            this.rediscoveryVolume = null;
        }
    }

    private static final class RediscoveryCubeVolume {
        private final Vec3d origin;
        private final double minX;
        private final double maxX;
        private final double minY;
        private final double maxY;
        private final double minZ;
        private final double maxZ;

        private RediscoveryCubeVolume(Vec3d origin,
                                      double minX,
                                      double maxX,
                                      double minY,
                                      double maxY,
                                      double minZ,
                                      double maxZ) {
            this.origin = origin;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }

    private static final class CubePhaseFxState {
        private boolean visible;
        private Vec3d lastLocalPos;

        private CubePhaseFxState(boolean visible, Vec3d lastLocalPos) {
            this.visible = visible;
            this.lastLocalPos = lastLocalPos;
        }
    }

    private static final class FrameThrowGhost {
        private final long targetPacked;
        private final Vec3d start;
        private Vec3d end;
        private final double durationTicks;
        private double ageTicks;
        private long lastRenderMs;
        private long arrivedAtMs = -1L;

        private FrameThrowGhost(long targetPacked, Vec3d start, Vec3d end, double durationTicks, double ageTicks, long lastRenderMs) {
            this.targetPacked = targetPacked;
            this.start = start;
            this.end = end;
            this.durationTicks = durationTicks;
            this.ageTicks = ageTicks;
            this.lastRenderMs = lastRenderMs;
        }
    }

    private static final class BeamHoldState {
        private Vec3d primaryTargetLocal;
        private double primaryExpireAtTick = Double.NEGATIVE_INFINITY;
        private Vec3d trailingTargetLocal;
        private double trailingExpireAtTick = Double.NEGATIVE_INFINITY;
    }

    private static final class GantryRenderSmoothingState {
        private Vec3d lastHeadWorld;
        private double lastRenderTick;

        private GantryRenderSmoothingState(Vec3d lastHeadWorld, double lastRenderTick) {
            this.lastHeadWorld = lastHeadWorld;
            this.lastRenderTick = lastRenderTick;
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
        LASER_ACTIVE,
        LASER_IDLE,
        GANTRY
    }

    private enum CubeBehaviorMode {
        STATIC,
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
