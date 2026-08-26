package com.redtoast.APIS;

import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import com.redtoast.simulation.annotations.CanBeNull;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.annotations.Primative;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.Parameters;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Function;
import com.redtoast.simulation.value.ValueTypes.List;
import com.redtoast.simulation.value.ValueTypes.Table;
import com.redtoast.simulation.value.ValueTypes.Tuple;

import java.util.Objects;
import java.util.UUID;

public class IOAPI implements API {
    private final Computer computer;

    public static class WrappedFunction extends Function{
        private final Computer computer;
        private final UUID uuid;

        public WrappedFunction(Computer computer, UUID uuid, String functionName){
            super(false, functionName, Parameters.any());
            this.computer = computer;
            this.uuid = uuid;
        }

        @Override
        public Value call(Value<?>[] parameters) {
            if (!computer.isOn() || computer.isCrashed()) return Value.asError("Computer dead, how did you get here?");
            for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
                if (Objects.equals(peripheralProvider.getUuid().toString(), uuid.toString())){
                    return peripheralProvider.callFunction(computer.getRuntime(), getName(), parameters);
                }
            }
            return Value.asError("Peripheral not found");
        }
    }

    @Override
    public String getLabel() {
        return "io";
    }

    public IOAPI(Computer computer){
        this.computer = computer;
    }

    @Exposed
    public List getPeripherals(){
        List list = new List();
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            list.add(Value.of(peripheralProvider.getUuid().toString()));
        }
        return list;
    }

    @Exposed
    public String getType(String uuidString){
        try {
            UUID.fromString(uuidString);
        }catch (IllegalArgumentException illegalArgumentException){
            throw new ExposedError("UUID invalidly formatted");
        }
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getUuid().toString(), uuidString)) return peripheralProvider.getTypeName();
        }
        throw new ExposedError("Peripheral not found");
    }

    @Exposed
    public String getTag(String uuidString){
        try {
            UUID.fromString(uuidString);
        }catch (IllegalArgumentException illegalArgumentException){
            throw new ExposedError("UUID invalidly formatted");
        }
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getUuid().toString(), uuidString)) return peripheralProvider.getTag();
        }
        throw new ExposedError("Peripheral not found");
    }

    @Exposed
    public void setTag(String uuidString, @CanBeNull String tag){
        try {
            UUID.fromString(uuidString);
        }catch (IllegalArgumentException illegalArgumentException){
            throw new ExposedError("UUID invalidly formatted");
        }
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getUuid().toString(), uuidString)) {
                peripheralProvider.setTag(tag==null ? "" : tag);
                return;
            }
        }
        throw new ExposedError("Peripheral not found");
    }

    @Exposed
    public boolean isCompatibility(String uuidString){
        try {
            UUID.fromString(uuidString);
        }catch (IllegalArgumentException illegalArgumentException){
            throw new ExposedError("UUID invalidly formatted");
        }
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getUuid().toString(), uuidString)) return peripheralProvider.isCompatibility();
        }
        throw new ExposedError("Peripheral not found");
    }

    @Exposed
    public List queryTag(String tag){
        if (tag.isBlank()) throw new ExposedError("tag cant be blank");
        tag = tag.trim();
        List buffer = new List();
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getTag(), tag)) buffer.add(Value.of(peripheralProvider.getUuid().toString()));
        }
        return buffer;
    }

    @Exposed
    public List queryType(String type){
        List buffer = new List();
        if (type.contains(":")) type = "neetcomputers:" + type;
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getTypeName(), type)) buffer.add(Value.of(peripheralProvider.getUuid().toString()));
        }
        return buffer;
    }

    @Exposed
    public Table wrapPeripheral(String uuidString){
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        }catch (IllegalArgumentException illegalArgumentException){
            throw new ExposedError("UUID invalidly formatted");
        }
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getUuid().toString(), uuidString)){
                Table table = new Table();
                for (String functionName : peripheralProvider.getFunctionNames()){
                    table.put(functionName, new WrappedFunction(computer, uuid, functionName).asValue());
                }
                return table;
            }
        }
        throw new ExposedError("Peripheral not found");
    }

    @Exposed
    public Value callFunction(String uuidString, String functionName, Tuple args){
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        }catch (IllegalArgumentException illegalArgumentException){
            throw new ExposedError("UUID invalidly formatted");
        }
        for (PeripheralProvider peripheralProvider : computer.getPeripheralProviders()){
            if (Objects.equals(peripheralProvider.getUuid(), uuid)){
                for (String functionName2 : peripheralProvider.getFunctionNames()){
                    if (functionName.equals(functionName2)) return peripheralProvider.callFunction(computer.getRuntime(), functionName, args.toArray());
                }
            }
        }
        throw new ExposedError("Peripheral not found");
    }

    @Exposed
    public void broadcastLocal(@Primative Tuple args) {
        computer.sendNetworkMessage(args.toArray());
    }
}
