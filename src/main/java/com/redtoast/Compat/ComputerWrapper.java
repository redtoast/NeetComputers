package com.redtoast.Compat;

import com.redtoast.Computer;
import com.redtoast.simulation.events.EventGeneric;
import com.redtoast.simulation.events.EventLabel;
import com.redtoast.simulation.value.Value;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class ComputerWrapper implements IComputerAccess {
    private final Computer computer;
    private final IPeripheral peripheral;

    public ComputerWrapper(Computer computer, IPeripheral peripheral){
        this.computer = computer;
        this.peripheral = peripheral;
        computer.computerAccesses.add(this); //add to computer's internal list so computers can detach properly
    }

    public IPeripheral getPeripheral() {
        return this.peripheral;
    }

    @Override
    public @Nullable String mount(String desiredLocation, Mount mount, String driveName) {
        return null;
    }

    @Override
    public @Nullable String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
        return null;
    }

    @Override
    public void unmount(@Nullable String location) {

    }

    @Override
    public int getID() {
        return computer.getUuid().hashCode();
    }

    @Override
    public void queueEvent(String event, @Nullable Object... arguments) {
        computer.queueEvent(new EventGeneric(event, arguments==null ? new Value[0] : Value.of(arguments).getValue().toArray()), EventLabel.COMPATIBILITY);
    }

    @Override
    public String getAttachmentName() {
        return peripheral!=null ? (peripheral.getType()+"_1") : "dont worry about it";
    }

    @Override
    public Map<String, IPeripheral> getAvailablePeripherals() {
        return peripheral!=null ? Map.of(getAttachmentName(), peripheral) : Map.of();
    }

    @Override
    public @Nullable IPeripheral getAvailablePeripheral(String name) {
        if (name.equals(getAttachmentName())) return peripheral;
        return null;
    }

    @Override
    public WorkMonitor getMainThreadMonitor() {
        return null;
    }

    public void removeSelf(){
        getPeripheral().detach(this);
    }
}
