package com.errorsys.quarry_reforged.client.net;

import com.errorsys.quarry_reforged.client.debug.RediscoveryOverlayHud;
import com.errorsys.quarry_reforged.net.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;

public final class ClientDebugNetworking {
    private ClientDebugNetworking() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.REDISCOVERY_DEBUG_OVERLAY, (client, handler, buf, responseSender) -> {
            boolean enabled = buf.readBoolean();
            if (!enabled) {
                client.execute(RediscoveryOverlayHud::clear);
                return;
            }

            BlockPos pos = buf.readBlockPos();
            boolean active = buf.readBoolean();
            boolean areaLocked = buf.readBoolean();
            boolean drain = buf.readBoolean();
            boolean fullRescan = buf.readBoolean();
            boolean finalSweep = buf.readBoolean();
            int rediscoveryLayerY = buf.readInt();
            long nextScanInTicks = buf.readVarLong();
            String activeTargetType = buf.readString(64);
            boolean hasActiveTargetPos = buf.readBoolean();
            BlockPos activeTargetPos = hasActiveTargetPos ? buf.readBlockPos() : null;
            String phaseHint = buf.readString(32);
            String renderPhase = buf.readString(32);
            int gantryRaw = buf.readVarInt();
            int gantryValid = buf.readVarInt();
            boolean hasGantryHead = buf.readBoolean();
            BlockPos gantryHead = hasGantryHead ? buf.readBlockPos() : null;
            int laserRaw = buf.readVarInt();
            int laserValid = buf.readVarInt();
            boolean hasLaserHead = buf.readBoolean();
            BlockPos laserHead = hasLaserHead ? buf.readBlockPos() : null;

            RediscoveryOverlayHud.Snapshot snapshot = new RediscoveryOverlayHud.Snapshot(
                    pos,
                    active,
                    areaLocked,
                    drain,
                    fullRescan,
                    finalSweep,
                    rediscoveryLayerY,
                    nextScanInTicks,
                    activeTargetType,
                    activeTargetPos,
                    phaseHint,
                    renderPhase,
                    gantryRaw,
                    gantryValid,
                    gantryHead,
                    laserRaw,
                    laserValid,
                    laserHead
            );
            client.execute(() -> RediscoveryOverlayHud.update(snapshot));
        });
    }
}
