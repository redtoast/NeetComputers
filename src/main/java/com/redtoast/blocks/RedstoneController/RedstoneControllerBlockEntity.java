package com.redtoast.blocks.RedstoneController;

import com.redtoast.blocks.Generics.PeripheralBlockEntity;
import com.redtoast.neet.BulkRegistry;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Table;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class RedstoneControllerBlockEntity extends PeripheralBlockEntity {
    private int[] levels = new int[6];
    private final int[] scan = new int[6];
    private final int[] comparator = new int[6];
    private final Direction facing;
    private boolean shouldUpdate = true;

    public RedstoneControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BulkRegistry.fetchBlockEntityType("redstone_controller"), pos, state, "neetcomputers:redstone_controller");
        facing = state.get(FacingBlock.FACING);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putIntArray("levels", levels);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("levels")) levels = nbt.getIntArray("levels");
    }

    public int getPowerOutput(Direction direction){
        return levels[direction.ordinal()] & 15;
    }

    public void submitDirection(int power, int comparator, Direction direction) {
        scan[direction.ordinal()] = power;
        this.comparator[direction.ordinal()] = comparator;
    }

    public void checkUpdate(){
        if (shouldUpdate) {
            update();
            shouldUpdate=false;
        }
    }

    public void update(){
        assert world != null;
        world.updateNeighbors(getPos(), world.getBlockState(getPos()).getBlock());
        world.setBlockState(pos, world.getBlockState(getPos()), Block.NOTIFY_LISTENERS);
    }

    @Exposed
    public int getInput(String direction) {
        Direction cardinalDirection = translate(direction);
        return Math.max(getPowerOutput(cardinalDirection), scan[cardinalDirection.ordinal()]);
    }

    @Exposed
    public int getComparator(String direction) {
        Direction cardinalDirection = translate(direction);
        return Math.max(getPowerOutput(cardinalDirection), comparator[cardinalDirection.ordinal()]);
    }

    @Exposed
    public int getOutput(String direction) {
        return getPowerOutput(translate(direction));
    }

    @Exposed
    public void setOutput(String direction, int powerLevel){
        if (powerLevel< 0 || powerLevel>15) throw new ExposedError("power level provided out of accepted range [0-15]");
        Direction cardinal = translate(direction);
        levels[cardinal.ordinal()] = powerLevel;
        markDirty();
        shouldUpdate = true;
    }

    @Exposed
    public void setOutput(String direction, boolean isPowered){
        Direction cardinal = translate(direction);
        levels[cardinal.ordinal()] = isPowered ? 15 : 0;
        markDirty();
        shouldUpdate = true;
    }

    @Exposed
    public void setAllOutputs(int powerLevel){
        if (powerLevel< 0 || powerLevel>15) throw new ExposedError("power level provided out of accepted range [0-15]");
        for (int i = 0; i < 6; i++) levels[i] = powerLevel;
        markDirty();
        shouldUpdate = true;
    }

    @Exposed
    public void setAllOutputs(boolean isPowered){
        for (int i = 0; i < 6; i++) levels[i] = isPowered ? 15 : 0;
        markDirty();
        shouldUpdate = true;
    }

    @Exposed
    public Table getSides(){
        Table table = new Table();
        for (String relative : new String[]{"bottom", "top", "front", "back", "left", "right"}){
            table.put(relative, Value.of(getInput(relative)));
        }
        return table;
    }

    @Exposed
    public Table getSidesCardinal(){
        Table table = new Table();
        for (Direction direction : Direction.values()){
            table.put(direction.asString(), Value.of(Math.max(getPowerOutput(direction), scan[direction.ordinal()])));
        }
        return table;
    }

    public Direction translate(String direction){
        if (direction.equalsIgnoreCase("left")) return facing.getAxis() != Direction.Axis.Y ? facing.rotateCounterclockwise(Direction.Axis.Y) : Direction.WEST;
        if (direction.equalsIgnoreCase("right")) return facing.getAxis() != Direction.Axis.Y ? facing.rotateClockwise(Direction.Axis.Y) : Direction.EAST;
        if (direction.equalsIgnoreCase("top")) return facing.getAxis() != Direction.Axis.Y ? Direction.UP : (facing==Direction.UP ? Direction.SOUTH : Direction.NORTH);
        if (direction.equalsIgnoreCase("bottom")) return facing.getAxis() != Direction.Axis.Y ? Direction.DOWN : (facing==Direction.UP ? Direction.NORTH : Direction.SOUTH);
        if (direction.equalsIgnoreCase("front")) return facing;
        if (direction.equalsIgnoreCase("back")) return facing.getOpposite();
        for (Direction subDirection : Direction.values()) if (subDirection.toString().equalsIgnoreCase(direction)) return subDirection;
        throw new ExposedError("Invalid direction");
    }
}
