package com.errorsys.quarry_reforged.command;

import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity.RediscoveryDebugSnapshot;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity.RenderChannelPhase;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.net.ModNetworking;
import com.errorsys.quarry_reforged.util.ChunkTickets;
import com.errorsys.quarry_reforged.util.QuarryAreaRegistry;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.BlockPosArgumentType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ModCommands {
    private static final Map<UUID, BlockPos> REDISCOVERY_OVERLAY_TARGETS = new HashMap<>();
    private static final Map<UUID, GantryTraceCapture> GANTRY_TRACE_CAPTURES = new HashMap<>();
    private static final int GANTRY_TRACE_DURATION_TICKS = 20 * 20;
    private static boolean callbacksRegistered = false;

    private ModCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ModCommands::registerCommands);
        if (!callbacksRegistered) {
            callbacksRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(ModCommands::tickRediscoveryOverlay);
            ServerTickEvents.END_SERVER_TICK.register(ModCommands::tickGantryTraceCapture);
        }
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                         net.minecraft.command.CommandRegistryAccess registryAccess,
                                         CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                literal("quarry")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("area")
                                .then(literal("remove")
                                        .executes(ctx -> removeAreaAtOperatorPosition(ctx.getSource(), false))
                                        .then(argument("force", BoolArgumentType.bool())
                                                .executes(ctx -> removeAreaAtOperatorPosition(ctx.getSource(), BoolArgumentType.getBool(ctx, "force"))))))
                        .then(literal("deleteInactives")
                                .executes(ctx -> forcePruneAreaRegistry(ctx.getSource())))
                        .then(literal("debug")
                                .then(literal("viz")
                                        .then(literal("preview")
                                                .then(literal("on")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPreview(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), true))))
                                                .then(literal("off")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPreview(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), false)))))
                                        .then(literal("phase")
                                                .then(literal("auto")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), null, "auto"))))
                                                .then(literal("render_none")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_NONE, "render_none"))))
                                                .then(literal("debug_preview")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.DEBUG_PREVIEW, "debug_preview"))))
                                                .then(literal("render_frame_work")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_FRAME_WORK, "render_frame_work"))))
                                                .then(literal("render_laser_active")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_LASER_ACTIVE, "render_laser_active"))))
                                                .then(literal("render_laser_idle")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_LASER_IDLE, "render_laser_idle"))))
                                                .then(literal("render_gantry_mine")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_GANTRY_MINE, "render_gantry_mine"))))
                                                .then(literal("render_gantry_travel")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_GANTRY_TRAVEL, "render_gantry_travel"))))
                                                .then(literal("render_gantry_freeze")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_GANTRY_FREEZE, "render_gantry_freeze"))))
                                                .then(literal("render_finish_home")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_FINISH_HOME, "render_finish_home"))))
                                                .then(literal("render_remove_frame")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.RENDER_REMOVE_FRAME, "render_remove_frame")))))
                                        .then(literal("freeze")
                                                .then(literal("on")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setFreeze(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), true))))
                                                .then(literal("off")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setFreeze(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), false)))))
                                        .then(literal("interp")
                                                .then(literal("on")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setInterpolation(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), true))))
                                                .then(literal("off")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setInterpolation(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), false)))))
                                        .then(literal("rediscovery_render")
                                                .then(literal("on")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setRediscoveryRender(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), true))))
                                                .then(literal("off")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> setRediscoveryRender(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), false)))))
                                )
                                .then(literal("diag")
                                        .then(literal("tickets")
                                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                                        .executes(ctx -> debugTickets(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos")))))
                                        .then(literal("perf")
                                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                                        .executes(ctx -> dumpPerf(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"))))
                                                .then(literal("reset")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> resetPerf(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"))))))
                                        .then(literal("rediscovery")
                                                .then(literal("on")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> enableRediscoveryOverlay(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos")))))
                                                .then(literal("off")
                                                        .executes(ctx -> disableRediscoveryOverlay(ctx.getSource()))))
                                        .then(literal("gantrytrace")
                                                .then(literal("on")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> startGantryTraceCapture(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos")))))
                                                .then(literal("off")
                                                        .executes(ctx -> stopGantryTraceCapture(ctx.getSource()))))
                                        .then(literal("rendertrace")
                                                .then(literal("on")
                                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                                .executes(ctx -> startRenderTraceCapture(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos")))))
                                                .then(literal("off")
                                                        .executes(ctx -> stopRenderTraceCapture(ctx.getSource()))))
                                )
                                .then(literal("io")
                                        .then(literal("autoexportlog")
                                                .then(literal("on")
                                                        .executes(ctx -> setAutoExportLog(ctx.getSource(), true)))
                                                .then(literal("off")
                                                        .executes(ctx -> setAutoExportLog(ctx.getSource(), false))))
                                )
                        )
        );
    }

    private static int setAutoExportLog(ServerCommandSource source, boolean enabled) {
        QuarryBlockEntity.setAutoExportDebugLogging(enabled);
        source.sendFeedback(() -> Text.literal("Quarry auto-export debug logging " + (enabled ? "enabled" : "disabled") + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setPreview(ServerCommandSource source, BlockPos pos, boolean enabled) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        boolean changed = quarry.setDebugVisualPreview(enabled);
        if (!changed) {
            source.sendError(Text.literal("Unable to " + (enabled ? "enable" : "disable") + " preview at this time. Quarry must be area-locked and idle."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Quarry debug preview " + (enabled ? "enabled" : "disabled") + " at " + pos.toShortString() + "."), false);
        return 1;
    }

    private static int setPhaseOverride(ServerCommandSource source, BlockPos pos, RenderChannelPhase forcedPhase, String label) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        quarry.setDebugForcedRenderPhase(forcedPhase);
        source.sendFeedback(() -> Text.literal("Quarry debug phase set to " + label + " at " + pos.toShortString() + "."), false);
        return 1;
    }

    private static int setFreeze(ServerCommandSource source, BlockPos pos, boolean enabled) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        quarry.setDebugFreezeAnimation(world, enabled);
        source.sendFeedback(() -> Text.literal("Quarry debug animation freeze " + (enabled ? "enabled" : "disabled") + " at " + pos.toShortString() + "."), false);
        return 1;
    }

    private static int setInterpolation(ServerCommandSource source, BlockPos pos, boolean enabled) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        quarry.setDebugInterpolationEnabled(enabled);
        source.sendFeedback(() -> Text.literal("Quarry debug interpolation " + (enabled ? "enabled" : "disabled") + " at " + pos.toShortString() + "."), false);
        return 1;
    }

    private static int setRediscoveryRender(ServerCommandSource source, BlockPos pos, boolean enabled) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        quarry.setDebugRediscoveryVolumeRenderEnabled(enabled);
        source.sendFeedback(() -> Text.literal("Quarry rediscovery volume/target debug render " + (enabled ? "enabled" : "disabled") + " at " + pos.toShortString() + "."), false);
        return 1;
    }

    private static int dumpPerf(ServerCommandSource source, BlockPos pos) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        QuarryBlockEntity.PerfSnapshot snapshot = quarry.getPerfSnapshot();
        source.sendFeedback(() -> Text.literal(
                "Quarry perf @" + pos.toShortString()
                        + " lastTick=" + formatNanosAsMs(snapshot.lastTickNanos()) + "ms"
                        + " windowAvg=" + formatNanosAsMs(snapshot.lastWindowAvgNanos()) + "ms"
                        + " windowMax=" + formatNanosAsMs(snapshot.lastWindowMaxNanos()) + "ms"
                        + " samples=" + snapshot.lastWindowSamples()
                        + " ticks=[" + snapshot.lastWindowStartTick() + ".." + snapshot.lastWindowEndTick() + "]"
        ), false);
        return 1;
    }

    private static int resetPerf(ServerCommandSource source, BlockPos pos) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        quarry.resetPerfStats();
        source.sendFeedback(() -> Text.literal("Quarry perf counters reset at " + pos.toShortString() + "."), false);
        return 1;
    }

    private static int debugTickets(ServerCommandSource source, BlockPos pos) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        if (!quarry.hasLockedAreaClient()) {
            source.sendError(Text.literal("Quarry at " + pos.toShortString() + " has no locked area."));
            return 0;
        }

        int minX = quarry.getInnerMinX() - 1;
        int maxX = quarry.getInnerMaxX() + 1;
        int minZ = quarry.getInnerMinZ() - 1;
        int maxZ = quarry.getInnerMaxZ() + 1;

        Set<ChunkPos> active = ChunkTickets.snapshotActiveTickets(pos);
        Set<ChunkPos> expected = ChunkTickets.computeAreaTicketSet(pos, minX, maxX, minZ, maxZ);

        source.sendFeedback(() -> Text.literal(
                "Quarry tickets @" + pos.toShortString()
                        + " active=" + active.size()
                        + " expected=" + expected.size()
                        + " boundsXZ=[" + minX + ".." + maxX + ", " + minZ + ".." + maxZ + "]"
        ), false);

        List<ChunkPos> activeSorted = new ArrayList<>(active);
        activeSorted.sort(Comparator.comparingInt((ChunkPos cp) -> cp.x).thenComparingInt(cp -> cp.z));
        source.sendFeedback(() -> Text.literal("Active chunks: " + formatChunkList(activeSorted)), false);

        List<ChunkPos> missing = new ArrayList<>();
        for (ChunkPos cp : expected) {
            if (!active.contains(cp)) missing.add(cp);
        }
        missing.sort(Comparator.comparingInt((ChunkPos cp) -> cp.x).thenComparingInt(cp -> cp.z));
        source.sendFeedback(() -> Text.literal("Missing expected chunks: " + formatChunkList(missing)), false);

        List<ChunkPos> extra = new ArrayList<>();
        for (ChunkPos cp : active) {
            if (!expected.contains(cp)) extra.add(cp);
        }
        extra.sort(Comparator.comparingInt((ChunkPos cp) -> cp.x).thenComparingInt(cp -> cp.z));
        source.sendFeedback(() -> Text.literal("Extra active chunks: " + formatChunkList(extra)), false);
        return 1;
    }

    private static int forcePruneAreaRegistry(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        QuarryAreaRegistry registry = QuarryAreaRegistry.get(world.getServer());
        registry.reloadFromDisk();
        int removed = registry.pruneNow();
        int remaining = registry.trackedAreaCount();
        source.sendFeedback(
                () -> Text.literal("Quarry area registry prune complete. Removed=" + removed + ", remaining=" + remaining + "."),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int removeAreaAtOperatorPosition(ServerCommandSource source, boolean force) {
        ServerWorld world = source.getWorld();
        QuarryAreaRegistry registry = QuarryAreaRegistry.get(world.getServer());
        BlockPos operatorPos = BlockPos.ofFloored(source.getPosition());
        QuarryAreaRegistry.AreaBounds target = registry.findAreaContainingBlock(world, operatorPos, true);
        if (target == null) {
            source.sendError(Text.literal("No tracked quarry area contains " + operatorPos.toShortString() + "."));
            return 0;
        }

        List<QuarryBlockEntity> lockingQuarries = findQuarriesLockingArea(world, target);
        if (!force && !lockingQuarries.isEmpty()) {
            source.sendError(Text.literal(
                    "Area is locked by " + lockingQuarries.size() + " quarry(s). Re-run with force=true to stop and remove it."
            ));
            return 0;
        }

        if (force) {
            for (QuarryBlockEntity quarry : lockingQuarries) {
                quarry.forceAdminStopAndUnlock(world);
            }
        }

        registry.removeArea(world, target);
        removeFrameBlocksInArea(world, target);
        for (QuarryBlockEntity quarry : findQuarriesNearArea(world, target)) {
            quarry.clearPlacementPreviewForAreaServer(target);
        }
        source.sendFeedback(
                () -> Text.literal(
                        "Removed Quarry Area [" + target.minX() + ".." + target.maxX()
                                + "," + target.minZ() + ".." + target.maxZ() + "]"
                ),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int removeFrameBlocksInArea(ServerWorld world, QuarryAreaRegistry.AreaBounds bounds) {
        int removed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    mutable.set(x, y, z);
                    if (!world.getBlockState(mutable).isOf(ModBlocks.FRAME)) continue;
                    if (world.breakBlock(mutable, false)) {
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private static List<QuarryBlockEntity> findQuarriesLockingArea(ServerWorld world, QuarryAreaRegistry.AreaBounds bounds) {
        List<QuarryBlockEntity> found = new ArrayList<>();
        for (QuarryBlockEntity quarry : findQuarriesNearArea(world, bounds)) {
            QuarryAreaRegistry.AreaBounds locked = quarry.getLockedAreaBoundsServer();
            if (locked == null || !locked.equals(bounds)) continue;
            found.add(quarry);
        }
        return found;
    }

    private static List<QuarryBlockEntity> findQuarriesNearArea(ServerWorld world, QuarryAreaRegistry.AreaBounds bounds) {
        List<QuarryBlockEntity> found = new ArrayList<>();
        int minChunkX = Math.floorDiv(bounds.minX() - 1, 16);
        int maxChunkX = Math.floorDiv(bounds.maxX() + 1, 16);
        int minChunkZ = Math.floorDiv(bounds.minZ() - 1, 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZ() + 1, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof QuarryBlockEntity quarry)) continue;
                    found.add(quarry);
                }
            }
        }
        return found;
    }

    private static String formatBounds(QuarryAreaRegistry.AreaBounds bounds) {
        return bounds.minX() + ".." + bounds.maxX()
                + ", " + bounds.minY() + ".." + bounds.maxY()
                + ", " + bounds.minZ() + ".." + bounds.maxZ();
    }

    private static int enableRediscoveryOverlay(ServerCommandSource source, BlockPos pos) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        REDISCOVERY_OVERLAY_TARGETS.put(player.getUuid(), pos.toImmutable());
        sendRediscoveryOverlayPacket(player, quarry, world, pos);
        source.sendFeedback(() -> Text.literal("Rediscovery overlay enabled for quarry at " + pos.toShortString() + ". Use /quarry debug diag rediscovery off to disable."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int disableRediscoveryOverlay(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        REDISCOVERY_OVERLAY_TARGETS.remove(player.getUuid());
        sendRediscoveryOverlayDisablePacket(player);
        source.sendFeedback(() -> Text.literal("Rediscovery overlay disabled."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int startGantryTraceCapture(ServerCommandSource source, BlockPos pos) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        GantryTraceCapture capture = new GantryTraceCapture(
                pos.toImmutable(),
                world.getRegistryKey(),
                world.getTime() + GANTRY_TRACE_DURATION_TICKS,
                new StringBuilder("serverTick,phase,laser,gantry,returnPhase,renderPhase,renderStateTick,active,drainRediscovery,verticalTravel,toolX,toolY,toolZ,deltaFromPrev,distanceToTarget,target,waypointCurrent,waypointNext,bps\n"),
                null
        );
        GANTRY_TRACE_CAPTURES.put(player.getUuid(), capture);
        source.sendFeedback(() -> Text.literal("Gantry trace capture started at " + pos.toShortString() + " for 20 seconds."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stopGantryTraceCapture(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        GantryTraceCapture capture = GANTRY_TRACE_CAPTURES.remove(player.getUuid());
        if (capture == null) {
            source.sendError(Text.literal("No active gantry trace capture."));
            return 0;
        }
        finishGantryTraceCapture(player, capture, "manual stop");
        source.sendFeedback(() -> Text.literal("Gantry trace capture stopped."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int startRenderTraceCapture(ServerCommandSource source, BlockPos pos) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, ModNetworking.QUARRY_RENDER_TRACE_CONTROL)) {
            source.sendError(Text.literal("Client cannot receive render trace control packets."));
            return 0;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeBlockPos(pos);
        buf.writeVarInt(GANTRY_TRACE_DURATION_TICKS);
        buf.writeDouble(1.25D);
        ServerPlayNetworking.send(player, ModNetworking.QUARRY_RENDER_TRACE_CONTROL, buf);
        source.sendFeedback(() -> Text.literal("Client render trace started at " + pos.toShortString() + " for 20 seconds."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stopRenderTraceCapture(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, ModNetworking.QUARRY_RENDER_TRACE_CONTROL)) {
            source.sendError(Text.literal("Client cannot receive render trace control packets."));
            return 0;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        ServerPlayNetworking.send(player, ModNetworking.QUARRY_RENDER_TRACE_CONTROL, buf);
        source.sendFeedback(() -> Text.literal("Client render trace stop requested."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void tickRediscoveryOverlay(net.minecraft.server.MinecraftServer server) {
        if (REDISCOVERY_OVERLAY_TARGETS.isEmpty()) return;

        Iterator<Map.Entry<UUID, BlockPos>> it = REDISCOVERY_OVERLAY_TARGETS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !ServerPlayNetworking.canSend(player, ModNetworking.REDISCOVERY_DEBUG_OVERLAY)) {
                it.remove();
                continue;
            }

            BlockPos pos = entry.getValue();
            if (!(player.getWorld().getBlockEntity(pos) instanceof QuarryBlockEntity quarry)) {
                sendRediscoveryOverlayDisablePacket(player);
                it.remove();
                continue;
            }

            sendRediscoveryOverlayPacket(player, quarry, player.getServerWorld(), pos);
        }
    }

    private static void tickGantryTraceCapture(net.minecraft.server.MinecraftServer server) {
        if (GANTRY_TRACE_CAPTURES.isEmpty()) return;

        Iterator<Map.Entry<UUID, GantryTraceCapture>> it = GANTRY_TRACE_CAPTURES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GantryTraceCapture> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            GantryTraceCapture capture = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }

            ServerWorld world = server.getWorld(capture.worldKey);
            if (world == null) {
                finishGantryTraceCapture(player, capture, "world unavailable");
                it.remove();
                continue;
            }

            BlockEntity blockEntity = world.getBlockEntity(capture.quarryPos);
            if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
                finishGantryTraceCapture(player, capture, "quarry missing");
                it.remove();
                continue;
            }

            RediscoveryDebugSnapshot snapshot = quarry.getRediscoveryDebugSnapshot(world);
            Vec3d toolHead = quarry.getClientToolHeadPos();
            BlockPos target = quarry.getRenderChannelActiveTargetClient();
            BlockPos waypointCurrent = quarry.getRenderChannelWaypointCurrentClient();
            BlockPos waypointNext = quarry.getRenderChannelWaypointNextClient();

            double delta = 0.0;
            if (capture.lastToolHeadPos != null) {
                delta = toolHead.distanceTo(capture.lastToolHeadPos);
            }
            double distanceToTarget = -1.0;
            if (target != null) {
                distanceToTarget = toolHead.distanceTo(Vec3d.ofCenter(target));
            }
            capture.lastToolHeadPos = toolHead;

            capture.rows.append(world.getTime()).append(',')
                    .append(csv(snapshot.machinePhase())).append(',')
                    .append(csv(snapshot.laserSubstate())).append(',')
                    .append(csv(snapshot.gantrySubstate())).append(',')
                    .append(csv(snapshot.returnPhase())).append(',')
                    .append(csv(snapshot.renderChannelPhase())).append(',')
                    .append(quarry.getRenderChannelStateTickClient()).append(',')
                    .append(snapshot.active()).append(',')
                    .append(snapshot.drainRediscoveryQueue()).append(',')
                    .append(snapshot.rediscoveryLaserVerticalTravelActive()).append(',')
                    .append(format(toolHead.x)).append(',')
                    .append(format(toolHead.y)).append(',')
                    .append(format(toolHead.z)).append(',')
                    .append(format(delta)).append(',')
                    .append(distanceToTarget < 0.0 ? "" : format(distanceToTarget)).append(',')
                    .append(csv(formatPos(target))).append(',')
                    .append(csv(formatPos(waypointCurrent))).append(',')
                    .append(csv(formatPos(waypointNext))).append(',')
                    .append(format(quarry.getBlocksPerSecondClient()))
                    .append('\n');

            if (world.getTime() >= capture.endTick) {
                finishGantryTraceCapture(player, capture, "duration complete");
                it.remove();
            }
        }
    }

    private static void finishGantryTraceCapture(ServerPlayerEntity player, GantryTraceCapture capture, String reason) {
        try {
            Path logsDir = Paths.get("logs");
            Files.createDirectories(logsDir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "quarry_gantry_trace_" + ts + "_" + capture.quarryPos.toShortString().replace(", ", "_").replace(",", "_") + ".csv";
            Path out = logsDir.resolve(fileName);
            Files.writeString(out, capture.rows.toString());
            player.sendMessage(Text.literal("Gantry trace saved (" + reason + "): " + out.toAbsolutePath()), false);
        } catch (Exception e) {
            player.sendMessage(Text.literal("Failed to save gantry trace: " + e.getMessage()), false);
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String formatNanosAsMs(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0L, nanos) / 1_000_000.0);
    }

    private static String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static void sendRediscoveryOverlayPacket(ServerPlayerEntity player, QuarryBlockEntity quarry, ServerWorld world, BlockPos pos) {
        RediscoveryDebugSnapshot snapshot = quarry.getRediscoveryDebugSnapshot(world);
        long ticksUntilNextScan = Math.max(0L, snapshot.nextRediscoveryScanTick() - world.getTime());

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeBlockPos(pos);
        buf.writeBoolean(snapshot.active());
        buf.writeBoolean(snapshot.areaLocked());
        buf.writeBoolean(snapshot.drainRediscoveryQueue());
        buf.writeBoolean(snapshot.rediscoveryFullRescanPending());
        buf.writeBoolean(snapshot.finalRediscoverySweepDone());
        buf.writeInt(snapshot.rediscoveryLayerY());
        buf.writeVarLong(ticksUntilNextScan);
        buf.writeString(orNone(snapshot.activeTargetType()), 64);
        writeOptionalPos(buf, snapshot.activeTargetPos());
        buf.writeString(snapshot.machinePhase(), 32);
        buf.writeString(snapshot.laserSubstate(), 32);
        buf.writeString(snapshot.gantrySubstate(), 32);
        buf.writeString(snapshot.returnPhase(), 32);
        buf.writeString(snapshot.renderChannelPhase(), 32);
        buf.writeBoolean(snapshot.rediscoveryLaserVerticalTravelActive());
        buf.writeBoolean(snapshot.rediscoveryCallerActive());
        buf.writeString(snapshot.rediscoveryCallerPhase(), 32);
        buf.writeString(snapshot.rediscoveryCallerLaserSubstate(), 32);
        buf.writeString(snapshot.rediscoveryCallerGantrySubstate(), 32);
        buf.writeVarInt(snapshot.rediscoveryQueueRawSize());
        buf.writeVarInt(snapshot.rediscoveryQueueValidSize());
        writeOptionalPos(buf, snapshot.rediscoveryQueueHead());

        ServerPlayNetworking.send(player, ModNetworking.REDISCOVERY_DEBUG_OVERLAY, buf);
    }

    private static void sendRediscoveryOverlayDisablePacket(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ModNetworking.REDISCOVERY_DEBUG_OVERLAY)) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        ServerPlayNetworking.send(player, ModNetworking.REDISCOVERY_DEBUG_OVERLAY, buf);
    }

    private static void writeOptionalPos(PacketByteBuf buf, BlockPos pos) {
        boolean hasPos = pos != null;
        buf.writeBoolean(hasPos);
        if (hasPos) {
            buf.writeBlockPos(pos);
        }
    }

    private static String formatChunkList(List<ChunkPos> chunks) {
        if (chunks.isEmpty()) return "(none)";
        int limit = 64;
        StringBuilder sb = new StringBuilder();
        int count = Math.min(limit, chunks.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(' ');
            ChunkPos cp = chunks.get(i);
            sb.append('[').append(cp.x).append(',').append(cp.z).append(']');
        }
        if (chunks.size() > limit) {
            sb.append(" ... +").append(chunks.size() - limit).append(" more");
        }
        return sb.toString();
    }

    private static String formatPos(BlockPos pos) {
        return pos == null ? "(none)" : pos.toShortString();
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private static final class GantryTraceCapture {
        private final BlockPos quarryPos;
        private final RegistryKey<World> worldKey;
        private final long endTick;
        private final StringBuilder rows;
        private Vec3d lastToolHeadPos;

        private GantryTraceCapture(BlockPos quarryPos,
                                   RegistryKey<World> worldKey,
                                   long endTick,
                                   StringBuilder rows,
                                   Vec3d lastToolHeadPos) {
            this.quarryPos = quarryPos;
            this.worldKey = worldKey;
            this.endTick = endTick;
            this.rows = rows;
            this.lastToolHeadPos = lastToolHeadPos;
        }
    }
}
