package com.redtoast.blocks.RedstoneController;

import com.mojang.serialization.MapCodec;
import com.redtoast.Connections.PeripheralBlock;
import com.redtoast.neet.BulkRegistry;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

@PeripheralBlock
public class RedstoneControllerBlock extends FacingBlock implements BlockEntityProvider {
    public RedstoneControllerBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<? extends FacingBlock> getCodec() {
        return null;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return super.getPlacementState(ctx).with(Properties.FACING, ctx.getPlayer().isSneaking() ? ctx.getPlayerLookDirection() : ctx.getPlayerLookDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneControllerBlockEntity(pos, state);
    }

    @Override
    public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.FACING);
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        if (world!=null && world.getBlockEntity(pos) instanceof RedstoneControllerBlockEntity redstoneControllerBlockEntity){
            return redstoneControllerBlockEntity.getPowerOutput(direction.getOpposite());
        }
        return 0;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getWeakRedstonePower(state, world, pos, direction);
    }

    private int getPowerInDirection(World world, BlockPos pos, Direction direction) {
        int i = world.getEmittedRedstonePower(pos, direction);
        if (i >= 15) {
            return i;
        } else {
            BlockState blockState = world.getBlockState(pos);
            return Math.max(i, blockState.isOf(Blocks.REDSTONE_WIRE) ? blockState.get(RedstoneWireBlock.POWER) : 0);
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return type == BulkRegistry.fetchBlockEntityType("redstone_controller") ? (world2, blockPos, blockState, t) -> {
            if (world2!=null && world2.getBlockEntity(blockPos) instanceof RedstoneControllerBlockEntity redstoneControllerBlockEntity){
                for (Direction direction : Direction.values()) {
                    BlockPos farBlock = blockPos.offset(direction);
                    int basePower = getPowerInDirection(world2, farBlock, direction.getOpposite());
                    int comparator = world2.getBlockState(farBlock).hasComparatorOutput() ? world2.getBlockState(farBlock).getComparatorOutput(world2, farBlock) : basePower;
                    redstoneControllerBlockEntity.submitDirection(basePower, comparator, direction);
                }
                redstoneControllerBlockEntity.checkUpdate();
            }
        } : null;
    }
}
