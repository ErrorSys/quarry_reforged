package com.errorsys.quarry_reforged.command;

import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity.RenderChannelPhase;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.BlockPosArgumentType;

public final class ModCommands {
    private ModCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ModCommands::registerCommands);
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
        );
    }

    private static int setPreview(ServerCommandSource source, BlockPos pos, boolean enabled) {
        ServerWorld world = source.getWorld();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            source.sendError(Text.literal("No quarry block entity found at " + pos.toShortString() + "."));
            return 0;
        }

        boolean changed = quarry.setDebugVisualPreview(world, enabled);
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

        quarry.setDebugForcedRenderPhase(world, forcedPhase);
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

        quarry.setDebugInterpolationEnabled(world, enabled);
        source.sendFeedback(() -> Text.literal("Quarry debug interpolation " + (enabled ? "enabled" : "disabled") + " at " + pos.toShortString() + "."), false);
        return 1;
    }
}
