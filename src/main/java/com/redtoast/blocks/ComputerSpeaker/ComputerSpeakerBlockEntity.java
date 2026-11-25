package com.redtoast.blocks.ComputerSpeaker;

import com.redtoast.APIS.SoundAPI;
import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import com.redtoast.Connections.PipeRenderSource;
import com.redtoast.Connections.PipeType;
import com.redtoast.blocks.Generics.PeripheralBlockEntity;
import com.redtoast.blocks.Generics.SpeakerBlockEntity;
import com.redtoast.neet.BulkRegistry;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.events.EventGeneric;
import com.redtoast.simulation.events.EventLabel;
import com.redtoast.simulation.value.Value;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ComputerSpeakerBlockEntity extends SpeakerBlockEntity implements PeripheralProvider, PipeRenderSource {
    private final java.util.List<Computer> computers = new LinkedList<>();
    private final API soundAPI;
    private UUID uuid = null;
    private String tag = null;
    private final String[] functionTable;

    public ComputerSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(BulkRegistry.fetchBlockEntityType("computer_speaker"), pos, state);
        this.soundAPI = new SoundAPI(this);
        functionTable = APILoader.getFunctions(soundAPI);
    }

    @Override
    public void queueEvent(String eventName, Object... args){
        if (getWorld()!=null && !getWorld().isClient){
            com.redtoast.simulation.value.ValueTypes.List buffer = Value.of(args).getValue();
            buffer.addFirst(Value.of(eventName));
            buffer.addFirst(Value.of(getUuid().toString()));
            EventGeneric event = new EventGeneric("neetcomputers:computer_speaker", buffer);
            for (Computer computer : computers){
                computer.queueEvent(event, EventLabel.PERIPHERAL);
            }
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        if (uuid==null) uuid = UUID.randomUUID();
        nbt.putUuid("peripheralUUID", uuid);
        if (tag!=null) nbt.putString("peripheralTag", tag);
        super.writeNbt(nbt, registryLookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("peripheralUUID")) uuid = nbt.getUuid("peripheralUUID");
        if (nbt.contains("peripheralTag")) tag = nbt.getString("peripheralTag");
    }

    @Override
    public String[] getFunctionNames() {
        return functionTable;
    }

    @Override
    public Value<?> callFunction(Runtime runtime, String name, Value<?>... Args) {
        try{
            return APILoader.searchAndCall(soundAPI, runtime, name, Args);
        } catch (APILoader.LoaderError e) {
            APILoader.printJavaError(e);
            return Value.asError("API loading error");
        }
    }

    @Override
    public String getTypeName() {
        return "neetcomputers:computer_speaker";
    }

    @Override
    public UUID getUuid() {
        if (uuid==null) {
            uuid = UUID.randomUUID();
            markDirty();
        }
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
        computers.add(computer);
    }

    @Override
    public void computerDetached(Computer computer) {
        computers.remove(computer);
    }

    @Override
    public boolean shouldRenderPipeType(PipeType type) {
        return type==PipeType.PERIPHERAL;
    }

    @Override
    public boolean equals(Object object){
        return object instanceof ComputerSpeakerBlockEntity be && be.getUuid().equals(getUuid());
    }
}
