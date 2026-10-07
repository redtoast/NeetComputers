package com.redtoast.Compat;

import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.Parameters;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Function;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.UUID;

public class WrappedPeripheral implements PeripheralProvider {
    private final IPeripheral peripheral;
    private final String[] functionNames;
    private final Hashtable<Method, Function> functionLookup = new Hashtable<>();
    private final BlockPos pos;

    public WrappedPeripheral(IPeripheral peripheral, BlockPos pos, Runtime runtime, java.util.function.Function<IPeripheral, IComputerAccess> computer){
        this.peripheral = peripheral;
        Class<?> clazz = peripheral.getClass();
        Method[] functions = clazz.getMethods();
        LinkedList<String> names = new LinkedList<>();
        for (Method method : functions){
            if (method.isAnnotationPresent(LuaFunction.class)){
                LuaFunction annotation = method.getAnnotation(LuaFunction.class);
                if (annotation.value().length==0){
                    names.add(method.getName());
                }else{
                    names.addAll(Arrays.asList(annotation.value()));
                }
                Parameters ruleset = Parameters.deduceCCTParameters(method);
                Function buffer = APILoader.sandboxFunction(method, peripheral, ruleset, runtime);
                if (annotation.mainThread()) {
                    Function finalBuffer = buffer;
                    buffer = new Function(false, finalBuffer.getName(), finalBuffer.getRules()) {
                        @Override
                        public Value call(Value<?>[] parameters) {
                            UUID uuid = UUID.randomUUID();
                            NeetComputersServer.mainThreadTicketTable.put(uuid, new MainThreadTicket(() -> finalBuffer.call(parameters)));
                            while (!NeetComputersServer.mainThreadTicketTable.get(uuid).isComplete()) {

                            }
                            Value answer = NeetComputersServer.mainThreadTicketTable.get(uuid).getAnswer();
                            NeetComputersServer.mainThreadTicketTable.remove(uuid);
                            return answer;
                        }
                    };
                }
                functionLookup.put(method, buffer);
            }
        }
        functionNames = names.toArray(new String[0]);
        this.pos = pos;

        peripheral.attach(computer.apply(peripheral));
    }

    @Override
    public String[] getFunctionNames() {
        return functionNames;
    }

    @Override
    public Value<?> callFunction(Runtime runtime, String name, Value<?>... Args) {
        Class<?> clazz = peripheral.getClass();
        Method[] functions = clazz.getMethods();
        for (Method method : functions){
            if (method.isAnnotationPresent(LuaFunction.class)){
                LuaFunction annotation = method.getAnnotation(LuaFunction.class);
                LinkedList<String> names = new LinkedList<>();
                if (annotation.value().length==0){
                    names.add(method.getName());
                }else{
                    names.addAll(Arrays.asList(annotation.value()));
                }
                for (String string : names){
                    if (string.equals(name)) {
                        return functionLookup.get(method).invoke(Args);
                    }
                }
            }
        }
        return Value.asError("Cant Find Function '"+name+"'");
    }

    @Override
    public String getTypeName() {
        String name = peripheral.getType();
        if (!name.contains(":")) return "computercraft:"+name;
        return name;
    }

    @Override
    public UUID getUuid() {
        return UUID.nameUUIDFromBytes(Long.toOctalString(pos.asLong()).getBytes());
    }

    @Override
    public @Nullable String getTag() {
        return null;
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

    @Override
    public boolean isCompatibility() {return true;}
}
