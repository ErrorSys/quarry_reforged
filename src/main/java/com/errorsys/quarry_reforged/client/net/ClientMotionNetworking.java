package com.errorsys.quarry_reforged.client.net;

import com.errorsys.quarry_reforged.net.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ClientMotionNetworking {
    private ClientMotionNetworking() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.QUARRY_MOTION_UPDATE, (client, handler, buf, responseSender) -> {
            BlockPos quarryPos = buf.readBlockPos();
            long serverTick = buf.readVarLong();
            Vec3d toolHeadPos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> {
                long localWorldTick = client.world == null ? serverTick : client.world.getTime();
                QuarryMotionClientState.update(quarryPos, serverTick, toolHeadPos, localWorldTick);
            });
        });
    }
}
