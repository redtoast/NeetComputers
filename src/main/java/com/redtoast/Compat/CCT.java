package com.redtoast.Compat;

import com.redtoast.Computer;
import com.redtoast.Connections.PeripheralProvider;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;
import dan200.computercraft.api.peripheral.WorkMonitor;
import dan200.computercraft.shared.peripheral.diskdrive.DiskDrivePeripheral;
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import dan200.computercraft.shared.peripheral.monitor.MonitorPeripheral;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Random;

import static dan200.computercraft.impl.Peripherals.getGenericPeripheral;

public class CCT {
    public static PeripheralProvider searchForCCT(BlockPos pos, World world, Computer computer){
        IPeripheral peripheral = PeripheralLookup.get().find(world, pos, null);//getPeripheral(world, pos);
        return switch (peripheral) {
            case null -> null;
            case DiskDrivePeripheral ignored -> null;
            case MonitorPeripheral monitor -> {
                MonitorBlockEntity monitorBlockEntity = (MonitorBlockEntity) monitor.getTarget();
                BlockPos originPos = monitorBlockEntity.getPos().offset(monitorBlockEntity.getRight(), -monitorBlockEntity.getXIndex()).offset(monitorBlockEntity.getDown(), -monitorBlockEntity.getYIndex());
                yield new WrappedPeripheral(monitor, originPos, computer.getRuntime(), (peripheral2) -> new ComputerWrapper(computer, peripheral2));
            }
            default ->
                    new WrappedPeripheral(peripheral, pos, computer.getRuntime(), (peripheral2) -> new ComputerWrapper(computer, peripheral2));
        };
    }

    public static PeripheralProvider searchForCCT(BlockPos pos, World world){
        IPeripheral peripheral = PeripheralLookup.get().find(world, pos, null);
        return switch (peripheral) {
            case null -> null;
            case DiskDrivePeripheral ignored -> null;
            default -> new WrappedPeripheral(peripheral, pos, null, blankAccess::new);
        };
    }

    private static IPeripheral getPeripheral(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            IPeripheral temp = getGenericPeripheral((ServerWorld) world, pos, direction, world.getBlockEntity(pos));
            if (temp!=null) return temp;
        }
        return null;
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
