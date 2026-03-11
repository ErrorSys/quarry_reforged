package com.errorsys.quarry_reforged.content.blockentity;

import com.errorsys.quarry_reforged.content.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class QuarryMarkerBlockEntity extends BlockEntity {
    private boolean previewActive = false;
    private boolean invalidCardinalPreviewActive = false;
    private int invalidCardinalRange = 0;
    private long originPacked = 0L;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private long[] linkedMarkers = new long[0];

    public QuarryMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY_MARKER, pos, state);
    }

    public void setPreview(BlockPos origin,
                           int minX, int maxX,
                           int minY, int maxY,
                           int minZ, int maxZ,
                           List<BlockPos> linkedMarkers) {
        this.previewActive = true;
        this.invalidCardinalPreviewActive = false;
        this.invalidCardinalRange = 0;
        this.originPacked = origin.asLong();
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.linkedMarkers = linkedMarkers.stream().mapToLong(BlockPos::asLong).toArray();
        sync();
    }

    public void clearPreview() {
        if (!previewActive && !invalidCardinalPreviewActive && invalidCardinalRange == 0 && originPacked == 0L && linkedMarkers.length == 0) return;
        previewActive = false;
        invalidCardinalPreviewActive = false;
        invalidCardinalRange = 0;
        originPacked = 0L;
        linkedMarkers = new long[0];
        sync();
    }

    public void setInvalidCardinalPreview(int range) {
        int clampedRange = Math.max(1, range);
        if (invalidCardinalPreviewActive && invalidCardinalRange == clampedRange && !previewActive) return;
        this.previewActive = false;
        this.originPacked = 0L;
        this.linkedMarkers = new long[0];
        this.invalidCardinalPreviewActive = true;
        this.invalidCardinalRange = clampedRange;
        sync();
    }

    public boolean hasPreview() {
        return previewActive;
    }

    public boolean hasInvalidCardinalPreview() {
        return invalidCardinalPreviewActive;
    }

    public int getInvalidCardinalRange() {
        return invalidCardinalRange;
    }

    public boolean isOriginMarker() {
        return previewActive && originPacked == pos.asLong();
    }

    @Nullable
    public BlockPos getOriginPos() {
        if (originPacked == 0L) return null;
        return BlockPos.fromLong(originPacked);
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public List<BlockPos> getLinkedMarkers() {
        List<BlockPos> result = new ArrayList<>(linkedMarkers.length);
        for (long packed : linkedMarkers) {
            result.add(BlockPos.fromLong(packed));
        }
        return result;
    }

    private void sync() {
        markDirty();
        if (world != null) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("PreviewActive", previewActive);
        nbt.putBoolean("InvalidCardinalPreviewActive", invalidCardinalPreviewActive);
        nbt.putInt("InvalidCardinalRange", invalidCardinalRange);
        nbt.putLong("OriginPacked", originPacked);
        nbt.putInt("MinX", minX);
        nbt.putInt("MaxX", maxX);
        nbt.putInt("MinY", minY);
        nbt.putInt("MaxY", maxY);
        nbt.putInt("MinZ", minZ);
        nbt.putInt("MaxZ", maxZ);
        nbt.putLongArray("LinkedMarkers", linkedMarkers);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        previewActive = nbt.getBoolean("PreviewActive");
        invalidCardinalPreviewActive = nbt.getBoolean("InvalidCardinalPreviewActive");
        invalidCardinalRange = nbt.getInt("InvalidCardinalRange");
        originPacked = nbt.getLong("OriginPacked");
        minX = nbt.getInt("MinX");
        maxX = nbt.getInt("MaxX");
        minY = nbt.getInt("MinY");
        maxY = nbt.getInt("MaxY");
        minZ = nbt.getInt("MinZ");
        maxZ = nbt.getInt("MaxZ");
        linkedMarkers = nbt.getLongArray("LinkedMarkers");
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        return nbt;
    }
}
