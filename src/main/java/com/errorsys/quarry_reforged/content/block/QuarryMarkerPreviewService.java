package com.errorsys.quarry_reforged.content.block;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.errorsys.quarry_reforged.config.ModConfig;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.blockentity.QuarryMarkerBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuarryMarkerPreviewService {
    private static final Map<RegistryKey<World>, WorldPreviewState> PREVIEW_STATES = new HashMap<>();

    private QuarryMarkerPreviewService() {}

    public static boolean tryActivatePreview(ServerWorld world, BlockPos clicked) {
        if (!world.getBlockState(clicked).isOf(ModBlocks.MARKER)) return false;

        List<BlockPos> markers = scanMarkers(world, clicked);
        Candidate candidate = findBestCandidateForClicked(markers, clicked);
        if (candidate == null) {
            QuarryReforged.LOGGER.info("Marker preview failed at {}: no valid marker layout found.", clicked);
            clearPreviewAt(world, clicked);
            setInvalidPreviewAt(world, clicked);
            return false;
        }

        if (intersectsAnotherPreview(world, candidate, markers)) {
            QuarryReforged.LOGGER.info("Marker preview failed at {}: candidate intersects another active preview.", clicked);
            clearPreviewAt(world, clicked);
            setInvalidPreviewAt(world, clicked);
            return false;
        }

        clearPreviewForMembers(world, candidate.members);
        applyPreview(world, candidate);
        QuarryReforged.LOGGER.info("Marker preview active. origin={}, bounds=({}, {}, {}) -> ({}, {}, {}), members={}",
                candidate.origin, candidate.minX, candidate.minY, candidate.minZ, candidate.maxX, candidate.maxY, candidate.maxZ, candidate.members);
        return true;
    }

    public static void onMarkerRemoved(ServerWorld world, BlockPos markerPos) {
        clearPreviewAt(world, markerPos);
    }

    public static void clearPreviewAt(ServerWorld world, BlockPos markerPos) {
        WorldPreviewState state = stateFor(world);
        Long originPacked = state.markerToOrigin.get(markerPos.asLong());
        if (originPacked == null) {
            if (world.getBlockEntity(markerPos) instanceof QuarryMarkerBlockEntity markerBe) {
                if (markerBe.hasPreview()) {
                    List<BlockPos> linked = markerBe.getLinkedMarkers();
                    if (!linked.isEmpty()) {
                        for (BlockPos linkedPos : linked) {
                            state.markerToOrigin.remove(linkedPos.asLong());
                            if (world.getBlockEntity(linkedPos) instanceof QuarryMarkerBlockEntity linkedBe) {
                                linkedBe.clearPreview();
                            }
                        }
                        BlockPos origin = markerBe.getOriginPos();
                        if (origin != null) state.byOrigin.remove(origin.asLong());
                        return;
                    }
                }
                markerBe.clearPreview();
            }
            return;
        }

        Preview preview = state.byOrigin.remove(originPacked);
        if (preview == null) return;
        for (long memberPacked : preview.memberPacked) {
            state.markerToOrigin.remove(memberPacked);
            BlockPos memberPos = BlockPos.fromLong(memberPacked);
            if (world.getBlockEntity(memberPos) instanceof QuarryMarkerBlockEntity markerBe) {
                markerBe.clearPreview();
            }
        }
    }

    private static void clearPreviewForMembers(ServerWorld world, List<BlockPos> members) {
        for (BlockPos member : members) clearPreviewAt(world, member);
    }

    private static void setInvalidPreviewAt(ServerWorld world, BlockPos markerPos) {
        if (!(world.getBlockEntity(markerPos) instanceof QuarryMarkerBlockEntity markerBe)) return;
        markerBe.setInvalidCardinalPreview(ModConfig.DATA.maxQuarrySize + 2);
    }

    private static void applyPreview(ServerWorld world, Candidate candidate) {
        WorldPreviewState state = stateFor(world);
        Preview preview = new Preview(
                candidate.origin.asLong(),
                candidate.minX, candidate.maxX,
                candidate.minY, candidate.maxY,
                candidate.minZ, candidate.maxZ,
                candidate.members.stream().mapToLong(BlockPos::asLong).toArray()
        );
        state.byOrigin.put(preview.originPacked, preview);
        for (BlockPos member : candidate.members) {
            state.markerToOrigin.put(member.asLong(), preview.originPacked);
            if (world.getBlockEntity(member) instanceof QuarryMarkerBlockEntity markerBe) {
                markerBe.setPreview(
                        candidate.origin,
                        candidate.minX, candidate.maxX,
                        candidate.minY, candidate.maxY,
                        candidate.minZ, candidate.maxZ,
                        candidate.members
                );
            }
        }
    }

    private static boolean intersectsAnotherPreview(ServerWorld world, Candidate candidate, List<BlockPos> scannedMarkers) {
        WorldPreviewState state = stateFor(world);
        Box candidateBox = new Box(
                candidate.minX, candidate.minY, candidate.minZ,
                candidate.maxX + 1.0, candidate.maxY + 1.0, candidate.maxZ + 1.0
        );

        Set<Long> candidateMemberSet = new HashSet<>();
        for (BlockPos member : candidate.members) candidateMemberSet.add(member.asLong());

        for (Preview existing : state.byOrigin.values()) {
            boolean samePreview = candidateMemberSet.contains(existing.originPacked);
            if (!samePreview) {
                for (long member : existing.memberPacked) {
                    if (candidateMemberSet.contains(member)) {
                        samePreview = true;
                        break;
                    }
                }
            }
            if (samePreview) continue;

            Box existingBox = new Box(
                    existing.minX, existing.minY, existing.minZ,
                    existing.maxX + 1.0, existing.maxY + 1.0, existing.maxZ + 1.0
            );
            if (candidateBox.intersects(existingBox)) return true;
        }

        for (BlockPos markerPos : scannedMarkers) {
            if (!(world.getBlockEntity(markerPos) instanceof QuarryMarkerBlockEntity markerBe)) continue;
            if (!markerBe.hasPreview() || !markerBe.isOriginMarker()) continue;

            Set<Long> existingMembers = new HashSet<>();
            for (BlockPos linked : markerBe.getLinkedMarkers()) existingMembers.add(linked.asLong());
            boolean samePreview = false;
            for (long candidateMember : candidateMemberSet) {
                if (existingMembers.contains(candidateMember)) {
                    samePreview = true;
                    break;
                }
            }
            if (samePreview) continue;

            Box existingBox = new Box(
                    markerBe.getMinX(), markerBe.getMinY(), markerBe.getMinZ(),
                    markerBe.getMaxX() + 1.0, markerBe.getMaxY() + 1.0, markerBe.getMaxZ() + 1.0
            );
            if (candidateBox.intersects(existingBox)) return true;
        }
        return false;
    }

    private static Candidate findBestCandidateForClicked(List<BlockPos> markers, BlockPos clicked) {
        int maxSize = Math.max(1, ModConfig.DATA.maxQuarrySize);
        int maxTopDelta = maxSize + 2;
        List<Candidate> candidates = new ArrayList<>();

        for (BlockPos origin : markers) {
            int x0 = origin.getX();
            int y0 = origin.getY();
            int z0 = origin.getZ();

            List<BlockPos> xMarkers = new ArrayList<>();
            List<BlockPos> zMarkers = new ArrayList<>();
            List<BlockPos> topMarkers = new ArrayList<>();
            BlockPos highestTopMarker = null;

            for (BlockPos marker : markers) {
                if (marker.equals(origin)) continue;
                if (marker.getZ() == z0 && marker.getX() != x0) {
                    xMarkers.add(marker);
                }
                if (marker.getX() == x0 && marker.getZ() != z0) {
                    zMarkers.add(marker);
                }
                if (marker.getX() == x0 && marker.getZ() == z0 && marker.getY() > y0 && marker.getY() - y0 <= maxTopDelta) {
                    topMarkers.add(marker);
                    if (highestTopMarker == null || marker.getY() > highestTopMarker.getY()) highestTopMarker = marker;
                }
            }

            if (xMarkers.isEmpty() || zMarkers.isEmpty()) continue;

            BlockPos selectedTopMarker = null;
            if (!topMarkers.isEmpty()) {
                for (BlockPos topMarker : topMarkers) {
                    if (topMarker.equals(clicked)) {
                        selectedTopMarker = topMarker;
                        break;
                    }
                }
                if (selectedTopMarker == null) selectedTopMarker = highestTopMarker;
            }
            int topY = selectedTopMarker != null ? selectedTopMarker.getY() : Math.min(y0 + 4, y0 + maxTopDelta);
            if (topY - y0 < 2) continue;

            for (BlockPos xMarker : xMarkers) {
                for (BlockPos zMarker : zMarkers) {
                    int minX = Math.min(x0, xMarker.getX());
                    int maxX = Math.max(x0, xMarker.getX());
                    int minZ = Math.min(z0, zMarker.getZ());
                    int maxZ = Math.max(z0, zMarker.getZ());

                    int minedWidth = maxX - minX - 1;
                    int minedLength = maxZ - minZ - 1;
                    if (minedWidth < 1 || minedLength < 1) continue;
                    if (minedWidth > maxSize || minedLength > maxSize) continue;

                    List<BlockPos> members = new ArrayList<>(4);
                    members.add(origin);
                    members.add(xMarker);
                    members.add(zMarker);
                    if (selectedTopMarker != null) members.add(selectedTopMarker);

                    boolean containsClicked = false;
                    for (BlockPos member : members) {
                        if (member.equals(clicked)) {
                            containsClicked = true;
                            break;
                        }
                    }
                    if (!containsClicked) continue;

                    candidates.add(new Candidate(origin, minX, maxX, y0, topY, minZ, maxZ, members));
                }
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator
                .comparingInt(Candidate::volume)
                .thenComparing((Candidate c) -> c.members.size() < 4));
        return candidates.get(0);
    }

    private static List<BlockPos> scanMarkers(ServerWorld world, BlockPos around) {
        int rXZ = ModConfig.DATA.maxQuarrySize + 2;
        int rY = ModConfig.DATA.maxQuarrySize + 2;
        List<BlockPos> markers = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = around.getX() - rXZ; x <= around.getX() + rXZ; x++) {
            for (int z = around.getZ() - rXZ; z <= around.getZ() + rXZ; z++) {
                for (int y = around.getY() - rY; y <= around.getY() + rY; y++) {
                    mutable.set(x, y, z);
                    if (world.getBlockState(mutable).isOf(ModBlocks.MARKER)) {
                        markers.add(mutable.toImmutable());
                    }
                }
            }
        }
        return markers;
    }

    private static WorldPreviewState stateFor(ServerWorld world) {
        return PREVIEW_STATES.computeIfAbsent(world.getRegistryKey(), k -> new WorldPreviewState());
    }

    private static final class WorldPreviewState {
        private final Map<Long, Preview> byOrigin = new HashMap<>();
        private final Map<Long, Long> markerToOrigin = new HashMap<>();
    }

    private record Preview(long originPacked,
                           int minX, int maxX,
                           int minY, int maxY,
                           int minZ, int maxZ,
                           long[] memberPacked) {
    }

    private record Candidate(BlockPos origin,
                             int minX, int maxX,
                             int minY, int maxY,
                             int minZ, int maxZ,
                             List<BlockPos> members) {
        private int volume() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }
}
