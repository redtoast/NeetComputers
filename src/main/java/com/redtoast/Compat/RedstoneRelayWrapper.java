package com.redtoast.Compat;

import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.Exposable;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.Value;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.peripheral.redstone.RedstoneRelayPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class RedstoneRelayWrapper implements PeripheralProvider, Exposable {
    private final RedstoneRelayPeripheral base;
    private final UUID uuid;

    public RedstoneRelayWrapper(RedstoneRelayPeripheral base, UUID uuid) {
        this.base = base;
        this.uuid = uuid;
    }

    public ComputerSide getSide(String direction) {
        ComputerSide side = ComputerSide.valueOfInsensitive(direction);
        if (side==null) throw new ExposedError("Invalid computer side");
        return side;
    }

    @Exposed
    public void setOutput(String side, boolean on) {
        base.setOutput(getSide(side), on);
    }

    @Exposed
    public boolean getOutput(String side) {
        return base.getOutput(getSide(side));
    }

    @Exposed
    public boolean getInput(String side) {
        return base.getInput(getSide(side));
    }

    @Exposed
    public void setAnalogOutput(String side, int value){
        try {
            base.setAnalogOutput(getSide(side), value);
        } catch (LuaException e) {
            throw new ExposedError(e.getMessage());
        }
    }

    @Exposed
    public void setAnalogueOutput(String side, int value){
        try {
            base.setAnalogOutput(getSide(side), value);
        } catch (LuaException e) {
            throw new ExposedError(e.getMessage());
        }
    }

    @Exposed
    public int getAnalogOutput(String side) {
        return base.getAnalogOutput(getSide(side));
    }

    @Exposed
    public int getAnalogueOutput(String side) {
        return base.getAnalogOutput(getSide(side));
    }

    @Exposed
    public int getAnalogInput(String side) {
        return base.getAnalogInput(getSide(side));
    }

    @Exposed
    public int getAnalogueInput(String side) {
        return base.getAnalogInput(getSide(side));
    }

    @Exposed
    public void setBundledOutput(String side, int output) {
        base.setBundledOutput(getSide(side), output);
    }

    @Exposed
    public int getBundledOutput(String side) {
        return base.getBundledOutput(getSide(side));
    }

    @Exposed
    public int getBundledInput(String side) {
        return base.getBundledInput(getSide(side));
    }

    @Exposed
    public boolean testBundledInput(String side, int mask) {
        return base.testBundledInput(getSide(side), mask);
    }

    @Override
    public String[] getFunctionNames() {
        return APILoader.getFunctions(this);
    }

    @Override
    public Value<?> callFunction(Runtime runtime, String name, Value<?>... Args) {
        try{
            return APILoader.searchAndCall(this, runtime, name, Args);
        } catch (APILoader.LoaderError e) {
            APILoader.printJavaError(e);
            return Value.asError("API loading error");
        }
    }

    @Override
    public String getTypeName() {
        return "computercraft:redstone_relay";
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public @Nullable String getTag() {
        return "";
    }

    @Override
    public void setTag(@NotNull String tag) {

    }

    @Override
    public void computerAttached(Computer computer) {

    }

    @Override
    public void computerDetached(Computer computer) {

    }
}
