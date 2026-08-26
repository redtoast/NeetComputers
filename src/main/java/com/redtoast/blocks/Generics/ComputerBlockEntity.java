package com.redtoast.blocks.Generics;

import com.redtoast.Compat.ComputerWrapper;
import com.redtoast.Computer;
import com.redtoast.ComputerState;
import com.redtoast.Connections.*;
import com.redtoast.blocks.ComputerDataComponent;
import com.redtoast.blocks.DesktopComputer.DesktopBlockComputer;
import com.redtoast.blocks.Generics.Displays.BinaryGraphicsRenderProvider;
import com.redtoast.graphics.BinaryGraphicsArray;
import com.redtoast.graphics.screens.RGBScreenHandler;
import com.redtoast.graphics.RGBGraphicsArray;
import com.redtoast.neet.Networking.BinaryGraphicsPayload;
import com.redtoast.neet.Networking.ComputerScreenInitPayload;
import com.redtoast.neet.config.ConfigLoader;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.config.ComputerConfig;
import com.redtoast.simulation.events.EventGeneric;
import com.redtoast.simulation.events.EventLabel;
import com.redtoast.simulation.Parameters;
import com.redtoast.simulation.value.Value;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ComputerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<ComputerScreenInitPayload>, PeripheralProvider, NetworkReceiver, PipeRenderSource, BinaryGraphicsRenderProvider {
    private final Computer computer;
    private final RGBGraphicsArray graphics;
    private String tag = null;
    private boolean loaded = false;
    private List<com.redtoast.Connections.PeripheralProvider> peripheralProviderCache = List.of();
    private List<NetworkReceiver> networkReceiverCache = List.of();
    // scanForPeripheralsInternal/scanForNetworkInternal walk the whole connected cable graph with a BFS;
    // running that every single tick (20x/sec) per computer is expensive and rarely necessary since the
    // topology essentially never changes tick-to-tick. Throttle it to once per second instead.
    private int scanCounter = 0;
    private static final int PERIPHERAL_SCAN_INTERVAL_TICKS = 20;

    public ComputerBlockEntity(BlockEntityType type, BlockPos pos, BlockState state, ComputerConfig specifications) {
        super(type, pos, state);
        ComputerBlockEntity be = this;
        computer = new Computer(specifications) {
            @Override
            public List<PeripheralProvider> getPeripheralProviders() {
                return be.scanForPeripherals();
            }

            @Override
            public void sendNetworkMessage(Value<?>... args) {
                for (NetworkReceiver receiver : be.networkReceiverCache) receiver.receiveNetwork(args);
            }

            @Override
            public void saveNBT() {
                markDirty();
            }

            @Override
            public boolean isClient() {
                return be.getWorld().isClient();
            }

            @Override
            public void refreshBinaryGraphics() {
                if (specifications.doesBinaryGraphics()) renderBinaryGraphics(Objects.requireNonNull(getBinaryGraphics()));
            }

            @Override
            public Object getParentEntity() {
                return be;
            }

            @Override
            public World getWorld() {
                return be.getWorld();
            }

            @Override
            public int getLunarTime() {
                return getWorld() == null ? 0 : (int) getWorld().getLunarTime();
            }
        };
        graphics = computer.getGraphics();
    }

    public void renderBinaryGraphics(BinaryGraphicsArray graphics){
        CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(new BinaryGraphicsPayload(getPos(), graphics));

        if (getWorld() instanceof ServerWorld serverWorld) {
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                player.networkHandler.sendPacket(packet);
            }
        }
    }

    public void AssignPointers(World world, ItemStack itemStack){
        if (itemStack.contains(ComputerDataComponent.TYPE)) {
            ComputerDataComponent data = itemStack.get(ComputerDataComponent.TYPE);
            computer.load(data);
        }else{
            MinecraftServer server = world.getServer();
            computer.load(server);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt = computer.saveNBT(nbt);
        if (tag!=null) nbt.putString("peripheralTag", tag);
        super.writeNbt(nbt, registryLookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("peripheralTag")) tag = nbt.getString("peripheralTag");
        computer.load(nbt);
    }

    public ActionResult onUse(PlayerEntity player, BlockState state){
        if (!player.isSneaking() && !player.isUsingItem() && computer.isOn()){
            NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
            if (screenHandlerFactory != null) {
                player.openHandledScreen(screenHandlerFactory);
            }
        }
        if (player.isSneaking()){
            computer.stop();
        }else{
            if (computer.isCrashed()) player.sendMessage(Text.literal(computer.getCrashMessage()));
            computer.start();
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal(computer.getConfiguration().modelName());
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        if (!computer.getConfiguration().doesColorGraphics()) return null;
        return new RGBScreenHandler(syncId,graphics,computer);
    }

    public void handlePeripheralScan() {
        List<PeripheralProvider> scanned = scanForPeripheralsInternal();
        List<PeripheralProvider> oldProviders = new ArrayList<>(peripheralProviderCache);
        List<PeripheralProvider> scanBuffer = new ArrayList<>(scanned);
        for (int i = 0; i < oldProviders.size(); i++) {
            for (int j = 0; j < scanBuffer.size(); j++) {
                if (oldProviders.get(i).getUuid().equals(scanBuffer.get(j).getUuid())) {
                    scanBuffer.remove(j);
                    oldProviders.remove(i);
                    i--;
                    break;
                }
            }
        }
        for (PeripheralProvider provider : oldProviders) {
            provider.computerDetached(computer);
            computer.queueEvent(new EventGeneric("peripheralDetached", Value.of(provider.getTypeName()), Value.of(provider.getUuid().toString())), EventLabel.SYSTEM);
        }
        for (PeripheralProvider provider : scanBuffer) {
            provider.computerAttached(computer);
            if (loaded) {
                computer.queueEvent(new EventGeneric("peripheralAttached", Value.of(provider.getTypeName()), Value.of(provider.getUuid().toString())), EventLabel.SYSTEM);
            }
        }
        peripheralProviderCache = scanned;
        loaded = true;
    }

    public static <T extends BlockEntity> void tick(World world, BlockPos blockPos, BlockState blockState, T t) {
        if (!world.isClient()){
            BlockEntity be = world.getBlockEntity(blockPos);
            if (be instanceof ComputerBlockEntity computerBlock) {
                if (computerBlock.computer.getFileSystem()!=null && computerBlock.scanCounter % PERIPHERAL_SCAN_INTERVAL_TICKS == 0) {
                    computerBlock.handlePeripheralScan();
                    computerBlock.networkReceiverCache = computerBlock.scanForNetworkInternal();
                }
                computerBlock.scanCounter++;
                if (!computerBlock.computer.isLoaded()) return;
                computerBlock.computer.tick(world);
                BlockState current = world.getBlockState(blockPos);
                BlockState updated = current
                        .with(DesktopBlockComputer.ON, computerBlock.computer.isOn())
                        .with(DesktopBlockComputer.CRASHED, computerBlock.computer.isCrashed())
                        .with(DesktopBlockComputer.STATE, computerBlock.computer.getStatus().ordinal());
                if (!updated.equals(current)) {
                    world.setBlockState(blockPos, updated, Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    public void markRemoved() {
        this.removed = true;
        if ((boolean) ConfigLoader.getServerConfig("cct-compatibility")) {
            for (ComputerWrapper computerAccess : computer.computerAccesses) {
                computerAccess.removeSelf(); //detach computers from peripherals properly
            }
        }
    }

    public Computer getComputer() {return computer;}

    public List<com.redtoast.Connections.PeripheralProvider> scanForPeripherals(){
        return peripheralProviderCache;
    }

    private boolean isntDuplicate(LinkedList<PeripheralProvider> peripherals, PeripheralProvider duplicate){
        for (PeripheralProvider provider : peripherals) if (provider.getUuid().equals(duplicate.getUuid())) return false;
        return true;
    }

    private boolean isntDuplicate(LinkedList<NetworkReceiver> peripherals, NetworkReceiver duplicate){
        for (NetworkReceiver provider : peripherals) if (provider.getUuid().equals(duplicate.getUuid())) return false;
        return true;
    }

    public List<com.redtoast.Connections.PeripheralProvider> scanForPeripheralsInternal() {
        LinkedList<BlockPos> todoList = new LinkedList<>();
        LinkedList<BlockPos> investigated = new LinkedList<>();
        LinkedList<PeripheralProvider> peripherals = new LinkedList<>();
        todoList.add(getPos());
        investigated.add(getPos());

        World world = getWorld();

        while (!todoList.isEmpty()){
            BlockPos current = todoList.getFirst();
            todoList.remove();
            for (Direction direction : Direction.values()){
                BlockPos investigating = current.offset(direction);
                if (investigated.contains(investigating)) {
                    continue;
                }
                if (CableManager.getInstance().pipeExists(world, investigating, PipeType.PERIPHERAL)) {
                    todoList.add(investigating);
                }
                PeripheralProvider provider = Connections.getPeripheral(investigating, world, computer);
                if (provider!=null && isntDuplicate(peripherals, provider)){
                    peripherals.add(provider);
                }
                investigated.add(investigating);
            }
        }

        return peripherals;
    }

    public List<NetworkReceiver> scanForNetworkInternal() {
        LinkedList<BlockPos> todoList = new LinkedList<>();
        LinkedList<BlockPos> investigated = new LinkedList<>();
        LinkedList<NetworkReceiver> networkDevices = new LinkedList<>();
        todoList.add(getPos());
        investigated.add(getPos());

        World world = getWorld();

        while (!todoList.isEmpty()){
            BlockPos current = todoList.getFirst();
            todoList.remove();
            for (Direction direction : Direction.values()){
                BlockPos investigating = current.offset(direction);
                if (investigated.contains(investigating)) {
                    continue;
                }
                if (CableManager.getInstance().pipeExists(world, investigating, PipeType.NETWORK)) {
                    todoList.add(investigating);
                }
                if (world.getBlockEntity(investigating) instanceof NetworkReceiver receiver && isntDuplicate(networkDevices, receiver)){
                    networkDevices.add(receiver);
                }
                    investigated.add(investigating);
                }
            }
            return networkDevices;
        }

    @Override
    public ComputerScreenInitPayload getScreenOpeningData(ServerPlayerEntity player) {
        return new ComputerScreenInitPayload(graphics, computer.getUuid());
    }

    @Override
    public boolean shouldRenderPipeType(PipeType type) {
        return type == PipeType.PERIPHERAL || type == PipeType.NETWORK;
    }

    @Override
    public BinaryGraphicsArray getBinaryGraphics() {
        return getComputer().getBinaryGraphics();
    }

    @Override
    public void setBinaryGraphics(BinaryGraphicsArray graphicsArray) {
        computer.setBinaryGraphics(graphicsArray);
    }

    @Override
    public Vec3i getColoration(float clock, int x, int y) {
        int r = 40;
        int g = 226;
        int b = 50;
        if ((x+2)%3==0){
            r++;
            g += 8;
            b += 3;
        }
        if ((y+1)%2==0){
            r++;
            g += 10;
            b += 3;
        }
        float density = 0.4f;
        float offset = (clock + y * density) % 6.2f;
        double weight = 8;
        double effect = Math.sin(offset) * weight;
        r += (int)Math.round(effect);
        g += (int)Math.round(effect);
        b += (int)Math.round(effect);
        return switch (world.getBlockState(getPos()).get(ComputerBlock.STATE)) {
            default -> {
                if ((y%4>1 || x%4>1) && !(y%4>1 && x%4>1)) yield new Vec3i(201, 109, 233);
                yield new Vec3i(0, 0, 0);
            }
            case 1 -> new Vec3i(r,g,b);
            case 2 -> new Vec3i(g, r, b);
            case 3 -> new Vec3i(b, r, g);
        };
    }

    @Override
    public boolean canRender() {
        return world!=null && !world.getBlockState(getPos()).isAir() && world.getBlockState(getPos()).get(ComputerBlock.STATE)!=0;
    }

    @Override
    public String[] getFunctionNames() {
        return new String[]{"shutdown", "startup", "getId", "getMachine", "isOn"};
    }

    @Override
    public Value<?> callFunction(Runtime runtime, String name, Value<?>... Args) {
        if (computer==null) return Value.asError("computer not loaded");
        if (Objects.equals(name, "shutdown")){
            Optional<String> test = Parameters.empty().canCast(Args, runtime==null ? null : runtime.getThread().getLang());
            if (!test.isEmpty()) return Value.asError(test.get());
            computer.stop();
            return Value.NULL;
        }
        if (Objects.equals(name, "startup")){
            Optional<String> test = Parameters.empty().canCast(Args, runtime==null ? null : runtime.getThread().getLang());
            if (!test.isEmpty()) return Value.asError(test.get());
            computer.start();
            return Value.NULL;
        }
        if (Objects.equals(name, "getId")){
            Optional<String> test = Parameters.empty().canCast(Args, runtime==null ? null : runtime.getThread().getLang());
            if (!test.isEmpty()) return Value.asError(test.get());
            return Value.of(computer.getUuid().toString());
        }
        if (Objects.equals(name, "getMachine")){
            Optional<String> test = Parameters.empty().canCast(Args, runtime==null ? null : runtime.getThread().getLang());
            if (!test.isEmpty()) return Value.asError(test.get());
            return Value.of(computer.getConfiguration().modelName());
        }
        if (Objects.equals(name, "isOn")){
            Optional<String> test = Parameters.empty().canCast(Args, runtime==null ? null : runtime.getThread().getLang());
            if (!test.isEmpty()) return Value.asError(test.get());
            return Value.of(computer.getStatus()== ComputerState.ON);
        }
        return Value.asError("Cant Find Function '"+name+"'");
    }

    @Override
    public String getTypeName() {
        return "neetcomputers:computer";
    }

    @Override
    public UUID getUuid() {
        return computer.getUuid();
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
    public void receiveNetwork(Value<?>... args) {
        computer.queueEvent(new EventGeneric("networkMessage", args), EventLabel.NETWORK);
    }
}
