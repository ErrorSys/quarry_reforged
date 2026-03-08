package com.errorsys.quarry_reforged.content.block;

import com.errorsys.quarry_reforged.content.ModBlocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class QuarryFrameBlock extends Block {
    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty WEST = Properties.WEST;
    public static final BooleanProperty UP = Properties.UP;
    public static final BooleanProperty DOWN = Properties.DOWN;
    public static final BooleanProperty THROW_PREVIEW = BooleanProperty.of("throw_preview");

    private static final VoxelShape CORE_SHAPE = Block.createCuboidShape(5, 5, 5, 11, 11, 11);
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(5, 5, 0, 11, 11, 5);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(5, 5, 11, 11, 11, 16);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(11, 5, 5, 16, 11, 11);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0, 5, 5, 5, 11, 11);
    private static final VoxelShape UP_SHAPE = Block.createCuboidShape(5, 11, 5, 11, 16, 11);
    private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(5, 0, 5, 11, 5, 11);
    private static final VoxelShape FLUID_BLOCKING_SHAPE = VoxelShapes.fullCube();

    public QuarryFrameBlock() {
        super(AbstractBlock.Settings.copy(Blocks.YELLOW_CONCRETE)
                .strength(0.1f, 500.0f)
                .dropsNothing()
                .nonOpaque()
                .suffocates((state, world, pos) -> false)
                .blockVision((state, world, pos) -> false));
        setDefaultState(getStateManager().getDefaultState()
                .with(NORTH, false)
                .with(SOUTH, false)
                .with(EAST, false)
                .with(WEST, false)
                .with(UP, false)
                .with(DOWN, false)
                .with(THROW_PREVIEW, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // Fluids evaluate collision using ShapeContext.absent(); treat the frame as a full block for flow.
        if (context == ShapeContext.absent()) {
            return FLUID_BLOCKING_SHAPE;
        }
        return getShape(state);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, THROW_PREVIEW);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getConnectedState(ctx.getWorld(), ctx.getBlockPos());
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return state.with(propertyFor(direction), connectsTo(neighborState, direction));
    }

    @Override
    public boolean canBucketPlace(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return Fluids.EMPTY.getDefaultState();
    }

    private BlockState getConnectedState(WorldAccess world, BlockPos pos) {
        return getDefaultState()
                .with(NORTH, connectsTo(world.getBlockState(pos.north()), Direction.NORTH))
                .with(SOUTH, connectsTo(world.getBlockState(pos.south()), Direction.SOUTH))
                .with(EAST, connectsTo(world.getBlockState(pos.east()), Direction.EAST))
                .with(WEST, connectsTo(world.getBlockState(pos.west()), Direction.WEST))
                .with(UP, connectsTo(world.getBlockState(pos.up()), Direction.UP))
                .with(DOWN, connectsTo(world.getBlockState(pos.down()), Direction.DOWN));
    }

    private boolean connectsTo(BlockState state, Direction directionToNeighbor) {
        if (state.isOf(this)) return true;
        // Treat the quarry rear face as a valid frame endpoint.
        if (state.isOf(ModBlocks.QUARRY) && state.contains(QuarryBlock.FACING)) {
            return directionToNeighbor == state.get(QuarryBlock.FACING);
        }
        return false;
    }

    private BooleanProperty propertyFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    private VoxelShape getShape(BlockState state) {
        VoxelShape shape = CORE_SHAPE;
        if (state.get(NORTH)) shape = VoxelShapes.union(shape, NORTH_SHAPE);
        if (state.get(SOUTH)) shape = VoxelShapes.union(shape, SOUTH_SHAPE);
        if (state.get(EAST)) shape = VoxelShapes.union(shape, EAST_SHAPE);
        if (state.get(WEST)) shape = VoxelShapes.union(shape, WEST_SHAPE);
        if (state.get(UP)) shape = VoxelShapes.union(shape, UP_SHAPE);
        if (state.get(DOWN)) shape = VoxelShapes.union(shape, DOWN_SHAPE);
        return shape;
    }
}
