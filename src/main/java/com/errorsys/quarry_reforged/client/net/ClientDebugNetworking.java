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
            String machinePhase = buf.readString(32);
            String laserSubstate = buf.readString(32);
            String gantrySubstate = buf.readString(32);
            String returnPhase = buf.readString(32);
            String renderChannelPhase = buf.readString(32);
            boolean rediscoveryLaserVerticalTravelActive = buf.readBoolean();
            boolean rediscoveryCallerActive = buf.readBoolean();
            String rediscoveryCallerPhase = buf.readString(32);
            String rediscoveryCallerLaserSubstate = buf.readString(32);
            String rediscoveryCallerGantrySubstate = buf.readString(32);
            int rediscoveryRaw = buf.readVarInt();
            int rediscoveryValid = buf.readVarInt();
            boolean hasRediscoveryHead = buf.readBoolean();
            BlockPos rediscoveryHead = hasRediscoveryHead ? buf.readBlockPos() : null;

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
                    machinePhase,
                    laserSubstate,
                    gantrySubstate,
                    returnPhase,
                    renderChannelPhase,
                    rediscoveryLaserVerticalTravelActive,
                    rediscoveryCallerActive,
                    rediscoveryCallerPhase,
                    rediscoveryCallerLaserSubstate,
                    rediscoveryCallerGantrySubstate,
                    rediscoveryRaw,
                    rediscoveryValid,
                    rediscoveryHead
            );
            client.execute(() -> RediscoveryOverlayHud.update(snapshot));
        });
    }
}
