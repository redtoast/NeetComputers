package com.redtoast.blocks.DynamicLight;

import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import com.redtoast.Connections.PipeRenderSource;
import com.redtoast.Connections.PipeType;
import com.redtoast.neet.BulkRegistry;
import com.redtoast.simulation.ParameterBuilder;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.Parameters;
import com.redtoast.simulation.value.Value;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DynamicLightBlockEntity extends BlockEntity implements PeripheralProvider, PipeRenderSource {
    private static final Parameters intrule = ParameterBuilder.make(int.class);
    private static final Parameters empty = Parameters.empty();
    private UUID uuid = null;
    private String tag = null;
    private Integer lightLevel = null;
    private Integer lightLevelCurrent = 0;

    public DynamicLightBlockEntity(BlockPos pos, BlockState state) {
        super(BulkRegistry.fetchBlockEntityType("dynamic_light"), pos, state);
    }

    @Override
    public String[] getFunctionNames() {
        return new String[]{"setLuminance", "turnOn", "turnOff"};
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        if (uuid==null) uuid = UUID.randomUUID();
        nbt.putUuid("uuid", uuid);
        if (tag!=null) nbt.putString("tag", tag);
        nbt.putInt("light_level", lightLevel==null ? 0 : lightLevel);
        super.writeNbt(nbt, registryLookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("uuid", NbtElement.INT_ARRAY_TYPE)){
            uuid = nbt.getUuid("uuid");
        }
        if (nbt.contains("tag")){
            tag = nbt.getString("tag");
        }
        if (nbt.contains("light_level", NbtElement.INT_TYPE) && lightLevel==null){
            lightLevel = nbt.getInt("light_level");
            lightLevelCurrent = nbt.getInt("light_level");
        }

    }

    @Override
    public Value<?> callFunction(Runtime runtime, String name, Value<?>... args) {
        if (Objects.equals(name, "setLuminance")){
            Optional<String> check = intrule.canCast(args, null);
            if (check.isPresent()) return Value.asError(check.get());
            lightLevel = (Integer) intrule.cast(args)[0];
            return Value.NULL;
        }
        if (Objects.equals(name, "turnOff")){
            Optional<String> check = empty.canCast(args, null);
            if (check.isPresent()) return Value.asError(check.get());
            lightLevel = 0;
            return Value.NULL;
        }
        if (Objects.equals(name, "turnOn")){
            Optional<String> check = empty.canCast(args, null);
            if (check.isPresent()) return Value.asError(check.get());
            lightLevel = 15;
            return Value.NULL;
        }
        return Value.asError("Cant Find Function '"+name+"'");
    }

    public static <T extends BlockEntity> void tick(World world, BlockPos blockPos, BlockState blockState, T t){
        BlockEntity blockEntity = world.getBlockEntity(blockPos);
        if (blockEntity instanceof DynamicLightBlockEntity dynamicLightBlockEntity && dynamicLightBlockEntity.lightLevel!=null){
            if (dynamicLightBlockEntity.lightLevel < dynamicLightBlockEntity.lightLevelCurrent && dynamicLightBlockEntity.lightLevelCurrent>0) dynamicLightBlockEntity.lightLevelCurrent--;
            if (dynamicLightBlockEntity.lightLevel > dynamicLightBlockEntity.lightLevelCurrent && dynamicLightBlockEntity.lightLevelCurrent<15) dynamicLightBlockEntity.lightLevelCurrent++;
            world.setBlockState(blockPos, blockState.with(DynamicLightBlock.LUMINANCE, dynamicLightBlockEntity.lightLevelCurrent), Block.NOTIFY_ALL);
        }
    }

    @Override
    public String getTypeName() {
        return "neetcomputers:dynamic_light";
    }

    @Override
    public UUID getUuid() {
        if (uuid==null) uuid = UUID.randomUUID();
        return uuid;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public void setTag(@NotNull String tag) {
        this.tag = tag.isBlank() ? null : tag.trim();
    }

    @Override
    public void computerAttached(Computer computer) {

    }

    @Override
    public void computerDetached(Computer computer) {

    }

    @Override
    public boolean shouldRenderPipeType(PipeType type) {
        return type==PipeType.PERIPHERAL;
    }
}
