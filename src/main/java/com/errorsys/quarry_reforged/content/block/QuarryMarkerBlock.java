package com.errorsys.quarry_reforged.content.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import com.errorsys.quarry_reforged.content.blockentity.QuarryMarkerBlockEntity;

public class QuarryMarkerBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.FACING;
    private static final VoxelShape UP_SHAPE = Block.createCuboidShape(7, 0, 7, 9, 10, 9);
    private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(7, 6, 7, 9, 16, 9);
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(7, 7, 6, 9, 9, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(7, 7, 0, 9, 9, 10);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(6, 7, 7, 16, 9, 9);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0, 7, 7, 10, 9, 9);

    public QuarryMarkerBlock() {
        super(AbstractBlock.Settings.copy(Blocks.TORCH).breakInstantly().nonOpaque().noCollision());
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.UP));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryMarkerBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (world instanceof ServerWorld sw) {
            if (world.getBlockEntity(pos) instanceof QuarryMarkerBlockEntity markerBe
                    && (markerBe.hasPreview() || markerBe.hasInvalidCardinalPreview())) {
                QuarryMarkerPreviewService.clearPreviewAt(sw, pos);
                return ActionResult.CONSUME;
            }
            QuarryMarkerPreviewService.tryActivatePreview(sw, pos);
            return ActionResult.CONSUME;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case DOWN -> DOWN_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> UP_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        return Block.sideCoversSmallSquare(world, supportPos, facing);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos placePos = ctx.getBlockPos();
        WorldView world = ctx.getWorld();
        for (Direction dir : ctx.getPlacementDirections()) {
            Direction facing = dir.getOpposite();
            BlockState state = getDefaultState().with(FACING, facing);
            if (canPlaceAt(state, world, placePos)) return state;
        }
        return null;
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state,
                                                Direction direction,
                                                BlockState neighborState,
                                                WorldAccess world,
                                                BlockPos pos,
                                                BlockPos neighborPos) {
        if (!canPlaceAt(state, world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) return;
        if (world instanceof ServerWorld sw) {
            QuarryMarkerPreviewService.onMarkerRemoved(sw, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
