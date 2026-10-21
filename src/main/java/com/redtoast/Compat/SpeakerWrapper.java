package com.redtoast.Compat;

import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.annotations.CanBeNull;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.Exposable;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Table;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.ObjectLuaTable;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SpeakerWrapper implements PeripheralProvider, Exposable {
    private final SpeakerPeripheral base;
    private final Map<Computer, IComputerAccess> accessMap = new Hashtable<>();
    private final UUID uuid;

    public SpeakerWrapper(SpeakerPeripheral base, UUID uuid) {
        this.base = base;
        this.uuid = uuid;
    }

    @Exposed
    public boolean playNote(String instrumentA, @CanBeNull Double volumeA, @CanBeNull Double pitchA) {
        try{
            return base.playNote(null, instrumentA, Optional.ofNullable(volumeA), Optional.ofNullable(pitchA));
        } catch (LuaException e) {
            throw new ExposedError(e.getMessage());
        }
    }

    @Exposed
    public boolean playSound(String name, @CanBeNull Double volumeA, @CanBeNull Double pitchA) {
        try{
            return base.playSound(null, name, Optional.ofNullable(volumeA), Optional.ofNullable(pitchA));
        } catch (LuaException e) {
            throw new ExposedError(e.getMessage());
        }
    }

    @Exposed
    public boolean playAudio(Table audio, @CanBeNull Double volume) {
        try{
            Hashtable<Object, Object> table = new Hashtable<>();
            audio.foreach((key, value) -> table.put(key.getValue(), value.getValue()));
            return base.playAudio(null, new ObjectLuaTable(table), Optional.ofNullable(volume));
        } catch (LuaException e) {
            throw new ExposedError(e.getMessage());
        }
    }

    @Exposed
    public void stop() {
        base.stop();
    }

    @Override
    public String[] getFunctionNames() {
        return new String[]{"playNote", "playSound", "playAudio", "stop"};
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
        return "computercraft:speaker";
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
        IComputerAccess access = new ComputerWrapper(computer, base);
        accessMap.put(computer, access);
        base.attach(access);
    }

    @Override
    public void computerDetached(Computer computer) {
        base.detach(accessMap.remove(computer));
    }

    @Override
    public boolean isCompatibility() {return true;}
}
