package com.errorsys.quarry_reforged.content.block;

import com.errorsys.quarry_reforged.content.ModBlockEntities;
import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.World;
import reborncore.api.ToolManager;
import org.jetbrains.annotations.Nullable;

public class QuarryBlock extends BlockWithEntity {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public QuarryBlock() {
        super(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(4.0f, 8.0f));
        setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false).with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(ACTIVE, false).with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (ToolManager.INSTANCE.canHandleTool(held)) {
            if (world.isClient) return ActionResult.SUCCESS;
            if (!(world.getBlockEntity(pos) instanceof QuarryBlockEntity quarry)) return ActionResult.PASS;
            boolean wrenchPickup = player.shouldCancelInteraction() || player.isSneaking() || player.isInSneakingPose();
            if (!ToolManager.INSTANCE.handleTool(held, pos, world, player, hit.getSide(), true)) return ActionResult.PASS;

            if (wrenchPickup) {
                ItemStack drop = new ItemStack(this.asItem());
                NbtCompound stateNbt = quarry.createItemStateNbt();
                if (!stateNbt.isEmpty()) {
                    drop.getOrCreateNbt().put("blockEntity_data", stateNbt);
                }
                net.minecraft.util.ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                world.removeBlock(pos, false);
            } else {
                Direction target = hit.getSide().getAxis().isHorizontal() ? hit.getSide() : state.get(FACING).rotateYClockwise();
                world.setBlockState(pos, state.with(FACING, target), Block.NOTIFY_ALL);
            }
            return ActionResult.CONSUME;
        }

        if (world.isClient) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof QuarryBlockEntity quarry) {
            NamedScreenHandlerFactory factory = quarry;
            player.openHandledScreen(factory);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) return;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof QuarryBlockEntity quarry) {
            quarry.onBroken();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, ModBlockEntities.QUARRY, QuarryBlockEntity::tickServer);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient) return;
        if (!itemStack.hasNbt()) return;
        NbtCompound root = itemStack.getNbt();
        if (root == null || !root.contains("blockEntity_data")) return;
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof QuarryBlockEntity quarry)) return;
        quarry.applyItemStateNbt(root.getCompound("blockEntity_data"));
    }
}
