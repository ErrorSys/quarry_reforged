package com.errorsys.quarry_reforged.command;

import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity.RediscoveryDebugSnapshot;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity.RenderChannelPhase;
import com.errorsys.quarry_reforged.net.ModNetworking;
import com.errorsys.quarry_reforged.util.ChunkTickets;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

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

public final class ModCommands {
    private static final Map<UUID, BlockPos> REDISCOVERY_OVERLAY_TARGETS = new HashMap<>();
    private static boolean callbacksRegistered = false;

    private ModCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ModCommands::registerCommands);
        if (!callbacksRegistered) {
            callbacksRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(ModCommands::tickRediscoveryOverlay);
        }
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                         net.minecraft.command.CommandRegistryAccess registryAccess,
                                         CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                literal("quarrydebug")
                        .requires(source -> source.hasPermissionLevel(2))
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
                                .then(literal("none")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.NONE, "none"))))
                                .then(literal("debug_preview")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.DEBUG_PREVIEW, "debug_preview"))))
                                .then(literal("frame_work")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.FRAME_WORK, "frame_work"))))
                                .then(literal("top_laser")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.TOP_LASER, "top_laser"))))
                                .then(literal("suppressed_return")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.SUPPRESSED_RETURN, "suppressed_return"))))
                                .then(literal("gantry")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setPhaseOverride(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), RenderChannelPhase.GANTRY, "gantry")))))
                        .then(literal("freeze")
                                .then(literal("on")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setFreeze(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), true))))
                                .then(literal("off")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setFreeze(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), false)))))
                        .then(literal("interpolation")
                                .then(literal("on")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setInterpolation(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), true))))
                                .then(literal("off")
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> setInterpolation(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"), false)))))
                        .then(literal("tickets")
                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> debugTickets(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos")))))
                        .then(literal("rediscovery")
                                .then(literal("off")
                                        .executes(ctx -> disableRediscoveryOverlay(ctx.getSource())))
                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> enableRediscoveryOverlay(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos")))))
        );
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
        source.sendFeedback(() -> Text.literal("Rediscovery overlay enabled for quarry at " + pos.toShortString() + ". Use /quarrydebug rediscovery off to disable."), false);
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

    private static void sendRediscoveryOverlayPacket(ServerPlayerEntity player, QuarryBlockEntity quarry, ServerWorld world, BlockPos pos) {
        RediscoveryDebugSnapshot snapshot = quarry.getRediscoveryDebugSnapshot(world);
        long ticksUntilNextScan = Math.max(0L, snapshot.nextRediscoveryScanTick() - world.getTime());
        String phaseHint = quarry.shouldUseTopLaserClient() ? "TOP_LASER" : "GANTRY";
        String renderPhase = quarry.getRenderChannelPhaseClient().name();

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
        buf.writeString(phaseHint, 32);
        buf.writeString(renderPhase, 32);
        buf.writeVarInt(snapshot.gantryQueueRawSize());
        buf.writeVarInt(snapshot.gantryQueueValidSize());
        writeOptionalPos(buf, snapshot.gantryQueueHead());
        buf.writeVarInt(snapshot.laserQueueRawSize());
        buf.writeVarInt(snapshot.laserQueueValidSize());
        writeOptionalPos(buf, snapshot.laserQueueHead());

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
}
