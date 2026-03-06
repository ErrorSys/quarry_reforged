package com.errorsys.quarry_reforged.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

@FunctionalInterface
public interface QuarryMineCallback {
    ActionResult canMine(ServerWorld world, BlockPos quarryPos, UUID owner, BlockPos target, BlockState state);

    Event<QuarryMineCallback> EVENT = EventFactory.createArrayBacked(QuarryMineCallback.class,
            (listeners) -> (world, quarryPos, owner, target, state) -> {
                for (QuarryMineCallback cb : listeners) {
                    ActionResult res = cb.canMine(world, quarryPos, owner, target, state);
                    if (res == ActionResult.FAIL) return ActionResult.FAIL;
                }
                return ActionResult.PASS;
            });
}
