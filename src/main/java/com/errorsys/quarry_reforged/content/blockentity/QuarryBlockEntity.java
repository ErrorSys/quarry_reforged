package com.errorsys.quarry_reforged.content.blockentity;

import com.errorsys.quarry_reforged.config.ModConfig;
import com.errorsys.quarry_reforged.content.ModBlocks;
import com.errorsys.quarry_reforged.content.ModItems;
import com.errorsys.quarry_reforged.content.block.QuarryBlock;
import com.errorsys.quarry_reforged.content.block.QuarryMarkerPreviewService;
import com.errorsys.quarry_reforged.event.QuarryMineCallback;
import com.errorsys.quarry_reforged.machine.config.ItemIoGroup;
import com.errorsys.quarry_reforged.machine.config.MachineIoMode;
import com.errorsys.quarry_reforged.machine.config.MachineRedstoneMode;
import com.errorsys.quarry_reforged.machine.config.MachineRelativeSide;
import com.errorsys.quarry_reforged.util.ChunkTickets;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.state.property.Properties;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;

import java.util.*;

@SuppressWarnings({
        "UnstableApiUsage",
        "unused",
        "deprecation",
        "BooleanMethodIsAlwaysInverted",
        "DataFlowIssue"
})
public class QuarryBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {
    private static final int MAX_SPEED_UPGRADES = 4;
    private static final Set<String> INTERNAL_BLOCK_BLACKLIST = Set.of(
            "minecraft:end_portal_frame",
            "minecraft:end_portal",
            "minecraft:nether_portal",
            "minecraft:spawner",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:barrier",
            "minecraft:structure_block",
            "minecraft:jigsaw"
    );

    // Inventories
    private final SimpleInventory output = new SimpleInventory(18, this::onInternalInventoryDirty);
    private final SimpleInventory upgrades = new SimpleInventory(8, this::onInternalInventoryDirty);
    private final SimpleInventory energyItems = new SimpleInventory(1, this::onInternalInventoryDirty);

    // Owner UUID
    private UUID owner;

    // State
    private boolean active = false;
    private Mode mode = Mode.EXPORT;
    private MachineRedstoneMode redstoneMode = MachineRedstoneMode.IGNORED;
    private boolean autoExportEnabled = true;
    private final MachineIoMode[] upgradeSideModes = createDefaultModes(ItemIoGroup.UPGRADES);
    private final MachineIoMode[] outputSideModes = createDefaultModes(ItemIoGroup.OUTPUT);

    // Area bounds (XYZ)
    private boolean areaLocked = false;
    private int minX, maxX, minY, maxY, minZ, maxZ;

    // Frame
    private int frameClearanceIndex = 0;
    private List<BlockPos> cachedFrameClearance = null;
    private int frameIndex = 0;
    private List<BlockPos> cachedFrame = null;
    private List<BlockPos> cachedFrameRemoval = null;
    private Set<Long> cachedFrameLookup = null;
    private final ArrayDeque<BlockPos> pendingFrameRepairs = new ArrayDeque<>();
    private boolean frameRemovalActive = false;
    private int frameRemovalIndex = -1;
    private int lastFrameCheckLayerY = Integer.MIN_VALUE;
    private long nextFrameIntegrityCheckTick = 0L;

    // Mining cursor
    private int startTopY = Integer.MIN_VALUE;
    private int layerY = Integer.MIN_VALUE;
    private int cursorX;
    private int cursorZ;
    private boolean xForward = true;
    private boolean zForward = true;
    private int gantryEntryLayerY = Integer.MIN_VALUE;
    private boolean gantryEntryDeferredDropPending = false;
    private double frameWorkBudget = 0.0;
    private double miningBudget = 0.0;
    private final ArrayDeque<BlockPos> pendingRediscoveryTargets = new ArrayDeque<>();
    private final Set<Long> pendingRediscoveryTargetSet = new HashSet<>();
    private final ArrayDeque<BlockPos> pendingInsetRediscoveryTargets = new ArrayDeque<>();
    private final Set<Long> pendingInsetRediscoveryTargetSet = new HashSet<>();
    private int rediscoveryLayerY = Integer.MIN_VALUE;
    private long nextRediscoveryScanTick = 0L;
    private boolean drainRediscoveryQueue = false;
    private boolean rediscoveryFullRescanPending = false;
    private long activeMiningTargetPacked = 0L;
    private int activeMiningTargetType = -1;
    private ReturnPhase returnPhase = ReturnPhase.NONE;
    private boolean suppressGantryDuringReturn = false;
    private boolean finalRediscoverySweepDone = false;
    private boolean finished = false;
    private StatusState statusState = StatusState.IDLE;
    private boolean insufficientEnergyForNextOp = false;
    private long requiredEnergyForNextOp = 0L;
    private String statusMessage = "";
    private int statusMessageColor = 0xFF9AA3B2;
    private long statusMessageUntilTick = -1L;
    private boolean debugVisualPreview = false;
    @Nullable
    private RenderChannelPhase debugForcedRenderPhase = null;
    private boolean debugFreezeAnimation = false;
    private long debugFrozenAnimationTick = 0L;
    private boolean debugInterpolationEnabled = true;
    private RenderChannelPhase renderChannelPhase = RenderChannelPhase.NONE;
    private long renderChannelPhaseStartedTick = 0L;
    private long renderChannelStateTick = 0L;
    private long renderChannelWaypointCurrentPacked = 0L;
    private long renderChannelWaypointNextPacked = 0L;
    private long renderChannelActiveTargetPacked = 0L;
    private long renderChannelRecentTargetPacked = 0L;
    private long renderChannelRecentTargetUntilTick = 0L;

    // Client render (tool head position)
    private long clientTargetPacked = 0L;
    private double toolHeadX = Double.NaN;
    private double toolHeadY = Double.NaN;
    private double toolHeadZ = Double.NaN;

    // Energy
    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(
            ModConfig.DATA.energyCapacity, ModConfig.DATA.maxEnergyInput, 0
    ) {
        @Override protected void onFinalCommit() {
            markDirty();
            if (world instanceof ServerWorld sw) {
                BlockState state = sw.getBlockState(pos);
                sw.updateListeners(pos, state, state, Block.NOTIFY_ALL);
            }
        }
    };

    // GUI props (ints only)
    private final PropertyDelegate props = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) (energy.amount >>> 32);
                case 1 -> (int) (energy.amount & 0xffffffffL);
                case 2 -> (int) (energy.getCapacity() >>> 32);
                case 3 -> (int) (energy.getCapacity() & 0xffffffffL);
                case 4 -> active ? 1 : 0;
                case 5 -> mode.ordinal();
                case 6 -> getProgressScaled1000();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { }
        @Override public int size() { return 7; }
    };

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(com.errorsys.quarry_reforged.content.ModBlockEntities.QUARRY, pos, state);
    }

    private static MachineIoMode[] createDefaultModes(ItemIoGroup group) {
        MachineIoMode[] modes = new MachineIoMode[MachineRelativeSide.values().length];
        Arrays.fill(modes, group.defaultMode());
        return modes;
    }

    public PropertyDelegate getPropertyDelegate() { return props; }
    public Inventory getOutputInventory() { return output; }
    public Inventory getUpgradeInventory() { return upgrades; }
    public Inventory getEnergyInventory() { return energyItems; }
    public Storage<ItemVariant> getSidedStorage(@Nullable Direction worldSide) {
        return new SidedQuarryItemStorage(this, worldSide);
    }

    public boolean canPlayerAccess(PlayerEntity player) {
        return player != null && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64 * 64;
    }

    private void onInternalInventoryDirty() {
        markDirty();
        if (world instanceof ServerWorld sw) {
            BlockState state = sw.getBlockState(pos);
            sw.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    public void onBroken() {
        if (world instanceof ServerWorld sw) ChunkTickets.clearTickets(sw, pos, this);
    }

    public void onToggleRequested() {
        if (!(world instanceof ServerWorld sw)) return;
        if (returnPhase != ReturnPhase.NONE) {
            syncState();
            return;
        }

        if (frameRemovalActive) {
            frameRemovalActive = false;
            clearActiveMiningTarget();
            clearStatusMessage();
            updateStatusState(sw);
            syncState();
            return;
        }

        if (!active && areaLocked && frameRemovalIndex >= 0) {
            if (!isRedstoneConditionMet(sw)) {
                syncState();
                return;
            }
            clearDebugVisualPreview();
            frameRemovalActive = true;
            clearStatusMessage();
            updateStatusState(sw);
            syncState();
            return;
        }

        if (active) {
            beginReturnToOrigin(sw, ReturnPhase.STOPPING);
            finished = false;
            clearStatusMessage();
            syncState();
            return;
        }

        if (!isRedstoneConditionMet(sw)) {
            syncState();
            return;
        }

        finished = false;
        if (!areaLocked) {
            boolean lockedFromMarkerPreview = lockAreaFromOriginMarkerPreview(sw);
            if (!lockedFromMarkerPreview) {
                setStatusMessage("gui.quarry_reforged.scan.fail.preview_required", 0xFFE35D5D, -1L);
                syncState();
                return;
            }
            if (!hasClearFrameBuildPath(sw)) {
                setStatusMessage("gui.quarry_reforged.status.message.frame_blocked", 0xFFE35D5D, -1L);
                syncState();
                return;
            }
            QuarryMarkerPreviewService.clearPreviewAt(sw, getOriginMarkerPos());
        } else {
            clearStatusMessage();
        }

        clearDebugVisualPreview();
        setActive(true);
        syncState();
    }

    private BlockPos getOriginMarkerPos() {
        Direction facing = getCachedState().contains(QuarryBlock.FACING) ? getCachedState().get(QuarryBlock.FACING) : Direction.NORTH;
        return pos.offset(facing.getOpposite());
    }

    private boolean lockAreaFromOriginMarkerPreview(ServerWorld sw) {
        BlockPos originMarkerPos = getOriginMarkerPos();
        if (!sw.getBlockState(originMarkerPos).isOf(ModBlocks.MARKER)) return false;
        if (!(sw.getBlockEntity(originMarkerPos) instanceof QuarryMarkerBlockEntity markerBe)) return false;
        if (!markerBe.hasPreview()) return false;
        BlockPos markerOrigin = markerBe.getOriginPos();
        if (markerOrigin == null || !markerOrigin.equals(originMarkerPos)) return false;

        lockArea(
                markerBe.getMinX(), markerBe.getMaxX(),
                markerBe.getMinY(), markerBe.getMaxY(),
                markerBe.getMinZ(), markerBe.getMaxZ()
        );
        return areaLocked;
    }

    public void onRemoveFrameRequested() {
        if (!(world instanceof ServerWorld sw)) return;
        if (!isRedstoneConditionMet(sw)) {
            syncState();
            return;
        }
        if (!canStartFrameRemoval(sw)) {
            syncState();
            return;
        }

        if (cachedFrame == null) cachedFrame = computeFrame();
        cachedFrameRemoval = null;
        frameRemovalIndex = 0;
        clearDebugVisualPreview();
        frameRemovalActive = true;
        active = false;
        frameWorkBudget = 0.0;
        miningBudget = Math.min(miningBudget, 1.0);
        clearActiveMiningTarget();
        clearStatusMessage();
        updateStatusState(sw);
        syncState();
    }

    public void onCycleRedstoneRequested() {
        if (!(world instanceof ServerWorld sw)) return;
        cycleRedstoneMode();
        updateStatusState(sw);
        syncState();
    }

    public void onToggleAutoExportRequested() {
        if (!(world instanceof ServerWorld)) return;
        toggleAutoExportEnabled();
        syncState();
    }

    public void onCycleSideModeRequested(ItemIoGroup group, MachineRelativeSide side) {
        if (!(world instanceof ServerWorld)) return;
        cycleSideMode(group, side);
        syncState();
    }

    public void setActive(boolean v) {
        if (this.active == v) return;
        this.active = v;

        if (world instanceof ServerWorld sw) {
            BlockState bs = sw.getBlockState(pos);
            if (bs.getBlock() instanceof BlockWithEntity) {
                sw.setBlockState(pos, bs.with(com.errorsys.quarry_reforged.content.block.QuarryBlock.ACTIVE, v), Block.NOTIFY_ALL);
            }

            if (!v) {
                frameWorkBudget = 0.0;
                miningBudget = 0.0;
                frameRemovalActive = false;
                frameRemovalIndex = -1;
                clearRediscoveryState();
                clearMiningTravelState();
                ChunkTickets.clearTickets(sw, pos, this);
            } else {
                clearDebugVisualPreview();
                if (areaLocked) {
                    if (!hasClearFrameBuildPath(sw)) {
                        setStatusMessage("gui.quarry_reforged.status.message.frame_blocked", 0xFFE35D5D, -1L);
                        this.active = false;
                        syncState();
                        return;
                    }
                    ensureFrameIntegrity(sw, true);
                    ensureMiningInit(sw);
                }
            }
            updateStatusState(sw);
            markDirty();
            sw.updateListeners(pos, bs, bs, Block.NOTIFY_ALL);
        }
    }

    public boolean isValidEnergyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        EnergyStorage itemEnergy = ContainerItemContext.withConstant(stack).find(EnergyStorage.ITEM);
        return itemEnergy != null && itemEnergy.supportsExtraction();
    }

    public void cycleRedstoneMode() {
        redstoneMode = redstoneMode.next();
        markDirty();
    }

    public void toggleAutoExportEnabled() {
        autoExportEnabled = !autoExportEnabled;
        mode = autoExportEnabled ? Mode.EXPORT : Mode.BUFFER;
        markDirty();
    }

    public MachineRedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public boolean isAutoExportEnabled() {
        return autoExportEnabled;
    }

    public MachineIoMode getSideMode(ItemIoGroup group, MachineRelativeSide side) {
        return getModeArray(group)[side.ordinal()];
    }

    public void cycleSideMode(ItemIoGroup group, MachineRelativeSide side) {
        MachineIoMode[] modes = getModeArray(group);
        MachineIoMode current = modes[side.ordinal()];
        modes[side.ordinal()] = group.nextMode(current);
        markDirty();
    }

    private MachineIoMode[] getModeArray(ItemIoGroup group) {
        return group == ItemIoGroup.UPGRADES ? upgradeSideModes : outputSideModes;
    }

    private boolean isRedstoneConditionMet(World queryWorld) {
        if (redstoneMode == MachineRedstoneMode.IGNORED) return true;
        boolean powered = queryWorld.isReceivingRedstonePower(pos);
        return redstoneMode.allows(powered);
    }

    private boolean isAutomationEnabled(World queryWorld) {
        return isRedstoneConditionMet(queryWorld);
    }

    private boolean sideAllows(ItemIoGroup group, Direction worldSide, boolean outputAccess) {
        Direction facing = getCachedState().contains(QuarryBlock.FACING) ? getCachedState().get(QuarryBlock.FACING) : Direction.NORTH;
        MachineRelativeSide relative = MachineRelativeSide.fromWorld(facing, worldSide);
        MachineIoMode mode = getSideMode(group, relative);
        return outputAccess ? mode.allowsOutput() : mode.allowsInput();
    }

    private void lockArea(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinY = Math.min(minY, maxY);
        int aMaxY = Math.max(minY, maxY);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);

        int w = aMaxX - aMinX + 1;
        int h = aMaxY - aMinY + 1;
        int l = aMaxZ - aMinZ + 1;
        int minedWidth = Math.max(0, w - 2);
        int minedLength = Math.max(0, l - 2);

        if (minedWidth < 1 || minedLength < 1) return;
        if (minedWidth > ModConfig.DATA.maxQuarrySize || minedLength > ModConfig.DATA.maxQuarrySize) return;
        if (h < 3) return;

        this.minX = aMinX;
        this.maxX = aMaxX;
        this.minY = aMinY;
        this.maxY = aMaxY;
        this.minZ = aMinZ;
        this.maxZ = aMaxZ;
        this.areaLocked = true;

        frameClearanceIndex = 0;
        cachedFrameClearance = null;
        frameIndex = 0;
        cachedFrame = null;
        cachedFrameRemoval = null;
        cachedFrameLookup = null;
        pendingFrameRepairs.clear();
        frameRemovalActive = false;
        frameRemovalIndex = -1;
        lastFrameCheckLayerY = Integer.MIN_VALUE;
        nextFrameIntegrityCheckTick = 0L;
        startTopY = Integer.MIN_VALUE;
        layerY = Integer.MIN_VALUE;
        frameWorkBudget = 0.0;
        miningBudget = 0.0;
        clearRediscoveryState();
        clearMiningTravelState();
    }

    // ticking
    public static void tickServer(World world, BlockPos pos, BlockState state, QuarryBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) return;

        // config sync
        if (be.energy.amount > ModConfig.DATA.energyCapacity) {
            be.energy.amount = ModConfig.DATA.energyCapacity;
        }
        be.clampUpgradeSlotsServer(sw);
        be.chargeFromEnergyItemSlot();

        be.expireStatusMessage(sw);
        be.updateStatusState(sw);

        if (!be.active && be.returnPhase == ReturnPhase.NONE && !be.frameRemovalActive) return;
        if (!be.areaLocked && be.returnPhase == ReturnPhase.NONE && !be.frameRemovalActive) return;

        if (!be.isRedstoneConditionMet(sw)) {
            be.updateStatusState(sw);
            return;
        }

        if (be.hasChunkloadUpgrade() && ModConfig.DATA.enableChunkloadingUpgrade && be.areaLocked) {
            ChunkTickets.ensureTicketsForArea(
                    sw,
                    be.pos,
                    be.minX,
                    be.maxX,
                    be.minZ,
                    be.maxZ,
                    ModConfig.DATA.chunkTicketLevel
            );
        }

        if (be.returnPhase != ReturnPhase.NONE) {
            be.miningBudget += be.getBlocksPerTick();
            be.stepReturnToOrigin(sw);
            be.miningBudget = Math.min(be.miningBudget, 1.0);
            be.updateStatusState(sw);
            return;
        }

        if (be.frameRemovalActive) {
            be.miningBudget += be.getBlocksPerTick();
            be.miningBudget = be.stepFrameRemoval(sw, be.miningBudget);
            be.miningBudget = Math.min(be.miningBudget, 1.0);
            be.updateStatusState(sw);
            return;
        }

        be.frameWorkBudget += be.getBlocksPerTick();
        int requestedFrameOps = (int) be.frameWorkBudget;
        if (requestedFrameOps > 0) {
            int completedFrameOps = be.stepFrame(sw, requestedFrameOps);
            be.frameWorkBudget -= completedFrameOps;
            if (completedFrameOps < requestedFrameOps) {
                be.frameWorkBudget = Math.min(be.frameWorkBudget, 1.0);
            }
        }
        if (be.frameIndex < be.cachedFrameSize(sw) || !be.pendingFrameRepairs.isEmpty()) return;

        if (!be.checkFrameIntegrityForCurrentLayer(sw)) return;

        be.ensureMiningInit(sw);
        be.runRediscoveryScan(sw);
        be.miningBudget += be.getBlocksPerTick();
        be.miningBudget = be.stepMine(sw, be.miningBudget);
        be.miningBudget = Math.min(be.miningBudget, 1.0);
        be.updateStatusState(sw);
    }

    private void chargeFromEnergyItemSlot() {
        if (world == null) return;
        if (energy.amount >= energy.getCapacity()) return;

        ItemStack stack = energyItems.getStack(0);
        if (stack.isEmpty()) return;

        var slot = InventoryStorage.of(energyItems, null).getSlot(0);
        EnergyStorage itemEnergy = ContainerItemContext.ofSingleSlot(slot).find(EnergyStorage.ITEM);
        if (itemEnergy == null || !itemEnergy.supportsExtraction()) return;

        long room = energy.getCapacity() - energy.amount;
        long maxMove = Math.min(room, energy.maxInsert);
        if (maxMove <= 0) return;

        try (Transaction tx = Transaction.openOuter()) {
            long moved = EnergyStorageUtil.move(itemEnergy, energy, maxMove, tx);
            if (moved > 0) {
                tx.commit();
                markDirty();
            }
        }
    }

    private int cachedFrameSize(ServerWorld sw) {
        if (cachedFrame == null) cachedFrame = computeFrame();
        return cachedFrame.size();
    }

    @Nullable
    private WorkTarget getNextFrameTarget(ServerWorld sw) {
        normalizeFrameProgress(sw);

        if (!pendingFrameRepairs.isEmpty()) {
            return new WorkTarget(pendingFrameRepairs.peekFirst(), WorkType.FRAME_REPAIR);
        }
        if (cachedFrame == null) cachedFrame = computeFrame();
        if (frameIndex < cachedFrame.size()) {
            return new WorkTarget(cachedFrame.get(frameIndex), WorkType.FRAME_PLACE);
        }
        return null;
    }

    private int stepFrame(ServerWorld sw, int ops) {
        int workDone = 0;
        while (workDone < ops) {
            WorkTarget target = getNextFrameTarget(sw);
            if (target == null) return workDone;

            switch (target.type()) {
                case FRAME_PLACE, FRAME_REPAIR -> {
                    BlockState existing = sw.getBlockState(target.pos());
                    if (!existing.isOf(ModBlocks.FRAME)) {
                        if (energy.amount < ModConfig.DATA.energyPerFrame) return workDone;
                        boolean hasFluid = !existing.getFluidState().isEmpty();
                        if (!existing.isAir() && !hasFluid && !harvestBlock(sw, target.pos(), existing)) return workDone;
                        sw.setBlockState(target.pos(), ModBlocks.FRAME.getDefaultState(), Block.NOTIFY_ALL);
                        energy.amount -= ModConfig.DATA.energyPerFrame;
                    }
                    if (target.type() == WorkType.FRAME_REPAIR) {
                        pendingFrameRepairs.removeFirst();
                    } else {
                        frameIndex++;
                    }
                }
                default -> { }
            }
            workDone++;
        }
        return workDone;
    }

    @Nullable
    private BlockPos getNextFrameRemovalTarget(ServerWorld sw) {
        List<BlockPos> removalOrder = getCachedFrameRemovalOrder();
        if (frameRemovalIndex < 0) frameRemovalIndex = 0;

        while (frameRemovalIndex < removalOrder.size()) {
            BlockPos target = removalOrder.get(frameRemovalIndex);
            if (sw.getBlockState(target).isOf(ModBlocks.FRAME)) return target;
            frameRemovalIndex++;
        }
        return null;
    }

    private double stepFrameRemoval(ServerWorld sw, double budget) {
        while (budget >= 1.0E-6) {
            BlockPos target = getNextFrameRemovalTarget(sw);
            if (target == null) {
                finishFrameRemoval(sw);
                return budget;
            }

            setActiveMiningTarget(new WorkTarget(target, WorkType.FRAME_REMOVE));

            if (budget < 1.0) return budget;
            if (energy.amount < ModConfig.DATA.energyPerFrame) return budget;
            if (!sw.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS)) return budget;

            recordRecentRenderTarget(sw, target);
            energy.amount -= ModConfig.DATA.energyPerFrame;
            frameRemovalIndex++;
            clearActiveMiningTarget();
            budget -= 1.0;
            markDirty();
        }
        return budget;
    }

    private void finishFrameRemoval(ServerWorld sw) {
        frameRemovalActive = false;
        frameRemovalIndex = -1;
        clearActiveMiningTarget();
        clearStatusMessage();
        ChunkTickets.clearTickets(sw, pos, this);

        frameClearanceIndex = 0;
        cachedFrameClearance = null;
        frameIndex = 0;
        cachedFrame = null;
        cachedFrameRemoval = null;
        cachedFrameLookup = null;
        pendingFrameRepairs.clear();
        lastFrameCheckLayerY = Integer.MIN_VALUE;
        nextFrameIntegrityCheckTick = 0L;
        startTopY = Integer.MIN_VALUE;
        layerY = Integer.MIN_VALUE;
        clientTargetPacked = 0L;
        frameWorkBudget = 0.0;
        miningBudget = 0.0;
        clearRediscoveryState();
        clearMiningTravelState();
        areaLocked = false;
        finished = false;

        updateStatusState(sw);
        markDirty();
    }

    private boolean checkFrameIntegrityForCurrentLayer(ServerWorld sw) {
        if (layerY == Integer.MIN_VALUE) return true;
        if (layerY == lastFrameCheckLayerY) return true;
        if (sw.getTime() < nextFrameIntegrityCheckTick) return true;

        lastFrameCheckLayerY = layerY;
        return ensureFrameIntegrity(sw, false);
    }

    private boolean ensureFrameIntegrity(ServerWorld sw, boolean force) {
        if (cachedFrame == null) cachedFrame = computeFrame();
        if (!force && sw.getTime() < nextFrameIntegrityCheckTick) return true;

        collectMissingFrameBlocks(sw);
        nextFrameIntegrityCheckTick = sw.getTime() + 20L;
        if (pendingFrameRepairs.isEmpty()) return true;
        updateStatusState(sw);
        return false;
    }

    private void collectMissingFrameBlocks(ServerWorld sw) {
        if (cachedFrame == null) cachedFrame = computeFrame();
        pendingFrameRepairs.clear();
        for (BlockPos framePos : cachedFrame) {
            if (!sw.getBlockState(framePos).isOf(ModBlocks.FRAME)) {
                pendingFrameRepairs.addLast(framePos);
            }
        }
    }

    private List<BlockPos> computeFrame() {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();

        addOrderedFrameRing(out, minY);

        for (int y = minY + 1; y < maxY; y++) {
            addOrderedPosts(out, y);
        }

        if (ModConfig.DATA.buildDescendingRing) {
            for (int y = minY + ModConfig.DATA.frameCrossmemberSpacing; y < maxY; y += ModConfig.DATA.frameCrossmemberSpacing) {
                addOrderedFrameRing(out, y);
            }
        }

        addOrderedFrameRing(out, maxY);
        return new ArrayList<>(out);
    }

    private Set<Long> getCachedFrameLookup() {
        if (cachedFrame == null) cachedFrame = computeFrame();
        if (cachedFrameLookup == null) {
            cachedFrameLookup = new HashSet<>(cachedFrame.size());
            for (BlockPos framePos : cachedFrame) {
                cachedFrameLookup.add(framePos.asLong());
            }
        }
        return cachedFrameLookup;
    }

    private List<BlockPos> getCachedFrameRemovalOrder() {
        if (cachedFrameRemoval == null) cachedFrameRemoval = computeFrameRemovalOrder();
        return cachedFrameRemoval;
    }

    private List<BlockPos> computeFrameRemovalOrder() {
        LinkedHashSet<BlockPos> ordered = new LinkedHashSet<>();

        List<BlockPos> topRing = buildRemovalRingLineList(maxY);
        if (cachedFrame == null) cachedFrame = computeFrame();
        if (!cachedFrame.isEmpty()) {
            BlockPos oldStart = cachedFrame.get(cachedFrame.size() - 1);
            int startIdx = topRing.indexOf(oldStart);
            if (startIdx > 0) Collections.rotate(topRing, -startIdx);
        }
        ordered.addAll(topRing);

        if (ModConfig.DATA.buildDescendingRing && ModConfig.DATA.frameCrossmemberSpacing > 0) {
            for (int y = maxY - 1; y > minY; y--) {
                if ((y - minY) % ModConfig.DATA.frameCrossmemberSpacing == 0) {
                    addRemovalRingLines(ordered, y);
                }
            }
        }

        addRemovalPostLines(ordered);
        addRemovalRingLines(ordered, minY);

        return new ArrayList<>(ordered);
    }

    private void addRemovalRingLines(Set<BlockPos> out, int y) {
        out.addAll(buildRemovalRingLineList(y));
    }

    private List<BlockPos> buildRemovalRingLineList(int y) {
        List<BlockPos> ring = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) ring.add(new BlockPos(x, y, minZ));
        for (int z = minZ + 1; z <= maxZ; z++) ring.add(new BlockPos(maxX, y, z));
        for (int x = maxX - 1; x >= minX; x--) ring.add(new BlockPos(x, y, maxZ));
        for (int z = maxZ - 1; z > minZ; z--) ring.add(new BlockPos(minX, y, z));
        return ring;
    }

    private void addRemovalPostLines(Set<BlockPos> out) {
        int[][] corners = new int[][]{
                {minX, minZ},
                {maxX, minZ},
                {maxX, maxZ},
                {minX, maxZ}
        };
        for (int[] corner : corners) {
            for (int y = maxY - 1; y > minY; y--) {
                out.add(new BlockPos(corner[0], y, corner[1]));
            }
        }
    }

    private List<BlockPos> computeFrameClearance() {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
        Set<BlockPos> framePositions = new HashSet<>(computeFrame());

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean onBoundary = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    if (!onBoundary) continue;
                    if (y == minY) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    if (!framePositions.contains(pos)) out.add(pos);
                }
            }
        }

        return new ArrayList<>(out);
    }

    private void addOrderedFrameRing(Set<BlockPos> out, int y) {
        List<BlockPos> ring = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            ring.add(new BlockPos(x, y, minZ));
            ring.add(new BlockPos(x, y, maxZ));
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            ring.add(new BlockPos(minX, y, z));
            ring.add(new BlockPos(maxX, y, z));
        }

        ring.stream()
                .distinct()
                .sorted(Comparator
                        .comparingInt((BlockPos p) -> p.getManhattanDistance(this.pos))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .forEach(out::add);
    }

    private void addOrderedPosts(Set<BlockPos> out, int y) {
        List.of(
                new BlockPos(minX, y, minZ),
                new BlockPos(minX, y, maxZ),
                new BlockPos(maxX, y, minZ),
                new BlockPos(maxX, y, maxZ)
        ).stream()
                .sorted(Comparator
                        .comparingInt((BlockPos p) -> p.getManhattanDistance(this.pos))
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .forEach(out::add);
    }

    private boolean hasMineableInterior() {
        return maxX - minX >= 2 && maxY - minY >= 2 && maxZ - minZ >= 2;
    }

    private boolean hasClearFrameBuildPath(ServerWorld sw) {
        for (BlockPos setupPos : getFrameSetupPositions()) {
            BlockState state = sw.getBlockState(setupPos);
            if (state.isAir() || state.isOf(ModBlocks.FRAME)) continue;
            if (isFrameSetupBlocked(sw, setupPos, state)) return false;
        }
        return true;
    }

    private List<BlockPos> getFrameSetupPositions() {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>(computeFrameClearance());
        out.addAll(computeFrame());
        return new ArrayList<>(out);
    }

    private void ensureMiningInit(ServerWorld sw) {
        if (layerY != Integer.MIN_VALUE) return;
        if (!hasMineableInterior()) {
            setActive(false);
            return;
        }

        int topY = findHighestMineableY(sw);
        if (topY == Integer.MIN_VALUE) {
            clientTargetPacked = 0L;
            stopAndRemoveFrame();
            return;
        }

        startTopY = topY;
        layerY = topY;
        cursorX = minX + 1;
        cursorZ = minZ + 1;
        xForward = true;
        zForward = true;
        lastFrameCheckLayerY = Integer.MIN_VALUE;
        frameWorkBudget = 0.0;
        miningBudget = 0.0;
        clearRediscoveryState();
        gantryEntryLayerY = Integer.MIN_VALUE;
        gantryEntryDeferredDropPending = false;
        clearMiningTravelState();
        rediscoveryLayerY = maxY;
        finalRediscoverySweepDone = false;
        setToolHeadPos(getToolHeadOriginPos());

        if (!seekNextMineable(sw)) {
            clientTargetPacked = 0L;
            stopAndRemoveFrame();
        }
        updateStatusState(sw);
    }

    @Nullable
    private WorkTarget getNextMiningTarget(ServerWorld sw) {
        WorkTarget activeTarget = getActiveMiningTarget(sw);
        if (activeTarget != null) return activeTarget;

        if (drainRediscoveryQueue) {
            BlockPos insetTarget = peekInsetRediscoveryTarget(sw);
            if (insetTarget != null) {
                WorkTarget target = new WorkTarget(insetTarget, WorkType.REDISCOVERY_INSET);
                setActiveMiningTarget(target);
                return target;
            }
            BlockPos rediscoveryTarget = peekRediscoveryTarget(sw);
            if (rediscoveryTarget != null) {
                WorkTarget target = new WorkTarget(rediscoveryTarget, WorkType.REDISCOVERY);
                setActiveMiningTarget(target);
                return target;
            }
            drainRediscoveryQueue = false;
        }

        if (!seekNextMineable(sw)) return null;
        WorkTarget target = new WorkTarget(new BlockPos(cursorX, layerY, cursorZ), WorkType.MINE);
        setActiveMiningTarget(target);
        return target;
    }

    private double stepMine(ServerWorld sw, double budget) {
        int bottom = sw.getBottomY();
        while (budget > 1.0E-6) {
            WorkTarget target = getNextMiningTarget(sw);
            if (target == null) {
                if (!finalRediscoverySweepDone) {
                    runFinalRediscoveryScan(sw);
                    finalRediscoverySweepDone = true;
                    if (hasPendingRediscoveryWork()) {
                        drainRediscoveryQueue = true;
                        continue;
                    }
                }
                beginReturnToOrigin(sw, ReturnPhase.FINISHING);
                return budget;
            }

            if (!isTopLaserPhaseTarget(target)) {
                Vec3d targetPos = Vec3d.ofCenter(target.pos());
                double distance = getToolHeadPos().distanceTo(targetPos);
                if (distance > 1.0E-6) {
                    double moved = moveToolHeadTowards(targetPos, budget);
                    budget -= moved;
                    if (budget <= 1.0E-6) return 0.0;
                    continue;
                }
            }

            if (budget < 1.0) return budget;

            BlockState st = sw.getBlockState(target.pos());
            if (!harvestBlock(sw, target.pos(), st)) return Math.min(budget, 1.0);

            recordRecentRenderTarget(sw, target.pos());
            clearActiveMiningTarget();
            if (target.type() == WorkType.REDISCOVERY_INSET) {
                completeInsetRediscoveryTarget();
            } else if (target.type() == WorkType.REDISCOVERY) {
                completeRediscoveryTarget();
            } else {
                int previousLayerY = layerY;
                advanceCursor();
                if (previousLayerY >= minY && layerY < minY) {
                    if (!reseedCursorForGantryPhase(sw)) {
                        clearActiveMiningTarget();
                        beginReturnToOrigin(sw, ReturnPhase.FINISHING);
                        return budget;
                    }
                }
                if (layerY < previousLayerY) {
                    drainRediscoveryQueue = hasPendingRediscoveryWork();
                }
            }
            budget -= 1.0;

            if (target.type() == WorkType.MINE && !drainRediscoveryQueue && layerY < bottom) {
                clientTargetPacked = 0L;
                stopAndRemoveFrame();
                return 0.0;
            }
        }
        return budget;
    }

    private boolean reseedCursorForGantryPhase(ServerWorld sw) {
        if (layerY == Integer.MIN_VALUE) return false;
        BlockPos start = findHighestMineableNearQuarry(sw);
        if (start == null) return false;

        cursorX = start.getX();
        layerY = start.getY();
        cursorZ = start.getZ();
        int innerMinX = minX + 1;
        int innerMaxX = maxX - 1;
        int innerMinZ = minZ + 1;
        int innerMaxZ = maxZ - 1;

        if (cursorX <= innerMinX) {
            xForward = true;
        } else if (cursorX >= innerMaxX) {
            xForward = false;
        } else {
            xForward = cursorX <= (innerMinX + innerMaxX) / 2;
        }

        if (cursorZ <= innerMinZ) {
            zForward = true;
        } else if (cursorZ >= innerMaxZ) {
            zForward = false;
        } else {
            zForward = cursorZ <= (innerMinZ + innerMaxZ) / 2;
        }
        gantryEntryLayerY = layerY;
        gantryEntryDeferredDropPending = false;
        return seekNextMineable(sw);
    }

    @Nullable
    private BlockPos findHighestMineableNearQuarry(ServerWorld sw) {
        int bottom = sw.getBottomY();
        int homeX = MathHelper.clamp(pos.getX(), minX + 1, maxX - 1);
        int homeZ = MathHelper.clamp(pos.getZ(), minZ + 1, maxZ - 1);
        int topGantryY = minY - 1;

        for (int y = topGantryY; y >= bottom; y--) {
            BlockPos best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int z = minZ + 1; z <= maxZ - 1; z++) {
                for (int x = minX + 1; x <= maxX - 1; x++) {
                    BlockPos target = new BlockPos(x, y, z);
                    if (!isMineableTarget(sw, target, sw.getBlockState(target))) continue;

                    int distance = Math.abs(x - homeX) + Math.abs(z - homeZ);
                    if (best == null || distance < bestDistance) {
                        best = target;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private void clampUpgradeSlotsServer(ServerWorld sw) {
        boolean changed = false;
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getCount() <= 1) continue;

            ItemStack overflow = stack.copy();
            overflow.decrement(1);
            stack.setCount(1);
            output.insertAll(overflow);
            if (!overflow.isEmpty()) {
                ItemScatterer.spawn(sw, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, overflow);
            }
            changed = true;
        }

        if (changed) {
            markDirty();
            BlockState state = sw.getBlockState(pos);
            sw.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        }
    }

    private boolean isTopLaserPhaseTarget(WorkTarget target) {
        if (target.type() == WorkType.REDISCOVERY_INSET) return true;
        if (target.type() == WorkType.REDISCOVERY) return false;
        return target.pos().getY() >= minY;
    }

    private int findHighestMineableY(ServerWorld sw) {
        int bottom = sw.getBottomY();
        for (int y = maxY; y >= bottom; y--) {
            for (int z = minZ + 1; z <= maxZ - 1; z++) {
                for (int x = minX + 1; x <= maxX - 1; x++) {
                    BlockPos target = new BlockPos(x, y, z);
                    if (isMineableTarget(sw, target, sw.getBlockState(target))) return y;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean seekNextMineable(ServerWorld sw) {
        int bottom = sw.getBottomY();
        while (layerY >= bottom) {
            BlockPos target = new BlockPos(cursorX, layerY, cursorZ);
            BlockState state = sw.getBlockState(target);
            if (isMineableTarget(sw, target, state)) {
                return true;
            }
            advanceCursor();
        }
        return false;
    }

    private boolean isMineableTarget(ServerWorld sw, BlockPos target, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (isBlacklisted(state)) return false;
        if (state.getHardness(sw, target) < 0.0f) return false;

        UUID own = owner == null ? new UUID(0, 0) : owner;
        ActionResult hook = QuarryMineCallback.EVENT.invoker().canMine(sw, pos, own, target, state);
        return hook != ActionResult.FAIL;
    }

    private boolean isFrameSetupBlocked(ServerWorld sw, BlockPos target, BlockState state) {
        if (state.isAir() || state.isOf(ModBlocks.FRAME)) return false;
        if (isBlacklisted(state)) return true;
        if (state.getHardness(sw, target) < 0.0f) return true;

        UUID own = owner == null ? new UUID(0, 0) : owner;
        ActionResult hook = QuarryMineCallback.EVENT.invoker().canMine(sw, pos, own, target, state);
        return hook == ActionResult.FAIL;
    }

    private boolean isBlacklisted(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        return INTERNAL_BLOCK_BLACKLIST.contains(id) || ModConfig.DATA.blacklist.contains(id);
    }

    private List<ItemStack> collectBlockEntityInventoryDrops(@Nullable BlockEntity targetBe) {
        if (!(targetBe instanceof Inventory inventory)) return Collections.emptyList();
        return collectInventoryDrops(inventory);
    }

    private List<EntityInventoryDrop> collectInventoryEntityDrops(ServerWorld sw, BlockPos target) {
        List<EntityInventoryDrop> drops = new ArrayList<>();
        for (Entity entity : sw.getOtherEntities(null, new Box(target), candidate -> candidate instanceof Inventory)) {
            if (!(entity instanceof Inventory inventory)) continue;

            List<ItemStack> entityDrops = collectInventoryDrops(inventory);
            if (entity instanceof ChestMinecartEntity) {
                entityDrops.add(new ItemStack(Items.CHEST_MINECART));
            } else if (entity instanceof HopperMinecartEntity) {
                entityDrops.add(new ItemStack(Items.HOPPER_MINECART));
            } else {
                continue;
            }

            drops.add(new EntityInventoryDrop(entity, entityDrops));
        }
        return drops;
    }

    private boolean harvestBlock(ServerWorld sw, BlockPos target, BlockState state) {
        if (state.isAir()) return true;
        if (isFrameSetupBlocked(sw, target, state)) {
            setStatusMessage("gui.quarry_reforged.status.message.frame_blocked", 0xFFE35D5D, -1L);
            setActive(false);
            return false;
        }

        List<BlockPos> harvestPositions = collectHarvestPositions(sw, target, state);
        float hardness = state.getHardness(sw, target);
        long cost = ModConfig.DATA.energyPerBlock + (long) (Math.max(0.0f, hardness) * ModConfig.DATA.hardnessEnergyScale);
        if (energy.amount < cost) return false;

        ItemStack tool = makeToolForDrops();
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos harvestPos : harvestPositions) {
            BlockState harvestState = sw.getBlockState(harvestPos);
            if (harvestState.isAir()) continue;
            BlockEntity harvestBe = sw.getBlockEntity(harvestPos);
            drops.addAll(Block.getDroppedStacks(harvestState, sw, harvestPos, harvestBe, null, tool));
            drops.addAll(collectBlockEntityInventoryDrops(harvestBe));
        }
        List<EntityInventoryDrop> entityDrops = collectInventoryEntityDrops(sw, target);
        for (EntityInventoryDrop entityDrop : entityDrops) drops.addAll(entityDrop.drops());

        boolean hasVoidOverflow = hasVoidUpgrade();
        if (!output.canFitAll(drops)) {
            if (shouldAutoExport(sw)) autoExport(sw);
            if (!output.canFitAll(drops) && !hasVoidOverflow) {
                setStatusMessage("gui.quarry_reforged.status.message.inventory_full", 0xFFE35D5D, -1L);
                setActive(false);
                return false;
            }
        }

        boolean removedAny = false;
        for (BlockPos harvestPos : harvestPositions) {
            BlockState harvestState = sw.getBlockState(harvestPos);
            if (harvestState.isAir()) continue;
            sw.syncWorldEvent(WorldEvents.BLOCK_BROKEN, harvestPos, Block.getRawIdFromState(harvestState));
            if (!sw.setBlockState(harvestPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS)) return false;
            removedAny = true;
        }
        if (!removedAny) return false;

        for (EntityInventoryDrop entityDrop : entityDrops) entityDrop.removeSource();
        energy.amount -= cost;
        for (ItemStack drop : drops) output.insertAll(drop);
        if (shouldAutoExport(sw)) autoExport(sw);
        markDirty();
        return true;
    }

    private List<BlockPos> collectHarvestPositions(ServerWorld sw, BlockPos target, BlockState state) {
        List<BlockPos> positions = new ArrayList<>(2);
        positions.add(target);

        BlockPos linked = getLinkedPartPos(target, state);
        if (linked == null) return positions;

        BlockState linkedState = sw.getBlockState(linked);
        if (!linkedState.isOf(state.getBlock())) return positions;
        if (!isMineableTarget(sw, linked, linkedState)) return positions;

        positions.add(linked);
        return positions;
    }

    @Nullable
    private BlockPos getLinkedPartPos(BlockPos pos, BlockState state) {
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            return half == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
        }
        if (state.contains(Properties.BED_PART) && state.contains(Properties.HORIZONTAL_FACING)) {
            BedPart part = state.get(Properties.BED_PART);
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            return part == BedPart.HEAD ? pos.offset(facing.getOpposite()) : pos.offset(facing);
        }
        return null;
    }

    private List<ItemStack> collectInventoryDrops(Inventory inventory) {
        List<ItemStack> extraDrops = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            extraDrops.add(stack.copy());
        }
        return extraDrops;
    }

    private void autoExport(ServerWorld sw) {
        if (!shouldAutoExport(sw)) return;
        for (Direction dir : Direction.values()) {
            if (!sideAllows(ItemIoGroup.OUTPUT, dir, true)) continue;
            BlockPos npos = pos.offset(dir);
            Storage<ItemVariant> to = ItemStorage.SIDED.find(sw, npos, dir.getOpposite());
            if (to == null) continue;

            output.pushTo(to, output.items.size());
        }
    }

    private boolean shouldAutoExport(ServerWorld sw) {
        return autoExportEnabled && isAutomationEnabled(sw);
    }

    private void advanceCursor() {
        CursorState advanced = advanceCursor(cursorX, layerY, cursorZ, xForward, zForward);
        cursorX = advanced.x;
        layerY = advanced.y;
        cursorZ = advanced.z;
        xForward = advanced.xForward;
        zForward = advanced.zForward;
    }

    private CursorState advanceCursor(int currentX, int currentY, int currentZ, boolean movingForward, boolean movingTowardMaxZ) {
        int innerMinX = minX + 1;
        int innerMaxX = maxX - 1;
        int innerMinZ = minZ + 1;
        int innerMaxZ = maxZ - 1;

        if (movingTowardMaxZ) {
            if (currentZ < innerMaxZ) return new CursorState(currentX, currentY, currentZ + 1, movingForward, true);
        } else {
            if (currentZ > innerMinZ) return new CursorState(currentX, currentY, currentZ - 1, movingForward, false);
        }

        boolean nextZDirection = !movingTowardMaxZ;
        if (movingForward) {
            if (currentX < innerMaxX) return new CursorState(currentX + 1, currentY, currentZ, true, nextZDirection);
        } else {
            if (currentX > innerMinX) return new CursorState(currentX - 1, currentY, currentZ, false, nextZDirection);
        }

        // Reverse the entire serpentine when stepping down so the next layer mirrors the previous one.
        currentY--;
        if (currentY < gantryEntryLayerY) {
            gantryEntryLayerY = Integer.MIN_VALUE;
            gantryEntryDeferredDropPending = false;
        }
        return new CursorState(currentX, currentY, currentZ, !movingForward, nextZDirection);
    }

    private void runRediscoveryScan(ServerWorld sw) {
        if (startTopY == Integer.MIN_VALUE) return;
        if (layerY == Integer.MIN_VALUE) return;
        if (sw.getTime() < nextRediscoveryScanTick) return;
        nextRediscoveryScanTick = sw.getTime() + 20L;

        // Rediscovery should only scan layers that are already fully passed by the mining cursor.
        // Scanning the current layer can enqueue still-pending blocks and make traversal appear to skip rows.
        int highestCompletedLayer = startTopY;
        int lowestTrackedLayer = sw.getBottomY();
        int lowestCompletedLayer = layerY + 1;
        if (lowestCompletedLayer > highestCompletedLayer) return;
        lowestCompletedLayer = Math.max(lowestCompletedLayer, lowestTrackedLayer);

        if (rediscoveryLayerY == Integer.MIN_VALUE || rediscoveryLayerY < lowestCompletedLayer || rediscoveryLayerY > highestCompletedLayer) {
            rediscoveryLayerY = highestCompletedLayer;
        }

        boolean detectedNewTarget = false;
        for (int layersScanned = 0; layersScanned < 4; layersScanned++) {
            if (scanInsetFacesOnLayer(sw, rediscoveryLayerY)) detectedNewTarget = true;
            if (scanRediscoveryLayer(sw, rediscoveryLayerY)) detectedNewTarget = true;

            rediscoveryLayerY--;
            if (rediscoveryLayerY < lowestCompletedLayer) {
                rediscoveryLayerY = highestCompletedLayer;
            }
        }

        // New rediscovery target found during incremental scan: cache the full tracked
        // rediscovery volume once, then return to incremental scanning cadence.
        if (detectedNewTarget && !drainRediscoveryQueue) {
            rediscoveryFullRescanPending = true;
        }
        if (rediscoveryFullRescanPending && !drainRediscoveryQueue) {
            scanRediscoveryRange(sw, highestCompletedLayer, lowestCompletedLayer);
            rediscoveryFullRescanPending = false;
        }
    }

    @Nullable
    private BlockPos peekRediscoveryTarget(ServerWorld sw) {
        while (!pendingRediscoveryTargets.isEmpty()) {
            BlockPos target = pendingRediscoveryTargets.peekFirst();
            if (target.getY() >= minY) {
                // At/above the frame floor rediscovery belongs to the laser queue.
                pendingRediscoveryTargets.removeFirst();
                pendingRediscoveryTargetSet.remove(target.asLong());
                enqueueInsetRediscoveryTarget(target);
                continue;
            }
            if (isMineableTarget(sw, target, sw.getBlockState(target))) return target;
            pendingRediscoveryTargets.removeFirst();
            pendingRediscoveryTargetSet.remove(target.asLong());
        }
        return null;
    }

    @Nullable
    private BlockPos peekInsetRediscoveryTarget(ServerWorld sw) {
        while (!pendingInsetRediscoveryTargets.isEmpty()) {
            BlockPos target = pendingInsetRediscoveryTargets.peekFirst();
            if (isMineableTarget(sw, target, sw.getBlockState(target))) return target;
            pendingInsetRediscoveryTargets.removeFirst();
            pendingInsetRediscoveryTargetSet.remove(target.asLong());
        }
        return null;
    }

    private void completeRediscoveryTarget() {
        if (pendingRediscoveryTargets.isEmpty()) return;
        BlockPos target = pendingRediscoveryTargets.removeFirst();
        pendingRediscoveryTargetSet.remove(target.asLong());
    }

    private void completeInsetRediscoveryTarget() {
        if (pendingInsetRediscoveryTargets.isEmpty()) return;
        BlockPos target = pendingInsetRediscoveryTargets.removeFirst();
        pendingInsetRediscoveryTargetSet.remove(target.asLong());
    }

    private boolean hasPendingRediscoveryWork() {
        return !pendingInsetRediscoveryTargets.isEmpty() || !pendingRediscoveryTargets.isEmpty();
    }

    private boolean enqueueRediscoveryCandidate(BlockPos target) {
        // Route all blocks at/above the frame floor to the laser/inset rediscovery queue.
        if (target.getY() >= minY) {
            return enqueueInsetRediscoveryTarget(target);
        }
        if (isInsetRediscoveryTarget(target)) {
            return enqueueInsetRediscoveryTarget(target);
        }
        return enqueueRediscoveryTarget(target);
    }

    private boolean isInsetRediscoveryTarget(BlockPos target) {
        if (!areaLocked) return false;
        int x = target.getX();
        int y = target.getY();
        int z = target.getZ();
        if (x < minX + 1 || x > maxX - 1 || y < minY || y > maxY - 1 || z < minZ + 1 || z > maxZ - 1) {
            return false;
        }

        // Exclude bottom inset face; include top inset face and inner side faces.
        if (y == minY) return false;
        boolean onTopInsetFace = y == maxY - 1;
        boolean onInnerSideFace = x == minX + 1 || x == maxX - 1 || z == minZ + 1 || z == maxZ - 1;
        return onTopInsetFace || onInnerSideFace;
    }

    private boolean enqueueRediscoveryTarget(BlockPos target) {
        long packed = target.asLong();
        if (pendingRediscoveryTargetSet.add(packed)) {
            if (pendingRediscoveryTargets.isEmpty()) {
                pendingRediscoveryTargets.addLast(target);
                return true;
            }

            List<BlockPos> reordered = new ArrayList<>(pendingRediscoveryTargets.size() + 1);
            boolean inserted = false;
            for (BlockPos queued : pendingRediscoveryTargets) {
                if (!inserted && compareRediscoveryTargets(target, queued) < 0) {
                    reordered.add(target);
                    inserted = true;
                }
                reordered.add(queued);
            }
            if (!inserted) reordered.add(target);

            pendingRediscoveryTargets.clear();
            pendingRediscoveryTargets.addAll(reordered);
            return true;
        }
        return false;
    }

    private boolean enqueueInsetRediscoveryTarget(BlockPos target) {
        long packed = target.asLong();
        if (pendingInsetRediscoveryTargetSet.add(packed)) {
            if (pendingInsetRediscoveryTargets.isEmpty()) {
                pendingInsetRediscoveryTargets.addLast(target);
                return true;
            }

            List<BlockPos> reordered = new ArrayList<>(pendingInsetRediscoveryTargets.size() + 1);
            boolean inserted = false;
            for (BlockPos queued : pendingInsetRediscoveryTargets) {
                if (!inserted && compareRediscoveryTargets(target, queued) < 0) {
                    reordered.add(target);
                    inserted = true;
                }
                reordered.add(queued);
            }
            if (!inserted) reordered.add(target);

            pendingInsetRediscoveryTargets.clear();
            pendingInsetRediscoveryTargets.addAll(reordered);
            return true;
        }
        return false;
    }

    private int compareRediscoveryTargets(BlockPos left, BlockPos right) {
        int byY = Integer.compare(right.getY(), left.getY());
        if (byY != 0) return byY;
        int byX = Integer.compare(left.getX(), right.getX());
        if (byX != 0) return byX;
        return Integer.compare(left.getZ(), right.getZ());
    }

    private void clearRediscoveryState() {
        pendingRediscoveryTargets.clear();
        pendingRediscoveryTargetSet.clear();
        pendingInsetRediscoveryTargets.clear();
        pendingInsetRediscoveryTargetSet.clear();
        rediscoveryLayerY = Integer.MIN_VALUE;
        nextRediscoveryScanTick = 0L;
        drainRediscoveryQueue = false;
        rediscoveryFullRescanPending = false;
        finalRediscoverySweepDone = false;
    }

    private void runFinalRediscoveryScan(ServerWorld sw) {
        if (startTopY == Integer.MIN_VALUE) return;

        int top = maxY;
        int bottom = sw.getBottomY();
        scanRediscoveryRange(sw, top, bottom);
    }

    private void scanRediscoveryRange(ServerWorld sw, int topYInclusive, int bottomYInclusive) {
        for (int y = topYInclusive; y >= bottomYInclusive; y--) {
            scanInsetFacesOnLayer(sw, y);
            scanRediscoveryLayer(sw, y);
        }
    }

    private boolean scanRediscoveryLayer(ServerWorld sw, int y) {
        boolean detected = false;
        boolean atOrAboveFrameFloor = y >= minY;
        int scanMinX = atOrAboveFrameFloor ? minX : minX + 1;
        int scanMaxX = atOrAboveFrameFloor ? maxX : maxX - 1;
        int scanMinZ = atOrAboveFrameFloor ? minZ : minZ + 1;
        int scanMaxZ = atOrAboveFrameFloor ? maxZ : maxZ - 1;
        Set<Long> frameLookup = atOrAboveFrameFloor ? getCachedFrameLookup() : null;

        for (int z = scanMinZ; z <= scanMaxZ; z++) {
            for (int x = scanMinX; x <= scanMaxX; x++) {
                BlockPos target = new BlockPos(x, y, z);
                if (frameLookup != null && frameLookup.contains(target.asLong())) continue;
                BlockState state = sw.getBlockState(target);
                if (!isMineableTarget(sw, target, state)) continue;
                if (enqueueRediscoveryCandidate(target)) detected = true;
            }
        }
        return detected;
    }

    private boolean scanInsetFacesOnLayer(ServerWorld sw, int y) {
        if (!areaLocked) return false;
        if (y < minY) return false; // Below the lowest frame layer uses gantry-area scanning only.
        if (y > maxY - 1) return false;

        boolean detected = false;
        int innerMinX = minX + 1;
        int innerMaxX = maxX - 1;
        int innerMinZ = minZ + 1;
        int innerMaxZ = maxZ - 1;

        if (y == maxY - 1) {
            for (int z = innerMinZ; z <= innerMaxZ; z++) {
                for (int x = innerMinX; x <= innerMaxX; x++) {
                    BlockPos target = new BlockPos(x, y, z);
                    BlockState state = sw.getBlockState(target);
                    if (!isMineableTarget(sw, target, state)) continue;
                    if (enqueueInsetRediscoveryTarget(target)) detected = true;
                }
            }
            return detected;
        }

        if (y == minY) return false; // bottom inset face is intentionally excluded

        for (int x = innerMinX; x <= innerMaxX; x++) {
            BlockPos north = new BlockPos(x, y, innerMinZ);
            BlockState northState = sw.getBlockState(north);
            if (isMineableTarget(sw, north, northState) && enqueueInsetRediscoveryTarget(north)) detected = true;

            if (innerMaxZ != innerMinZ) {
                BlockPos south = new BlockPos(x, y, innerMaxZ);
                BlockState southState = sw.getBlockState(south);
                if (isMineableTarget(sw, south, southState) && enqueueInsetRediscoveryTarget(south)) detected = true;
            }
        }
        for (int z = innerMinZ + 1; z <= innerMaxZ - 1; z++) {
            BlockPos west = new BlockPos(innerMinX, y, z);
            BlockState westState = sw.getBlockState(west);
            if (isMineableTarget(sw, west, westState) && enqueueInsetRediscoveryTarget(west)) detected = true;

            if (innerMaxX != innerMinX) {
                BlockPos east = new BlockPos(innerMaxX, y, z);
                BlockState eastState = sw.getBlockState(east);
                if (isMineableTarget(sw, east, eastState) && enqueueInsetRediscoveryTarget(east)) detected = true;
            }
        }
        return detected;
    }

    private Vec3d getToolHeadOriginPos() {
        if (!areaLocked) return Vec3d.ofCenter(pos);

        int originX = MathHelper.clamp(pos.getX(), minX + 1, maxX - 1);
        int originY = MathHelper.clamp(maxY - 3, minY + 1, maxY - 1);
        int originZ = MathHelper.clamp(pos.getZ(), minZ + 1, maxZ - 1);
        return Vec3d.ofCenter(new BlockPos(originX, originY, originZ));
    }

    private Vec3d getToolHeadPos() {
        if (Double.isNaN(toolHeadX) || Double.isNaN(toolHeadY) || Double.isNaN(toolHeadZ)) {
            Vec3d start = Vec3d.ofCenter(pos);
            toolHeadX = start.x;
            toolHeadY = start.y;
            toolHeadZ = start.z;
            clientTargetPacked = pos.asLong();
        }
        return new Vec3d(toolHeadX, toolHeadY, toolHeadZ);
    }

    private void setToolHeadPos(Vec3d target) {
        long packed = BlockPos.ofFloored(target).asLong();
        boolean changed = Double.compare(toolHeadX, target.x) != 0
                || Double.compare(toolHeadY, target.y) != 0
                || Double.compare(toolHeadZ, target.z) != 0
                || clientTargetPacked != packed;
        if (!changed) return;
        toolHeadX = target.x;
        toolHeadY = target.y;
        toolHeadZ = target.z;
        clientTargetPacked = packed;
        if (world instanceof ServerWorld sw) {
            markDirty();
            BlockState state = sw.getBlockState(pos);
            sw.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    private double moveToolHeadTowards(Vec3d target, double maxDistance) {
        Vec3d current = getToolHeadPos();
        Vec3d delta = target.subtract(current);
        double distance = delta.length();
        if (distance <= 1.0E-6) return 0.0;

        double traveled = Math.min(maxDistance, distance);
        Vec3d next = current.add(delta.multiply(traveled / distance));
        setToolHeadPos(next);
        return traveled;
    }

    @Nullable
    private WorkTarget getActiveMiningTarget(ServerWorld sw) {
        if (activeMiningTargetPacked == 0L || activeMiningTargetType < 0 || activeMiningTargetType >= WorkType.values().length) {
            return null;
        }

        WorkType type = WorkType.values()[activeMiningTargetType];
        BlockPos targetPos = BlockPos.fromLong(activeMiningTargetPacked);
        if (type == WorkType.REDISCOVERY_INSET) {
            if (!targetPos.equals(peekInsetRediscoveryTarget(sw))) {
                clearActiveMiningTarget();
                return null;
            }
        } else if (type == WorkType.REDISCOVERY) {
            if (!targetPos.equals(peekRediscoveryTarget(sw))) {
                clearActiveMiningTarget();
                return null;
            }
        } else if (type == WorkType.FRAME_REMOVE) {
            if (!sw.getBlockState(targetPos).isOf(ModBlocks.FRAME)) {
                clearActiveMiningTarget();
                return null;
            }
        } else if (!isMineableTarget(sw, targetPos, sw.getBlockState(targetPos))) {
            clearActiveMiningTarget();
            return null;
        }
        return new WorkTarget(targetPos, type);
    }

    private void setActiveMiningTarget(WorkTarget target) {
        activeMiningTargetPacked = target.pos().asLong();
        activeMiningTargetType = target.type().ordinal();
    }

    private void clearActiveMiningTarget() {
        activeMiningTargetPacked = 0L;
        activeMiningTargetType = -1;
    }

    private void recordRecentRenderTarget(ServerWorld sw, BlockPos target) {
        renderChannelRecentTargetPacked = target.asLong();
        renderChannelRecentTargetUntilTick = sw.getTime() + 8L;
    }

    private void clearMiningTravelState() {
        clearActiveMiningTarget();
        returnPhase = ReturnPhase.NONE;
        suppressGantryDuringReturn = false;
        renderChannelRecentTargetPacked = 0L;
        renderChannelRecentTargetUntilTick = 0L;
        gantryEntryLayerY = Integer.MIN_VALUE;
        gantryEntryDeferredDropPending = false;
        frameRemovalActive = false;
        frameRemovalIndex = -1;
        Vec3d start = getToolHeadOriginPos();
        toolHeadX = start.x;
        toolHeadY = start.y;
        toolHeadZ = start.z;
        clientTargetPacked = BlockPos.ofFloored(start).asLong();
    }

    private void beginReturnToOrigin(ServerWorld sw, ReturnPhase phase) {
        if (returnPhase != ReturnPhase.NONE) return;

        boolean frameWorkPending = frameIndex < cachedFrameSize(sw)
                || !pendingFrameRepairs.isEmpty();
        boolean topLaserPhase = !frameWorkPending && shouldUseTopLaserClient();

        clearActiveMiningTarget();
        returnPhase = phase;
        suppressGantryDuringReturn = frameWorkPending || topLaserPhase;
        active = false;
        frameWorkBudget = 0.0;
        miningBudget = Math.min(miningBudget, 1.0);

        BlockState state = sw.getBlockState(pos);
        if (state.getBlock() instanceof BlockWithEntity && state.contains(QuarryBlock.ACTIVE) && state.get(QuarryBlock.ACTIVE)) {
            sw.setBlockState(pos, state.with(QuarryBlock.ACTIVE, false), Block.NOTIFY_ALL);
        } else {
            sw.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        }
        markDirty();
    }

    private void stepReturnToOrigin(ServerWorld sw) {
        Vec3d origin = getToolHeadOriginPos();
        double distance = getToolHeadPos().distanceTo(origin);
        if (distance > 1.0E-6 && miningBudget > 1.0E-6) {
            double moved = moveToolHeadTowards(origin, miningBudget);
            miningBudget -= moved;
            distance = getToolHeadPos().distanceTo(origin);
        }

        if (distance > 1.0E-6) return;

        setToolHeadPos(origin);
        miningBudget = 0.0;
        ReturnPhase completedPhase = returnPhase;
        returnPhase = ReturnPhase.NONE;
        suppressGantryDuringReturn = false;
        clearActiveMiningTarget();

        if (completedPhase == ReturnPhase.FINISHING) {
            stopAndRemoveFrame();
            return;
        }

        clearStatusMessage();
        ChunkTickets.clearTickets(sw, pos, this);
        updateStatusState(sw);
    }

    private void normalizeFrameProgress(ServerWorld sw) {
        if (cachedFrame == null) cachedFrame = computeFrame();
        while (!pendingFrameRepairs.isEmpty() && sw.getBlockState(pendingFrameRepairs.peekFirst()).isOf(ModBlocks.FRAME)) {
            pendingFrameRepairs.removeFirst();
        }
        while (frameIndex < cachedFrame.size() && sw.getBlockState(cachedFrame.get(frameIndex)).isOf(ModBlocks.FRAME)) {
            frameIndex++;
        }
    }

    private long getRequiredEnergyForNextOperation(ServerWorld sw) {
        if (returnPhase != ReturnPhase.NONE) return 0L;
        if (frameRemovalActive) return ModConfig.DATA.energyPerFrame;
        if (!active || !areaLocked) return 0L;
        if (cachedFrame == null) cachedFrame = computeFrame();
        if (frameIndex < cachedFrame.size() || !pendingFrameRepairs.isEmpty()) return ModConfig.DATA.energyPerFrame;

        BlockPos rediscoveredTarget = null;
        if (drainRediscoveryQueue) {
            rediscoveredTarget = peekInsetRediscoveryTarget(sw);
            if (rediscoveredTarget == null) {
                rediscoveredTarget = peekRediscoveryTarget(sw);
            }
        }
        if (rediscoveredTarget != null) {
            BlockState nextState = sw.getBlockState(rediscoveredTarget);
            float hardness = nextState.getHardness(sw, rediscoveredTarget);
            return ModConfig.DATA.energyPerBlock + (long) (Math.max(0.0f, hardness) * ModConfig.DATA.hardnessEnergyScale);
        }

        BlockPos nextTarget = getNextMineableTarget(sw);
        if (nextTarget == null) return 0L;

        BlockState nextState = sw.getBlockState(nextTarget);
        float hardness = nextState.getHardness(sw, nextTarget);
        return ModConfig.DATA.energyPerBlock + (long) (Math.max(0.0f, hardness) * ModConfig.DATA.hardnessEnergyScale);
    }

    @Nullable
    private BlockPos getNextMineableTarget(ServerWorld sw) {
        if (!hasMineableInterior()) return null;

        int peekLayerY = layerY;
        int peekCursorX = cursorX;
        int peekCursorZ = cursorZ;
        boolean peekXForward = xForward;
        boolean peekZForward = zForward;

        if (peekLayerY == Integer.MIN_VALUE) {
            int topY = findHighestMineableY(sw);
            if (topY == Integer.MIN_VALUE) return null;
            peekLayerY = topY;
            peekCursorX = minX + 1;
            peekCursorZ = minZ + 1;
            peekXForward = true;
            peekZForward = true;
        }

        int bottom = sw.getBottomY();
        while (peekLayerY >= bottom) {
            BlockPos target = new BlockPos(peekCursorX, peekLayerY, peekCursorZ);
            if (isMineableTarget(sw, target, sw.getBlockState(target))) return target;
            CursorState advanced = advanceCursor(peekCursorX, peekLayerY, peekCursorZ, peekXForward, peekZForward);
            peekCursorX = advanced.x;
            peekLayerY = advanced.y;
            peekCursorZ = advanced.z;
            peekXForward = advanced.xForward;
            peekZForward = advanced.zForward;
        }

        return null;
    }

    private int getProgressScaled1000() {
        if (startTopY == Integer.MIN_VALUE || layerY == Integer.MIN_VALUE) return 0;
        int bottom = (world instanceof ServerWorld sw) ? sw.getBottomY() : -64;
        int total = Math.max(1, startTopY - bottom);
        int done = MathHelper.clamp(startTopY - layerY, 0, total);
        return (int) ((done * 1000L) / total);
    }

    private int getSpeedCount() {
        int speed = 0;
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack s = upgrades.getStack(i);
            if (!s.isEmpty() && s.isOf(ModItems.SPEED_UPGRADE)) speed += s.getCount();
        }
        return speed;
    }

    private int getMiningCycleTicks() {
        int speed = Math.min(4, getSpeedCount());
        return switch (speed) {
            case 1, 2, 3 -> 5;
            case 4 -> 1;
            default -> 10;
        };
    }

    private int getBlocksPerMiningCycle() {
        int speed = Math.min(4, getSpeedCount());
        return switch (speed) {
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 2;
            default -> 1;
        };
    }

    private double getBlocksPerSecond() {
        return 20.0 * getBlocksPerMiningCycle() / getMiningCycleTicks();
    }

    private double getBlocksPerTick() {
        return getBlocksPerSecond() / 20.0;
    }

    public void stopAndRemoveFrame() {
        if (!(world instanceof ServerWorld sw)) return;
        updateStatusState(sw);
        if (insufficientEnergyForNextOp) return;

        setActive(false);
        ChunkTickets.clearTickets(sw, pos, this);
        if (cachedFrame == null) cachedFrame = computeFrame();
        for (BlockPos framePos : cachedFrame) {
            if (sw.getBlockState(framePos).isOf(ModBlocks.FRAME)) {
                sw.breakBlock(framePos, false);
            }
        }

        frameClearanceIndex = 0;
        cachedFrameClearance = null;
        frameIndex = 0;
        cachedFrame = null;
        cachedFrameRemoval = null;
        cachedFrameLookup = null;
        pendingFrameRepairs.clear();
        lastFrameCheckLayerY = Integer.MIN_VALUE;
        nextFrameIntegrityCheckTick = 0L;
        startTopY = Integer.MIN_VALUE;
        layerY = Integer.MIN_VALUE;
        clientTargetPacked = 0L;
        frameWorkBudget = 0.0;
        miningBudget = 0.0;
        clearRediscoveryState();
        clearMiningTravelState();
        areaLocked = false;
        finished = true;
        clearStatusMessage();
        updateStatusState(sw);
        markDirty();
    }

    private void expireStatusMessage(ServerWorld sw) {
        if (statusMessageUntilTick >= 0L && sw.getTime() >= statusMessageUntilTick) {
            clearStatusMessage();
            syncState();
        }
    }

    private void setStatusMessage(String message, int color, long durationTicks) {
        statusMessage = message;
        statusMessageColor = color;
        statusMessageUntilTick = durationTicks < 0 ? -1L : ((world instanceof ServerWorld sw) ? sw.getTime() + durationTicks : durationTicks);
    }

    private void clearStatusMessage() {
        statusMessage = "";
        statusMessageColor = 0xFF9AA3B2;
        statusMessageUntilTick = -1L;
    }

    private void updateStatusState(ServerWorld sw) {
        requiredEnergyForNextOp = getRequiredEnergyForNextOperation(sw);
        insufficientEnergyForNextOp = (active || frameRemovalActive) && requiredEnergyForNextOp > 0L && energy.amount < requiredEnergyForNextOp;

        if (finished) {
            statusState = StatusState.FINISHED;
        } else if (insufficientEnergyForNextOp) {
            statusState = StatusState.NO_POWER;
        } else if (frameRemovalActive) {
            statusState = StatusState.ACTIVE;
        } else if (active && (frameIndex < cachedFrameSize(sw) || !pendingFrameRepairs.isEmpty())) {
            statusState = StatusState.REPAIRING;
        } else if (active) {
            statusState = StatusState.ACTIVE;
        } else {
            statusState = StatusState.IDLE;
        }
        syncState();
    }

    private boolean isInitialFrameBuildInProgress() {
        return active
                && pendingFrameRepairs.isEmpty()
                && (cachedFrame == null || frameIndex < cachedFrame.size());
    }

    private RenderChannelPhase determineRenderChannelPhase() {
        if (debugForcedRenderPhase != null) return debugForcedRenderPhase;
        if (debugVisualPreview && canShowDebugPreview()) return RenderChannelPhase.DEBUG_PREVIEW;
        if (!areaLocked) return RenderChannelPhase.NONE;
        if (isFrameWorkActiveClient()) return RenderChannelPhase.FRAME_WORK;
        if (shouldUseTopLaserClient()) return RenderChannelPhase.TOP_LASER;
        if (suppressGantryDuringReturn) return RenderChannelPhase.SUPPRESSED_RETURN;
        if (active || returnPhase != ReturnPhase.NONE || frameRemovalActive || shouldRenderPausedGantryClient()) {
            return RenderChannelPhase.GANTRY;
        }
        return RenderChannelPhase.NONE;
    }

    private void updateRenderChannelState(ServerWorld sw) {
        RenderChannelPhase nextPhase = determineRenderChannelPhase();
        long now = sw.getTime();
        if (nextPhase != renderChannelPhase) {
            renderChannelPhase = nextPhase;
            renderChannelPhaseStartedTick = now;
        }
        renderChannelStateTick = now;

        BlockPos currentWaypoint = BlockPos.ofFloored(getToolHeadPos());
        BlockPos nextWaypoint = getCurrentTargetPosOrNull();
        renderChannelWaypointCurrentPacked = currentWaypoint.asLong();
        renderChannelWaypointNextPacked = nextWaypoint == null ? 0L : nextWaypoint.asLong();
        if (activeMiningTargetPacked != 0L) {
            renderChannelActiveTargetPacked = activeMiningTargetPacked;
        } else if (renderChannelRecentTargetPacked != 0L && now <= renderChannelRecentTargetUntilTick) {
            renderChannelActiveTargetPacked = renderChannelRecentTargetPacked;
        } else {
            renderChannelActiveTargetPacked = 0L;
        }
    }

    private void syncState() {
        if (world instanceof ServerWorld sw) {
            updateRenderChannelState(sw);
            markDirty();
            BlockState state = sw.getBlockState(pos);
            sw.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        }
    }

    private boolean hasVoidUpgrade() {
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack s = upgrades.getStack(i);
            if (!s.isEmpty() && s.isOf(ModItems.VOID_UPGRADE)) return true;
        }
        return false;
    }

    private boolean hasChunkloadUpgrade() {
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack s = upgrades.getStack(i);
            if (!s.isEmpty() && s.isOf(ModItems.CHUNKLOAD_UPGRADE)) return true;
        }
        return false;
    }

    private ItemStack makeToolForDrops() {
        int fortune = 0;
        boolean silk = false;
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack s = upgrades.getStack(i);
            if (s.isEmpty()) continue;
            if (s.isOf(ModItems.SILK_UPGRADE)) silk = true;
            else if (s.isOf(ModItems.FORTUNE_UPGRADE_1)) fortune = Math.max(fortune, 1);
            else if (s.isOf(ModItems.FORTUNE_UPGRADE_2)) fortune = Math.max(fortune, 2);
            else if (s.isOf(ModItems.FORTUNE_UPGRADE_3)) fortune = Math.max(fortune, 3);
        }

        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (silk) {
            tool.addEnchantment(Enchantments.SILK_TOUCH, 1);
        } else if (fortune > 0) {
            tool.addEnchantment(Enchantments.FORTUNE, fortune);
        }
        return tool;
    }

    public boolean isValidUpgrade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isOf(ModItems.SPEED_UPGRADE)
                || stack.isOf(ModItems.FORTUNE_UPGRADE_1)
                || stack.isOf(ModItems.FORTUNE_UPGRADE_2)
                || stack.isOf(ModItems.FORTUNE_UPGRADE_3)
                || stack.isOf(ModItems.SILK_UPGRADE)
                || stack.isOf(ModItems.CHUNKLOAD_UPGRADE)
                || stack.isOf(ModItems.VOID_UPGRADE);
    }

    public boolean canInsertUpgrade(ItemStack stack) {
        if (!isValidUpgrade(stack)) return false;
        return !stack.isOf(ModItems.SPEED_UPGRADE) || getSpeedCount() < MAX_SPEED_UPGRADES;
    }

    public int getUpgradeMaxCount(ItemStack stack) {
        return 1;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("gui.quarry_reforged.quarry.title");
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        if (owner == null && player != null) owner = player.getUuid();
        return new com.errorsys.quarry_reforged.screen.QuarryScreenHandler(syncId, playerInventory, this);
    }

    public NbtCompound createItemStateNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");
        nbt.remove("id");
        return nbt;
    }

    public void applyItemStateNbt(NbtCompound nbt) {
        if (nbt == null) return;
        NbtCompound copy = nbt.copy();
        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());
        readNbt(copy);
        markDirty();
        if (world instanceof ServerWorld sw) {
            BlockState state = sw.getBlockState(pos);
            sw.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        }
    }

    // Client render helper
    public boolean isActiveClient() { return active; }
    public boolean isNoPowerClient() { return statusState == StatusState.NO_POWER; }
    public boolean shouldForceHomeGantryClient() {
        return statusState == StatusState.NO_POWER
                || returnPhase != ReturnPhase.NONE
                || (!active && !frameRemovalActive);
    }

    public int getFrameTopY() { return maxY; }

    public String getDisplayStatus() {
        String key = switch (statusState) {
            case ACTIVE -> "gui.quarry_reforged.status.active";
            case NO_POWER -> "gui.quarry_reforged.status.no_power";
            case REPAIRING -> "gui.quarry_reforged.status.repairing";
            case FINISHED -> "gui.quarry_reforged.status.finished";
            default -> "gui.quarry_reforged.status.idle";
        };
        return Text.translatable(key).getString();
    }

    public String getDisplayStatusMessage() {
        if (statusState == StatusState.NO_POWER) {
            return Text.translatable("gui.quarry_reforged.status.message.no_power", energy.amount, requiredEnergyForNextOp).getString();
        }
        if (isPausedByRedstoneClient()) {
            return Text.translatable("gui.quarry_reforged.status.message.redstone_paused").getString();
        }
        if (!statusMessage.isEmpty()) return Text.translatable(statusMessage).getString();
        if (returnPhase == ReturnPhase.STOPPING) {
            return Text.translatable("gui.quarry_reforged.status.message.stopping").getString();
        }
        if (returnPhase == ReturnPhase.FINISHING) {
            return Text.translatable("gui.quarry_reforged.status.message.returning_finished").getString();
        }
        if (frameRemovalActive) {
            return Text.translatable("gui.quarry_reforged.status.message.removing_frame").getString();
        }
        return switch (statusState) {
            case REPAIRING -> Text.translatable(
                    isInitialFrameBuildInProgress()
                            ? "gui.quarry_reforged.status.message.building"
                            : "gui.quarry_reforged.status.message.repairing"
            ).getString();
            case ACTIVE -> Text.translatable("gui.quarry_reforged.status.message.active", String.format(java.util.Locale.ROOT, "%.2f", getBlocksPerSecond())).getString();
            case FINISHED -> Text.translatable("gui.quarry_reforged.status.message.finished").getString();
            default -> Text.translatable("gui.quarry_reforged.status.message.idle").getString();
        };
    }

    public int getDisplayStatusColor() {
        if (statusState == StatusState.NO_POWER) return 0xFFE35D5D;
        if (!statusMessage.isEmpty()) return statusMessageColor;
        if (returnPhase == ReturnPhase.STOPPING) return 0xFFE09A3F;
        if (returnPhase == ReturnPhase.FINISHING) return 0xFF6ACB77;
        return switch (statusState) {
            case REPAIRING -> 0xFFE09A3F;
            case ACTIVE -> 0xFF9AA3B2;
            case FINISHED -> 0xFF6ACB77;
            default -> 0xFF9AA3B2;
        };
    }

    private boolean isPausedByRedstoneClient() {
        if (!(active || frameRemovalActive || returnPhase != ReturnPhase.NONE)) return false;
        if (world == null) return false;
        return !isRedstoneConditionMet(world);
    }

    private boolean isRedstoneConditionMetForControlClient() {
        if (redstoneMode == MachineRedstoneMode.IGNORED) return true;
        if (world == null) return true;
        return isRedstoneConditionMet(world);
    }

    public boolean canToggleActiveClient() {
        if (returnPhase != ReturnPhase.NONE) return false;
        if (active || frameRemovalActive) return true;
        return isRedstoneConditionMetForControlClient();
    }

    public boolean canRemoveFrameClient() {
        return canStartFrameRemovalClient() && isRedstoneConditionMetForControlClient();
    }

    public boolean isFrameRemovalActiveClient() {
        return frameRemovalActive;
    }

    public boolean isDebugInterpolationEnabledClient() {
        return debugInterpolationEnabled;
    }

    public long getDebugAnimationTickClient(long worldTick) {
        return debugFreezeAnimation ? debugFrozenAnimationTick : worldTick;
    }

    public boolean setDebugVisualPreview(boolean enabled) {
        if (debugVisualPreview == enabled) return true;
        if (enabled && !canShowDebugPreview()) {
            return false;
        }

        debugVisualPreview = enabled;
        if (enabled) {
            setToolHeadPos(getToolHeadOriginPos());
        }
        syncState();
        return true;
    }

    public void setDebugForcedRenderPhase(@Nullable RenderChannelPhase forcedPhase) {
        debugForcedRenderPhase = forcedPhase;
        syncState();
    }

    public void setDebugFreezeAnimation(ServerWorld sw, boolean freeze) {
        if (freeze && !debugFreezeAnimation) {
            debugFrozenAnimationTick = sw.getTime();
        } else if (!freeze && debugFreezeAnimation) {
            debugFrozenAnimationTick = 0L;
        }
        debugFreezeAnimation = freeze;
        syncState();
    }

    public void setDebugInterpolationEnabled(boolean enabled) {
        debugInterpolationEnabled = enabled;
        syncState();
    }

    public RediscoveryDebugSnapshot getRediscoveryDebugSnapshot(ServerWorld sw) {
        BlockPos activeTargetPos = activeMiningTargetPacked == 0L ? null : BlockPos.fromLong(activeMiningTargetPacked);
        String activeTargetType = null;
        if (activeTargetPos != null && activeMiningTargetType >= 0 && activeMiningTargetType < WorkType.values().length) {
            activeTargetType = WorkType.values()[activeMiningTargetType].name();
        }

        BlockPos gantryQueueHead = firstValidQueuedTarget(sw, pendingRediscoveryTargets);
        BlockPos laserQueueHead = firstValidQueuedTarget(sw, pendingInsetRediscoveryTargets);
        int gantryQueueValid = countValidQueuedTargets(sw, pendingRediscoveryTargets);
        int laserQueueValid = countValidQueuedTargets(sw, pendingInsetRediscoveryTargets);

        return new RediscoveryDebugSnapshot(
                active,
                areaLocked,
                drainRediscoveryQueue,
                rediscoveryFullRescanPending,
                finalRediscoverySweepDone,
                rediscoveryLayerY,
                nextRediscoveryScanTick,
                pendingRediscoveryTargets.size(),
                gantryQueueValid,
                gantryQueueHead,
                pendingInsetRediscoveryTargets.size(),
                laserQueueValid,
                laserQueueHead,
                activeTargetPos,
                activeTargetType
        );
    }

    @Nullable
    private BlockPos firstValidQueuedTarget(ServerWorld sw, ArrayDeque<BlockPos> queue) {
        for (BlockPos candidate : queue) {
            if (isMineableTarget(sw, candidate, sw.getBlockState(candidate))) return candidate;
        }
        return null;
    }

    private int countValidQueuedTargets(ServerWorld sw, ArrayDeque<BlockPos> queue) {
        int count = 0;
        for (BlockPos candidate : queue) {
            if (isMineableTarget(sw, candidate, sw.getBlockState(candidate))) count++;
        }
        return count;
    }

    private void clearDebugVisualPreview() {
        debugVisualPreview = false;
    }

    private boolean canShowDebugPreview() {
        return areaLocked
                && !active
                && !frameRemovalActive
                && returnPhase == ReturnPhase.NONE
                && statusState == StatusState.IDLE;
    }

    public boolean shouldRenderPausedGantryClient() {
        if (!areaLocked) return false;
        if (active || frameRemovalActive || returnPhase != ReturnPhase.NONE) return false;
        if (finished) return false;
        if (hasPendingFrameWorkClient()) return false;
        if (isFrameWorkActiveClient()) return false;
        if (shouldUseTopLaserClient()) return false;
        // Paused gantry only applies once mining has entered gantry phase (below top-laser range).
        return layerY != Integer.MIN_VALUE && layerY < minY;
    }

    private boolean hasPendingFrameWorkClient() {
        if (!areaLocked) return false;
        int frameSize = cachedFrame == null ? computeFrame().size() : cachedFrame.size();
        return frameIndex < frameSize
                || !pendingFrameRepairs.isEmpty();
    }

    public RenderChannelPhase getRenderChannelPhaseClient() {
        return renderChannelPhase;
    }

    public long getRenderChannelPhaseStartedTickClient() {
        return renderChannelPhaseStartedTick;
    }

    public long getRenderChannelStateTickClient() {
        return renderChannelStateTick;
    }

    @Nullable
    public BlockPos getRenderChannelWaypointCurrentClient() {
        if (renderChannelWaypointCurrentPacked == 0L) return null;
        return BlockPos.fromLong(renderChannelWaypointCurrentPacked);
    }

    @Nullable
    public BlockPos getRenderChannelWaypointNextClient() {
        if (renderChannelWaypointNextPacked == 0L) return null;
        return BlockPos.fromLong(renderChannelWaypointNextPacked);
    }

    @Nullable
    public BlockPos getRenderChannelActiveTargetClient() {
        if (renderChannelActiveTargetPacked == 0L) return null;
        return BlockPos.fromLong(renderChannelActiveTargetPacked);
    }

    public int getProgressLayerCurrent() {
        int total = getProgressLayerTotal();
        if (total <= 0) return 0;
        if (finished) return total;
        if (layerY == Integer.MIN_VALUE) return 0;

        int referenceY = minY - 1;
        return MathHelper.clamp(referenceY - layerY, 0, total);
    }

    public int getProgressLayerTotal() {
        if (!areaLocked) return 0;
        int bottom = world != null ? world.getBottomY() : -64;
        int referenceY = minY - 1;
        return Math.max(0, referenceY - bottom + 1);
    }

    public int getProgressPercentDisplay() {
        int total = getProgressLayerTotal();
        if (total <= 0) return 0;
        return MathHelper.clamp(Math.round(getProgressLayerCurrent() * 100.0f / total), 0, 100);
    }

    public Vec3d getClientToolHeadPos() {
        if (Double.isNaN(toolHeadX) || Double.isNaN(toolHeadY) || Double.isNaN(toolHeadZ)) {
            return Vec3d.ofCenter(pos);
        }
        return new Vec3d(toolHeadX, toolHeadY, toolHeadZ);
    }

    public Vec3d getClientToolHeadOriginPos() {
        return getToolHeadOriginPos();
    }

    public boolean hasLockedAreaClient() {
        return areaLocked;
    }

    public int getInnerMinX() {
        return minX + 1;
    }

    public int getInnerMaxX() {
        return maxX - 1;
    }

    public int getInnerMinY() {
        return minY + 1;
    }

    public int getInnerMaxY() {
        return maxY - 1;
    }

    public int getInnerMinZ() {
        return minZ + 1;
    }

    public int getInnerMaxZ() {
        return maxZ - 1;
    }

    public boolean shouldUseTopLaserClient() {
        if (!areaLocked) return false;
        if (frameRemovalActive) return true;
        if (!active) return false;
        if (activeMiningTargetType == WorkType.REDISCOVERY_INSET.ordinal()) {
            return true;
        }
        if (drainRediscoveryQueue && !pendingInsetRediscoveryTargets.isEmpty()) {
            return true;
        }
        return layerY != Integer.MIN_VALUE && layerY >= minY;
    }

    public boolean isFrameWorkActiveClient() {
        return areaLocked && statusState == StatusState.REPAIRING;
    }

    public List<BlockPos> getClientUpcomingFrameTargets(int count) {
        if (!areaLocked || count <= 0) return List.of();
        if (cachedFrame == null) cachedFrame = computeFrame();
        if (frameIndex < 0 || frameIndex >= cachedFrame.size()) return List.of();

        int end = Math.min(cachedFrame.size(), frameIndex + count);
        return new ArrayList<>(cachedFrame.subList(frameIndex, end));
    }

    public boolean isClientFramePlanned(BlockPos pos) {
        if (!areaLocked) return false;
        return getCachedFrameLookup().contains(pos.asLong());
    }

    public double getBlocksPerSecondClient() {
        return getBlocksPerSecond();
    }

    private boolean canStartFrameRemovalClient() {
        return areaLocked
                && !active
                && !frameRemovalActive
                && returnPhase == ReturnPhase.NONE
                && statusState == StatusState.IDLE;
    }

    private boolean canStartFrameRemoval(ServerWorld sw) {
        if (!canStartFrameRemovalClient()) return false;
        if (cachedFrame == null) cachedFrame = computeFrame();
        for (BlockPos framePos : cachedFrame) {
            if (sw.getBlockState(framePos).isOf(ModBlocks.FRAME)) return true;
        }
        return false;
    }

    @Nullable
    private BlockPos getCurrentTargetPosOrNull() {
        if (!active && !frameRemovalActive && returnPhase == ReturnPhase.NONE) {
            return null;
        }
        if (returnPhase != ReturnPhase.NONE) {
            return BlockPos.ofFloored(getToolHeadPos());
        }
        if (activeMiningTargetPacked != 0L) {
            return BlockPos.fromLong(activeMiningTargetPacked);
        }
        if (layerY == Integer.MIN_VALUE) return null;
        return new BlockPos(cursorX, layerY, cursorZ);
    }

    private int[] encodeModes(MachineIoMode[] modes) {
        int[] encoded = new int[modes.length];
        for (int i = 0; i < modes.length; i++) {
            encoded[i] = modes[i].ordinal();
        }
        return encoded;
    }

    private void decodeModes(int[] encoded, MachineIoMode[] target, ItemIoGroup group) {
        MachineIoMode[] values = MachineIoMode.values();
        for (int i = 0; i < target.length; i++) {
            MachineIoMode modeValue = group.defaultMode();
            if (encoded != null && i < encoded.length) {
                int ord = MathHelper.clamp(encoded[i], 0, values.length - 1);
                MachineIoMode candidate = values[ord];
                if (group.supports(candidate)) {
                    modeValue = candidate;
                }
            }
            target[i] = modeValue;
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, output.items);
        nbt.put("Upgrades", upgrades.toNbt());
        nbt.put("EnergyItems", energyItems.toNbt());
        if (owner != null) nbt.putUuid("Owner", owner);

        nbt.putBoolean("Active", active);
        nbt.putInt("Mode", mode.ordinal());
        nbt.putString("RedstoneMode", redstoneMode.name());
        nbt.putBoolean("AutoExportEnabled", autoExportEnabled);
        nbt.putIntArray("UpgradeSideModes", encodeModes(upgradeSideModes));
        nbt.putIntArray("OutputSideModes", encodeModes(outputSideModes));

        nbt.putBoolean("AreaLocked", areaLocked);
        nbt.putInt("MinX", minX);
        nbt.putInt("MaxX", maxX);
        nbt.putInt("MinY", minY);
        nbt.putInt("MaxY", maxY);
        nbt.putInt("MinZ", minZ);
        nbt.putInt("MaxZ", maxZ);

        nbt.putInt("FrameClearanceIndex", frameClearanceIndex);
        nbt.putInt("FrameIndex", frameIndex);
        nbt.putBoolean("FrameRemovalActive", frameRemovalActive);
        nbt.putInt("FrameRemovalIndex", frameRemovalIndex);
        nbt.putInt("LastFrameCheckLayerY", lastFrameCheckLayerY);
        nbt.putLong("NextFrameIntegrityCheckTick", nextFrameIntegrityCheckTick);

        nbt.putInt("StartTopY", startTopY);
        nbt.putInt("LayerY", layerY);
        nbt.putInt("CursorX", cursorX);
        nbt.putInt("CursorZ", cursorZ);
        nbt.putBoolean("XForward", xForward);
        nbt.putBoolean("ZForward", zForward);
        nbt.putInt("GantryEntryLayerY", gantryEntryLayerY);
        nbt.putBoolean("GantryEntryDeferredDropPending", gantryEntryDeferredDropPending);
        nbt.putDouble("FrameWorkBudget", frameWorkBudget);
        nbt.putDouble("MiningBudget", miningBudget);
        nbt.putInt("RediscoveryLayerY", rediscoveryLayerY);
        nbt.putLong("NextRediscoveryScanTick", nextRediscoveryScanTick);
        nbt.putBoolean("DrainRediscoveryQueue", drainRediscoveryQueue);
        nbt.putLong("ActiveMiningTarget", activeMiningTargetPacked);
        nbt.putInt("ActiveMiningTargetType", activeMiningTargetType);
        nbt.putString("ReturnPhase", returnPhase.name());
        nbt.putBoolean("SuppressGantryDuringReturn", suppressGantryDuringReturn);
        nbt.putBoolean("FinalRediscoverySweepDone", finalRediscoverySweepDone);
        nbt.putBoolean("Finished", finished);
        nbt.putString("StatusState", statusState.name());
        nbt.putBoolean("InsufficientEnergyForNextOp", insufficientEnergyForNextOp);
        nbt.putLong("RequiredEnergyForNextOp", requiredEnergyForNextOp);
        nbt.putString("StatusMessage", statusMessage);
        nbt.putInt("StatusMessageColor", statusMessageColor);
        nbt.putLong("StatusMessageUntilTick", statusMessageUntilTick);
        nbt.putBoolean("DebugVisualPreview", debugVisualPreview);
        nbt.putBoolean("DebugHasForcedRenderPhase", debugForcedRenderPhase != null);
        if (debugForcedRenderPhase != null) {
            nbt.putString("DebugForcedRenderPhase", debugForcedRenderPhase.name());
        }
        nbt.putBoolean("DebugFreezeAnimation", debugFreezeAnimation);
        nbt.putLong("DebugFrozenAnimationTick", debugFrozenAnimationTick);
        nbt.putBoolean("DebugInterpolationEnabled", debugInterpolationEnabled);
        nbt.putString("RenderChannelPhase", renderChannelPhase.name());
        nbt.putLong("RenderChannelPhaseStartedTick", renderChannelPhaseStartedTick);
        nbt.putLong("RenderChannelStateTick", renderChannelStateTick);
        nbt.putLong("RenderChannelWaypointCurrent", renderChannelWaypointCurrentPacked);
        nbt.putLong("RenderChannelWaypointNext", renderChannelWaypointNextPacked);
        nbt.putLong("RenderChannelActiveTarget", renderChannelActiveTargetPacked);

        nbt.putLong("Energy", energy.amount);
        nbt.putLong("ClientTarget", clientTargetPacked);
        nbt.putDouble("ToolHeadX", toolHeadX);
        nbt.putDouble("ToolHeadY", toolHeadY);
        nbt.putDouble("ToolHeadZ", toolHeadZ);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        // Client inventories are authoritatively synced by ScreenHandler slot packets.
        // Applying block-entity NBT inventory snapshots client-side can race with slot sync
        // and produce temporary ghost stacks in open GUIs.
        boolean readInventories = world == null || !world.isClient;
        if (readInventories) {
            Inventories.readNbt(nbt, output.items);
            upgrades.readNbt(nbt.getCompound("Upgrades"));
            energyItems.readNbt(nbt.getCompound("EnergyItems"));
        }
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");

        active = nbt.getBoolean("Active");
        mode = Mode.values()[MathHelper.clamp(nbt.getInt("Mode"), 0, Mode.values().length - 1)];
        if (nbt.contains("RedstoneMode")) {
            try {
                redstoneMode = MachineRedstoneMode.valueOf(nbt.getString("RedstoneMode"));
            } catch (IllegalArgumentException ignored) {
                redstoneMode = MachineRedstoneMode.IGNORED;
            }
        } else {
            redstoneMode = MachineRedstoneMode.IGNORED;
        }
        autoExportEnabled = nbt.contains("AutoExportEnabled") ? nbt.getBoolean("AutoExportEnabled") : mode == Mode.EXPORT;
        mode = autoExportEnabled ? Mode.EXPORT : Mode.BUFFER;
        decodeModes(nbt.contains("UpgradeSideModes") ? nbt.getIntArray("UpgradeSideModes") : null, upgradeSideModes, ItemIoGroup.UPGRADES);
        decodeModes(nbt.contains("OutputSideModes") ? nbt.getIntArray("OutputSideModes") : null, outputSideModes, ItemIoGroup.OUTPUT);

        areaLocked = nbt.getBoolean("AreaLocked");
        minX = nbt.getInt("MinX");
        maxX = nbt.getInt("MaxX");
        minY = nbt.getInt("MinY");
        maxY = nbt.getInt("MaxY");
        minZ = nbt.getInt("MinZ");
        maxZ = nbt.getInt("MaxZ");

        frameClearanceIndex = nbt.getInt("FrameClearanceIndex");
        frameIndex = nbt.getInt("FrameIndex");
        frameRemovalActive = nbt.getBoolean("FrameRemovalActive");
        frameRemovalIndex = nbt.contains("FrameRemovalIndex") ? nbt.getInt("FrameRemovalIndex") : -1;
        lastFrameCheckLayerY = nbt.getInt("LastFrameCheckLayerY");
        nextFrameIntegrityCheckTick = nbt.getLong("NextFrameIntegrityCheckTick");

        startTopY = nbt.getInt("StartTopY");
        layerY = nbt.getInt("LayerY");
        cursorX = nbt.getInt("CursorX");
        cursorZ = nbt.getInt("CursorZ");
        xForward = nbt.getBoolean("XForward");
        zForward = !nbt.contains("ZForward") || nbt.getBoolean("ZForward");
        gantryEntryLayerY = nbt.contains("GantryEntryLayerY") ? nbt.getInt("GantryEntryLayerY") : Integer.MIN_VALUE;
        gantryEntryDeferredDropPending = nbt.getBoolean("GantryEntryDeferredDropPending");
        frameWorkBudget = nbt.contains("FrameWorkBudget") ? nbt.getDouble("FrameWorkBudget") : 0.0;
        miningBudget = nbt.contains("MiningBudget") ? nbt.getDouble("MiningBudget") : 0.0;
        rediscoveryLayerY = nbt.contains("RediscoveryLayerY") ? nbt.getInt("RediscoveryLayerY") : Integer.MIN_VALUE;
        nextRediscoveryScanTick = nbt.getLong("NextRediscoveryScanTick");
        drainRediscoveryQueue = nbt.getBoolean("DrainRediscoveryQueue");
        activeMiningTargetPacked = nbt.getLong("ActiveMiningTarget");
        activeMiningTargetType = nbt.contains("ActiveMiningTargetType") ? nbt.getInt("ActiveMiningTargetType") : -1;
        String storedReturnPhase = nbt.contains("ReturnPhase") ? nbt.getString("ReturnPhase") : ReturnPhase.NONE.name();
        returnPhase = ReturnPhase.valueOf(storedReturnPhase);
        suppressGantryDuringReturn = nbt.getBoolean("SuppressGantryDuringReturn");
        finalRediscoverySweepDone = nbt.getBoolean("FinalRediscoverySweepDone");
        finished = nbt.getBoolean("Finished");
        String storedStatusState = nbt.contains("StatusState") ? nbt.getString("StatusState") : StatusState.IDLE.name();
        statusState = StatusState.valueOf(storedStatusState);
        insufficientEnergyForNextOp = nbt.getBoolean("InsufficientEnergyForNextOp");
        requiredEnergyForNextOp = nbt.getLong("RequiredEnergyForNextOp");
        statusMessage = nbt.getString("StatusMessage");
        statusMessageColor = nbt.getInt("StatusMessageColor");
        statusMessageUntilTick = nbt.getLong("StatusMessageUntilTick");
        debugVisualPreview = nbt.getBoolean("DebugVisualPreview");
        if (nbt.getBoolean("DebugHasForcedRenderPhase") && nbt.contains("DebugForcedRenderPhase")) {
            try {
                debugForcedRenderPhase = RenderChannelPhase.valueOf(nbt.getString("DebugForcedRenderPhase"));
            } catch (IllegalArgumentException ignored) {
                debugForcedRenderPhase = null;
            }
        } else {
            debugForcedRenderPhase = null;
        }
        debugFreezeAnimation = nbt.getBoolean("DebugFreezeAnimation");
        debugFrozenAnimationTick = nbt.getLong("DebugFrozenAnimationTick");
        debugInterpolationEnabled = !nbt.contains("DebugInterpolationEnabled") || nbt.getBoolean("DebugInterpolationEnabled");
        String storedRenderChannelPhase = nbt.contains("RenderChannelPhase") ? nbt.getString("RenderChannelPhase") : RenderChannelPhase.NONE.name();
        try {
            renderChannelPhase = RenderChannelPhase.valueOf(storedRenderChannelPhase);
        } catch (IllegalArgumentException ignored) {
            renderChannelPhase = RenderChannelPhase.NONE;
        }
        renderChannelPhaseStartedTick = nbt.getLong("RenderChannelPhaseStartedTick");
        renderChannelStateTick = nbt.getLong("RenderChannelStateTick");
        renderChannelWaypointCurrentPacked = nbt.getLong("RenderChannelWaypointCurrent");
        renderChannelWaypointNextPacked = nbt.getLong("RenderChannelWaypointNext");
        renderChannelActiveTargetPacked = nbt.getLong("RenderChannelActiveTarget");

        energy.amount = nbt.getLong("Energy");
        clientTargetPacked = nbt.getLong("ClientTarget");
        toolHeadX = nbt.contains("ToolHeadX") ? nbt.getDouble("ToolHeadX") : Double.NaN;
        toolHeadY = nbt.contains("ToolHeadY") ? nbt.getDouble("ToolHeadY") : Double.NaN;
        toolHeadZ = nbt.contains("ToolHeadZ") ? nbt.getDouble("ToolHeadZ") : Double.NaN;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound n = new NbtCompound();
        writeNbt(n);
        return n;
    }

    public enum Mode { BUFFER, EXPORT }
    public enum StatusState { IDLE, ACTIVE, REPAIRING, NO_POWER, FINISHED }
    public enum RenderChannelPhase { NONE, DEBUG_PREVIEW, FRAME_WORK, TOP_LASER, SUPPRESSED_RETURN, GANTRY }
    public record RediscoveryDebugSnapshot(
            boolean active,
            boolean areaLocked,
            boolean drainRediscoveryQueue,
            boolean rediscoveryFullRescanPending,
            boolean finalRediscoverySweepDone,
            int rediscoveryLayerY,
            long nextRediscoveryScanTick,
            int gantryQueueRawSize,
            int gantryQueueValidSize,
            @Nullable BlockPos gantryQueueHead,
            int laserQueueRawSize,
            int laserQueueValidSize,
            @Nullable BlockPos laserQueueHead,
            @Nullable BlockPos activeTargetPos,
            @Nullable String activeTargetType
    ) {}
    private enum ReturnPhase { NONE, STOPPING, FINISHING }
    private enum WorkType { FRAME_PLACE, FRAME_REPAIR, FRAME_CLEARANCE, FRAME_REMOVE, MINE, REDISCOVERY, REDISCOVERY_INSET }

    private record EntityInventoryDrop(Entity entity, List<ItemStack> drops) {
        private void removeSource() {
            if (entity instanceof Inventory inventory) {
                for (int i = 0; i < inventory.size(); i++) inventory.setStack(i, ItemStack.EMPTY);
            }
            entity.discard();
        }
    }

    private record CursorState(int x, int y, int z, boolean xForward, boolean zForward) {}
    private record WorkTarget(BlockPos pos, WorkType type) {}

    private static final class SidedQuarryItemStorage implements Storage<ItemVariant> {
        private final QuarryBlockEntity quarry;
        @Nullable
        private final Direction worldSide;

        private SidedQuarryItemStorage(QuarryBlockEntity quarry, @Nullable Direction worldSide) {
            this.quarry = quarry;
            this.worldSide = worldSide;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0 || worldSide == null || quarry.world == null) return 0;
            if (!quarry.isAutomationEnabled(quarry.world)) return 0;
            if (!quarry.sideAllows(ItemIoGroup.UPGRADES, worldSide, false)) return 0;

            ItemStack single = resource.toStack();
            if (!quarry.canInsertUpgrade(single)) return 0;

            long inserted = 0;
            for (int i = 0; i < quarry.upgrades.size() && inserted < maxAmount; i++) {
                if (!quarry.canInsertUpgrade(single)) break;
                ItemStack slot = quarry.upgrades.getStack(i);
                int slotLimit = quarry.getUpgradeMaxCount(slot.isEmpty() ? single : slot);
                if (slot.isEmpty()) {
                    int move = (int) Math.min(maxAmount - inserted, slotLimit);
                    if (move <= 0) continue;
                    ItemStack place = resource.toStack(move);
                    quarry.upgrades.setStack(i, place);
                    inserted += move;
                    continue;
                }
                if (!ItemVariant.of(slot).equals(resource)) continue;
                int free = slotLimit - slot.getCount();
                if (free <= 0) continue;
                int move = (int) Math.min(maxAmount - inserted, free);
                slot.increment(move);
                inserted += move;
            }

            if (inserted > 0) quarry.markDirty();
            return inserted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0 || worldSide == null || quarry.world == null) return 0;
            if (!quarry.isAutomationEnabled(quarry.world)) return 0;

            long extracted = 0;
            if (quarry.sideAllows(ItemIoGroup.OUTPUT, worldSide, true)) {
                extracted += extractFromInventory(quarry.output, resource, maxAmount - extracted);
            }
            if (extracted < maxAmount && quarry.sideAllows(ItemIoGroup.UPGRADES, worldSide, true)) {
                extracted += extractFromInventory(quarry.upgrades, resource, maxAmount - extracted);
            }
            if (extracted > 0) quarry.markDirty();
            return extracted;
        }

        private long extractFromInventory(SimpleInventory inv, ItemVariant resource, long maxAmount) {
            long extracted = 0;
            for (int i = 0; i < inv.size() && extracted < maxAmount; i++) {
                ItemStack slot = inv.getStack(i);
                if (slot.isEmpty()) continue;
                if (!ItemVariant.of(slot).equals(resource)) continue;
                int take = (int) Math.min(maxAmount - extracted, slot.getCount());
                slot.decrement(take);
                extracted += take;
            }
            return extracted;
        }

        @Override
        public @NotNull Iterator<StorageView<ItemVariant>> iterator() {
            return Collections.emptyIterator();
        }
    }

    // Minimal internal inventory
    public static class SimpleInventory implements Inventory {
        final net.minecraft.util.collection.DefaultedList<ItemStack> items;
        private final Runnable dirtyCallback;

        public SimpleInventory(int size, Runnable dirtyCallback) {
            items = net.minecraft.util.collection.DefaultedList.ofSize(size, ItemStack.EMPTY);
            this.dirtyCallback = dirtyCallback == null ? () -> {} : dirtyCallback;
        }

        @Override public int size() { return items.size(); }
        @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
        @Override public ItemStack getStack(int slot) { return items.get(slot); }
        @Override public ItemStack removeStack(int slot, int amount) {
            ItemStack removed = Inventories.splitStack(items, slot, amount);
            if (!removed.isEmpty()) markDirty();
            return removed;
        }
        @Override public ItemStack removeStack(int slot) {
            ItemStack removed = Inventories.removeStack(items, slot);
            if (!removed.isEmpty()) markDirty();
            return removed;
        }
        @Override public void setStack(int slot, ItemStack stack) {
            items.set(slot, stack);
            markDirty();
        }
        @Override public void markDirty() { dirtyCallback.run(); }
        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
        @Override public void clear() {
            if (isEmpty()) return;
            items.clear();
            markDirty();
        }

        public NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            Inventories.writeNbt(n, items);
            return n;
        }

        public void readNbt(NbtCompound n) {
            Inventories.readNbt(n, items);
        }

        public boolean canFitAll(List<ItemStack> stacks) {
            net.minecraft.util.collection.DefaultedList<ItemStack> copy =
                    net.minecraft.util.collection.DefaultedList.ofSize(items.size(), ItemStack.EMPTY);
            for (int i = 0; i < items.size(); i++) copy.set(i, items.get(i).copy());
            for (ItemStack s : stacks) if (!simulateInsert(copy, s.copy())) return false;
            return true;
        }

        private boolean simulateInsert(net.minecraft.util.collection.DefaultedList<ItemStack> inv, ItemStack stack) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack slot = inv.get(i);
                if (slot.isEmpty()) { inv.set(i, stack); return true; }
                if (ItemStack.canCombine(slot, stack)) {
                    int move = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                    if (move > 0) {
                        slot.increment(move);
                        stack.decrement(move);
                        if (stack.isEmpty()) return true;
                    }
                }
            }
            return stack.isEmpty();
        }

        public void insertAll(ItemStack stack) {
            if (stack.isEmpty()) return;
            boolean changed = false;
            for (int i = 0; i < items.size(); i++) {
                ItemStack slot = items.get(i);
                if (slot.isEmpty()) {
                    items.set(i, stack.copy());
                    stack.setCount(0);
                    changed = true;
                    break;
                }
                if (ItemStack.canCombine(slot, stack)) {
                    int move = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                    if (move > 0) {
                        slot.increment(move);
                        stack.decrement(move);
                        changed = true;
                        if (stack.isEmpty()) break;
                    }
                }
            }
            if (changed) markDirty();
        }

        public int pushTo(Storage<ItemVariant> to, int opsBudget) {
            int ops = 0;
            boolean changed = false;
            try (Transaction tx = Transaction.openOuter()) {
                for (int i = 0; i < items.size() && ops < opsBudget; i++) {
                    ItemStack stack = items.get(i);
                    if (stack.isEmpty()) continue;

                    ItemVariant var = ItemVariant.of(stack);
                    long count = stack.getCount();
                    long inserted = to.insert(var, count, tx);
                    if (inserted > 0) {
                        stack.decrement((int) inserted);
                        ops++;
                        changed = true;
                    }
                }
                tx.commit();
            }
            if (changed) markDirty();
            return ops;
        }
    }
}
