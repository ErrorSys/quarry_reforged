package com.errorsys.quarry_reforged.net;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ModNetworking {
    public static final Identifier TOGGLE_ACTIVE = id("toggle_active");
    public static final Identifier REMOVE_FRAME = id("remove_frame");

    private ModNetworking() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ACTIVE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> withQuarry(player, pos, (q) -> q.onToggleRequested(player)));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOVE_FRAME, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> withQuarry(player, pos, (q) -> q.onRemoveFrameRequested(player)));
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
}
