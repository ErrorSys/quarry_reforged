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

    public static void ensureTicketsForArea(ServerWorld world,
                                            BlockPos quarryPos,
                                            int minX,
                                            int maxX,
                                            int minZ,
                                            int maxZ,
                                            int ticketLevel) {
        ChunkPos qc = new ChunkPos(quarryPos);
        int minChunkX = Math.floorDiv(Math.min(minX, maxX), 16);
        int maxChunkX = Math.floorDiv(Math.max(minX, maxX), 16);
        int minChunkZ = Math.floorDiv(Math.min(minZ, maxZ), 16);
        int maxChunkZ = Math.floorDiv(Math.max(minZ, maxZ), 16);

        Set<ChunkPos> want = new HashSet<>();
        want.add(qc);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                want.add(new ChunkPos(cx, cz));
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

    public static Set<ChunkPos> snapshotActiveTickets(BlockPos quarryPos) {
        Set<ChunkPos> have = ACTIVE.get(key(quarryPos));
        if (have == null || have.isEmpty()) return Set.of();
        return new HashSet<>(have);
    }

    public static Set<ChunkPos> computeAreaTicketSet(BlockPos quarryPos, int minX, int maxX, int minZ, int maxZ) {
        ChunkPos qc = new ChunkPos(quarryPos);
        int minChunkX = Math.floorDiv(Math.min(minX, maxX), 16);
        int maxChunkX = Math.floorDiv(Math.max(minX, maxX), 16);
        int minChunkZ = Math.floorDiv(Math.min(minZ, maxZ), 16);
        int maxChunkZ = Math.floorDiv(Math.max(minZ, maxZ), 16);

        Set<ChunkPos> want = new HashSet<>();
        want.add(qc);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                want.add(new ChunkPos(cx, cz));
            }
        }
        return want;
    }
}
