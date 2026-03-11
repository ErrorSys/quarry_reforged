package com.errorsys.quarry_reforged.net;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.machine.config.ItemIoGroup;
import com.errorsys.quarry_reforged.machine.config.MachineRelativeSide;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ModNetworking {
    public static final Identifier TOGGLE_ACTIVE = id("toggle_active");
    public static final Identifier REMOVE_FRAME = id("remove_frame");
    public static final Identifier CYCLE_REDSTONE_MODE = id("cycle_redstone_mode");
    public static final Identifier TOGGLE_AUTO_EXPORT = id("toggle_auto_export");
    public static final Identifier CYCLE_SIDE_MODE = id("cycle_side_mode");
    public static final Identifier REDISCOVERY_DEBUG_OVERLAY = id("rediscovery_debug_overlay");
    public static final Identifier QUARRY_MOTION_UPDATE = id("quarry_motion_update");
    public static final Identifier QUARRY_RENDER_TRACE_CONTROL = id("quarry_render_trace_control");

    private ModNetworking() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ACTIVE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> withQuarry(player, pos, QuarryBlockEntity::onToggleRequested));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOVE_FRAME, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> withQuarry(player, pos, QuarryBlockEntity::onRemoveFrameRequested));
        });

        ServerPlayNetworking.registerGlobalReceiver(CYCLE_REDSTONE_MODE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> withQuarry(player, pos, QuarryBlockEntity::onCycleRedstoneRequested));
        });

        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_AUTO_EXPORT, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> withQuarry(player, pos, QuarryBlockEntity::onToggleAutoExportRequested));
        });

        ServerPlayNetworking.registerGlobalReceiver(CYCLE_SIDE_MODE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int groupId = buf.readVarInt();
            int sideId = buf.readVarInt();
            server.execute(() -> withQuarry(player, pos, (q) -> {
                ItemIoGroup[] groups = ItemIoGroup.values();
                MachineRelativeSide[] sides = MachineRelativeSide.values();
                if (groupId < 0 || groupId >= groups.length) return;
                if (sideId < 0 || sideId >= sides.length) return;
                q.onCycleSideModeRequested(groups[groupId], sides[sideId]);
            }));
        });

        QuarryReforged.LOGGER.info("Networking receivers registered.");
    }

    private static Identifier id(String path) {
        return new Identifier(QuarryReforged.MOD_ID, path);
    }

    private static void withQuarry(ServerPlayerEntity player, BlockPos pos, java.util.function.Consumer<QuarryBlockEntity> fn) {
        if (player.getWorld().getBlockEntity(pos) instanceof QuarryBlockEntity quarry) {
            if (!quarry.canPlayerAccess(player)) return;
            fn.accept(quarry);
        }
    }

    public static void sendQuarryMotion(ServerWorld world, BlockPos quarryPos, long serverTick, Vec3d toolHeadPos) {
        // Match renderer visibility radius with slight margin.
        double maxDistanceSq = 288.0 * 288.0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, QUARRY_MOTION_UPDATE)) continue;
            if (player.squaredDistanceTo(Vec3d.ofCenter(quarryPos)) > maxDistanceSq) continue;
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeBlockPos(quarryPos);
            buf.writeVarLong(serverTick);
            buf.writeDouble(toolHeadPos.x);
            buf.writeDouble(toolHeadPos.y);
            buf.writeDouble(toolHeadPos.z);
            ServerPlayNetworking.send(player, QUARRY_MOTION_UPDATE, buf);
        }
    }
}
