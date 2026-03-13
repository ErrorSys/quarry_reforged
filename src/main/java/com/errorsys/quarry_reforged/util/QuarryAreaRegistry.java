package com.errorsys.quarry_reforged.util;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

public final class QuarryAreaRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, QuarryAreaRegistry> INSTANCES = new WeakHashMap<>();
    private static final long ONE_WEEK_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long PRUNE_INTERVAL_MS = 60L * 60L * 1000L;

    public static QuarryAreaRegistry get(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, QuarryAreaRegistry::new);
        }
    }

    private final MinecraftServer server;
    private final Path filePath;
    private final Map<String, AreaEntry> areasById = new HashMap<>();

    private boolean loaded = false;
    private long nextPruneAtEpochMs = 0L;

    private QuarryAreaRegistry(MinecraftServer server) {
        this.server = server;
        this.filePath = server.getSavePath(WorldSavePath.ROOT)
                .resolve("data")
                .resolve("quarry_reforged")
                .resolve("quarry_areas.json");
    }

    public synchronized void maybePrune() {
        ensureLoaded();
        long now = System.currentTimeMillis();
        if (now < nextPruneAtEpochMs) return;
        nextPruneAtEpochMs = now + PRUNE_INTERVAL_MS;

        boolean changed = pruneExpired(now);
        if (changed) save();
    }

    public synchronized int pruneNow() {
        ensureLoaded();
        long now = System.currentTimeMillis();
        int before = areasById.size();
        boolean changed = pruneExpired(now);
        nextPruneAtEpochMs = now + PRUNE_INTERVAL_MS;
        if (changed) save();
        return before - areasById.size();
    }

    public synchronized void reloadFromDisk() {
        loaded = false;
        areasById.clear();
        ensureLoaded();
    }

    public synchronized int trackedAreaCount() {
        ensureLoaded();
        return areasById.size();
    }

    public synchronized void recordAreaLocked(ServerWorld world,
                                              AreaBounds bounds,
                                              String phase,
                                              @Nullable UUID ownerUuid,
                                              BlockPos quarryPos,
                                              Direction facing) {
        ensureLoaded();
        maybePruneInline();
        long now = System.currentTimeMillis();
        String dimension = dimensionId(world);
        String areaId = areaId(dimension, bounds);
        AreaEntry entry = areasById.get(areaId);
        if (entry == null) {
            entry = new AreaEntry();
            entry.areaId = areaId;
            entry.dimension = dimension;
            entry.minX = bounds.minX();
            entry.maxX = bounds.maxX();
            entry.minY = bounds.minY();
            entry.maxY = bounds.maxY();
            entry.minZ = bounds.minZ();
            entry.maxZ = bounds.maxZ();
            areasById.put(areaId, entry);
        }
        entry.state = phase;
        entry.finished = false;
        entry.lastEventEpochMs = now;
        entry.ownerUuid = ownerUuid == null ? null : ownerUuid.toString();
        entry.lastQuarryPos = quarryPos.toShortString();
        entry.lastFacing = facing.getName();
        entry.softLockQuarryPos = packedPos(quarryPos);
        save();
    }

    public synchronized void recordPhaseChange(ServerWorld world,
                                               AreaBounds bounds,
                                               String phase,
                                               @Nullable UUID ownerUuid,
                                               BlockPos quarryPos,
                                               Direction facing) {
        ensureLoaded();
        maybePruneInline();
        if ("REMOVE_FRAME".equals(phase)) return;
        String areaId = areaId(dimensionId(world), bounds);
        AreaEntry entry = areasById.get(areaId);
        if (entry == null) {
            recordAreaLocked(world, bounds, phase, ownerUuid, quarryPos, facing);
            return;
        }
        if (Objects.equals(entry.state, phase)) return;

        entry.state = phase;
        entry.finished = false;
        entry.lastEventEpochMs = System.currentTimeMillis();
        entry.ownerUuid = ownerUuid == null ? null : ownerUuid.toString();
        entry.lastQuarryPos = quarryPos.toShortString();
        entry.lastFacing = facing.getName();
        entry.softLockQuarryPos = packedPos(quarryPos);
        save();
    }

    public synchronized void markFinished(ServerWorld world,
                                          AreaBounds bounds,
                                          @Nullable UUID ownerUuid,
                                          BlockPos quarryPos,
                                          Direction facing) {
        ensureLoaded();
        maybePruneInline();
        String areaId = areaId(dimensionId(world), bounds);
        AreaEntry entry = areasById.get(areaId);
        if (entry == null) {
            entry = new AreaEntry();
            entry.areaId = areaId;
            entry.dimension = dimensionId(world);
            entry.minX = bounds.minX();
            entry.maxX = bounds.maxX();
            entry.minY = bounds.minY();
            entry.maxY = bounds.maxY();
            entry.minZ = bounds.minZ();
            entry.maxZ = bounds.maxZ();
            areasById.put(areaId, entry);
        }
        entry.state = "FINISH";
        entry.finished = true;
        entry.lastEventEpochMs = System.currentTimeMillis();
        entry.ownerUuid = ownerUuid == null ? null : ownerUuid.toString();
        entry.lastQuarryPos = quarryPos.toShortString();
        entry.lastFacing = facing.getName();
        entry.softLockQuarryPos = null;
        save();
    }

    public synchronized void removeArea(ServerWorld world, AreaBounds bounds) {
        ensureLoaded();
        maybePruneInline();
        String areaId = areaId(dimensionId(world), bounds);
        if (areasById.remove(areaId) != null) {
            save();
        }
    }

    public synchronized void releaseSoftLockByQuarryPos(ServerWorld world, BlockPos quarryPos) {
        ensureLoaded();
        maybePruneInline();
        String dimension = dimensionId(world);
        String quarryPosPacked = packedPos(quarryPos);
        boolean changed = false;
        for (AreaEntry entry : areasById.values()) {
            if (!dimension.equals(entry.dimension)) continue;
            if (!Objects.equals(entry.softLockQuarryPos, quarryPosPacked)) continue;
            entry.softLockQuarryPos = null;
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    @Nullable
    public synchronized AreaBounds findRelockCandidate(ServerWorld world, BlockPos quarryPos, Direction facing) {
        ensureLoaded();
        maybePruneInline();
        String dimension = dimensionId(world);
        AreaEntry best = null;
        int bestVolume = Integer.MAX_VALUE;
        for (AreaEntry entry : areasById.values()) {
            if (entry.finished || !dimension.equals(entry.dimension)) continue;
            if (!matchesRelockPlacement(entry, quarryPos, facing)) continue;
            if (isSoftLockedByAnotherLiveQuarry(world, entry, quarryPos)) continue;
            int volume = volume(entry);
            if (volume < bestVolume) {
                best = entry;
                bestVolume = volume;
            }
        }
        if (best == null) return null;
        if (claimSoftLock(world, best, quarryPos)) {
            save();
        }
        return boundsOf(best);
    }

    @Nullable
    public synchronized AreaBounds findAreaByFrameEdgeBlock(ServerWorld world, BlockPos blockPos) {
        ensureLoaded();
        maybePruneInline();
        String dimension = dimensionId(world);
        AreaEntry best = null;
        int bestVolume = Integer.MAX_VALUE;
        for (AreaEntry entry : areasById.values()) {
            if (entry.finished || !dimension.equals(entry.dimension)) continue;
            if (!isFrameEdgeBlock(entry, blockPos)) continue;
            int volume = volume(entry);
            if (volume < bestVolume) {
                best = entry;
                bestVolume = volume;
            }
        }
        return best == null ? null : boundsOf(best);
    }

    @Nullable
    public synchronized AreaBounds findAreaContainingBlock(ServerWorld world, BlockPos blockPos, boolean includeFinished) {
        ensureLoaded();
        maybePruneInline();
        String dimension = dimensionId(world);
        AreaEntry best = null;
        int bestVolume = Integer.MAX_VALUE;
        for (AreaEntry entry : areasById.values()) {
            if ((!includeFinished && entry.finished) || !dimension.equals(entry.dimension)) continue;
            if (!containsBlock(entry, blockPos)) continue;
            int volume = volume(entry);
            if (volume < bestVolume) {
                best = entry;
                bestVolume = volume;
            }
        }
        return best == null ? null : boundsOf(best);
    }

    public synchronized boolean intersectsActiveArea(ServerWorld world, AreaBounds bounds, boolean allowExactMatch) {
        ensureLoaded();
        maybePruneInline();
        String dimension = dimensionId(world);
        for (AreaEntry entry : areasById.values()) {
            if (entry.finished || !dimension.equals(entry.dimension)) continue;
            AreaBounds existing = boundsOf(entry);
            if (allowExactMatch && existing.equals(bounds)) continue;
            if (intersectsXZ(existing, bounds)) return true;
        }
        return false;
    }

    private static boolean intersectsXZ(AreaBounds a, AreaBounds b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX()
                && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    private static boolean matchesRelockPlacement(AreaEntry entry, BlockPos quarryPos, Direction facing) {
        if (!facing.getAxis().isHorizontal()) return false;
        BlockPos behind = quarryPos.offset(facing.getOpposite());
        // Relock requires the block behind the quarry to coincide with an actual frame-path block location.
        if (!isFramePathBlock(entry, behind)) return false;
        // Placement also needs a consistent outward orientation from the coincident frame edge.
        boolean matchesWestFace = behind.getX() == entry.minX && facing == Direction.WEST;
        boolean matchesEastFace = behind.getX() == entry.maxX && facing == Direction.EAST;
        boolean matchesNorthFace = behind.getZ() == entry.minZ && facing == Direction.NORTH;
        boolean matchesSouthFace = behind.getZ() == entry.maxZ && facing == Direction.SOUTH;
        return matchesWestFace || matchesEastFace || matchesNorthFace || matchesSouthFace;
    }

    private static boolean isFramePathBlock(AreaEntry entry, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (x < entry.minX || x > entry.maxX || y < entry.minY || y > entry.maxY || z < entry.minZ || z > entry.maxZ) {
            return false;
        }
        if (y == entry.minY || y == entry.maxY) {
            // Bottom/top rings only, not full caps.
            return x == entry.minX || x == entry.maxX || z == entry.minZ || z == entry.maxZ;
        }
        // Mid-height posts at corners only.
        boolean cornerX = x == entry.minX || x == entry.maxX;
        boolean cornerZ = z == entry.minZ || z == entry.maxZ;
        return cornerX && cornerZ;
    }

    private static boolean isFrameEdgeBlock(AreaEntry entry, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (x < entry.minX || x > entry.maxX || y < entry.minY || y > entry.maxY || z < entry.minZ || z > entry.maxZ) {
            return false;
        }
        return x == entry.minX || x == entry.maxX
                || y == entry.minY || y == entry.maxY
                || z == entry.minZ || z == entry.maxZ;
    }

    private static boolean containsBlock(AreaEntry entry, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= entry.minX && x <= entry.maxX
                && y >= entry.minY && y <= entry.maxY
                && z >= entry.minZ && z <= entry.maxZ;
    }

    private static boolean isSoftLockedByAnotherLiveQuarry(ServerWorld world, AreaEntry entry, BlockPos requesterPos) {
        if (entry.softLockQuarryPos == null || entry.softLockQuarryPos.isBlank()) return false;
        BlockPos holderPos = unpackPos(entry.softLockQuarryPos);
        if (holderPos == null) return false;
        if (holderPos.equals(requesterPos)) return false;
        return hasQuarryAt(world, holderPos);
    }

    private static boolean claimSoftLock(ServerWorld world, AreaEntry entry, BlockPos requesterPos) {
        String requesterPacked = packedPos(requesterPos);
        if (Objects.equals(entry.softLockQuarryPos, requesterPacked)) return false;
        if (entry.softLockQuarryPos == null || entry.softLockQuarryPos.isBlank()) {
            entry.softLockQuarryPos = requesterPacked;
            return true;
        }

        BlockPos holderPos = unpackPos(entry.softLockQuarryPos);
        if (holderPos == null || !hasQuarryAt(world, holderPos)) {
            entry.softLockQuarryPos = requesterPacked;
            return true;
        }
        return false;
    }

    private static boolean hasQuarryAt(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isOf(com.errorsys.quarry_reforged.content.ModBlocks.QUARRY);
    }

    private static String packedPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Nullable
    private static BlockPos unpackPos(String packed) {
        String[] parts = packed.split(",");
        if (parts.length != 3) return null;
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int volume(AreaEntry entry) {
        return (entry.maxX - entry.minX + 1)
                * (entry.maxY - entry.minY + 1)
                * (entry.maxZ - entry.minZ + 1);
    }

    private static AreaBounds boundsOf(AreaEntry entry) {
        return new AreaBounds(entry.minX, entry.maxX, entry.minY, entry.maxY, entry.minZ, entry.maxZ);
    }

    private static String dimensionId(ServerWorld world) {
        return world.getRegistryKey().getValue().toString();
    }

    private static String areaId(String dimension, AreaBounds bounds) {
        return String.format(
                Locale.ROOT,
                "%s|%d,%d,%d,%d,%d,%d",
                dimension,
                bounds.minX(), bounds.maxX(),
                bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ()
        );
    }

    private void maybePruneInline() {
        long now = System.currentTimeMillis();
        if (now < nextPruneAtEpochMs) return;
        nextPruneAtEpochMs = now + PRUNE_INTERVAL_MS;
        if (pruneExpired(now)) {
            save();
        }
    }

    private boolean pruneExpired(long now) {
        boolean changed = false;
        Iterator<AreaEntry> iterator = areasById.values().iterator();
        while (iterator.hasNext()) {
            AreaEntry entry = iterator.next();
            if (now - entry.lastEventEpochMs >= ONE_WEEK_MS) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        nextPruneAtEpochMs = System.currentTimeMillis() + PRUNE_INTERVAL_MS;
        if (!Files.exists(filePath)) return;

        try (Reader reader = Files.newBufferedReader(filePath)) {
            PersistedData data = GSON.fromJson(reader, PersistedData.class);
            if (data == null || data.areas == null) return;
            for (AreaEntry entry : data.areas) {
                if (entry == null || entry.areaId == null || entry.dimension == null) continue;
                areasById.put(entry.areaId, entry);
            }
        } catch (IOException | JsonSyntaxException ex) {
            QuarryReforged.LOGGER.warn("Failed to load quarry area registry from {}", filePath, ex);
        }
    }

    private void save() {
        PersistedData data = new PersistedData();
        data.version = 1;
        data.lastPruneEpochMs = System.currentTimeMillis();
        data.areas = new ArrayList<>(areasById.values());

        try {
            Files.createDirectories(filePath.getParent());
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            QuarryReforged.LOGGER.warn("Failed to save quarry area registry to {}", filePath, ex);
        }
    }

    public record AreaBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    private static final class PersistedData {
        int version = 1;
        long lastPruneEpochMs = 0L;
        List<AreaEntry> areas = List.of();
    }

    private static final class AreaEntry {
        String areaId;
        String dimension;
        int minX;
        int maxX;
        int minY;
        int maxY;
        int minZ;
        int maxZ;
        String state;
        boolean finished;
        long lastEventEpochMs;
        String ownerUuid;
        String lastQuarryPos;
        String lastFacing;
        String softLockQuarryPos;
    }
}
