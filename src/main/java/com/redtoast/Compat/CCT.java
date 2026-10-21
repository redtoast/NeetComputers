package com.redtoast.Compat;

import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;
import dan200.computercraft.api.peripheral.WorkMonitor;
import dan200.computercraft.shared.peripheral.redstone.RedstoneRelayPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CCT {
    public static PeripheralProvider searchForCCT(BlockPos pos, World world, Computer computer){
        IPeripheral peripheral = PeripheralLookup.get().find(world, pos, null);
        return switch (peripheral) {
            case null -> null;
            case SpeakerPeripheral speaker ->
                    new SpeakerWrapper(speaker, UUID.nameUUIDFromBytes(Long.toOctalString(pos.asLong()).getBytes()));
            case RedstoneRelayPeripheral relay ->
                    new RedstoneRelayWrapper(relay, UUID.nameUUIDFromBytes(Long.toOctalString(pos.asLong()).getBytes()));
            default ->
                    new WrappedPeripheral(peripheral, pos, computer.getRuntime(), (peripheral2) -> new ComputerWrapper(computer, peripheral2));
        };
    }

    public static PeripheralProvider searchForCCT(BlockPos pos, World world){
        IPeripheral peripheral = PeripheralLookup.get().find(world, pos, null);
        return switch (peripheral) {
            case null -> null;
            case SpeakerPeripheral speaker ->
                    new SpeakerWrapper(speaker, UUID.nameUUIDFromBytes(Long.toOctalString(pos.asLong()).getBytes()));
            case RedstoneRelayPeripheral relay ->
                    new RedstoneRelayWrapper(relay, UUID.nameUUIDFromBytes(Long.toOctalString(pos.asLong()).getBytes()));
            default -> new WrappedPeripheral(peripheral, pos, null, blankAccess::new);
        };
    }

    static class blankAccess implements IComputerAccess {
        final int id = new Random().nextInt(500)+500;
        private final IPeripheral peripheral;

        blankAccess(IPeripheral peripheral) {
            this.peripheral = peripheral;
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
            return id;
        }

        @Override
        public void queueEvent(String event, @Nullable Object... arguments) {

        }

        @Override
        public @NotNull String getAttachmentName() {
            return "front";
        }

        @Override
        public @NotNull Map<String, IPeripheral> getAvailablePeripherals() {
            return Map.of(getAttachmentName(), peripheral);
        }

        @Override
        public @Nullable IPeripheral getAvailablePeripheral(String name) {
            if (name.equals(getAttachmentName())) return peripheral;
            return null;
        }

        @Override
        public @NotNull WorkMonitor getMainThreadMonitor() {
            return null;
        }
    }
}
