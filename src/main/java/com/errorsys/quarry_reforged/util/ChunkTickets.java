package com.errorsys.quarry_reforged.util;

import com.errorsys.quarry_reforged.QuarryReforged;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkTickets {
    private ChunkTickets() {}

    private static final ChunkTicketType<BlockPos> TICKET = ChunkTicketType.create(
            QuarryReforged.MOD_ID + ":quarry", Comparator.comparingLong(BlockPos::asLong)
    );

    private static final Map<Long, Set<ChunkPos>> ACTIVE = new ConcurrentHashMap<>();

    private static long key(BlockPos quarryPos) { return quarryPos.asLong(); }

    public static void ensureTickets(ServerWorld world, BlockPos quarryPos, Object quarryInstance, BlockPos target, int radiusChunks, int ticketLevel) {
        if (target == null) return;

        ChunkPos qc = new ChunkPos(quarryPos);
        ChunkPos tc = new ChunkPos(target);

        Set<ChunkPos> want = new HashSet<>();
        want.add(qc);
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                want.add(new ChunkPos(tc.x + dx, tc.z + dz));
            }
        }

        long k = key(quarryPos);
        Set<ChunkPos> have = ACTIVE.computeIfAbsent(k, kk -> new HashSet<>());

        for (ChunkPos cp : want) {
            if (have.add(cp)) {
                world.getChunkManager().addTicket(TICKET, cp, ticketLevel, quarryPos);
            }
        }

        Iterator<ChunkPos> it = have.iterator();
        while (it.hasNext()) {
            ChunkPos cp = it.next();
            if (!want.contains(cp)) {
                world.getChunkManager().removeTicket(TICKET, cp, ticketLevel, quarryPos);
                it.remove();
            }
        }
    }

    public static void clearTickets(ServerWorld world, BlockPos quarryPos, Object quarryInstance) {
        long k = key(quarryPos);
        Set<ChunkPos> have = ACTIVE.remove(k);
        if (have == null) return;

        for (ChunkPos cp : have) {
            world.getChunkManager().removeTicket(TICKET, cp, 2, quarryPos);
        }
    }
}
