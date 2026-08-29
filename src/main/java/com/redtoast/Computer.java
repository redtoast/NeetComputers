package com.redtoast;

import com.redtoast.Compat.ComputerWrapper;
import com.redtoast.Connections.PeripheralProvider;
import com.redtoast.blocks.ComputerDataComponent;
import com.redtoast.blocks.Generics.Displays.BinaryGraphicsProvider;
import com.redtoast.graphics.BinaryGraphicsArray;
import com.redtoast.graphics.screens.RGBScreenHandler;
import com.redtoast.graphics.RGBGraphicsArray;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.neet.Networking.CloseRGBPayload;
import com.redtoast.neet.Networking.RGBComputerPayload;
import com.redtoast.simulation.*;
import com.redtoast.simulation.FS.ComputerFileSystem;
import com.redtoast.simulation.FS.DiskError;
import com.redtoast.simulation.FS.DiskSystem;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.config.ComputerConfig;
import com.redtoast.simulation.events.EventGeneric;
import com.redtoast.simulation.events.EventLabel;
import com.redtoast.simulation.events.EventManager;
import com.redtoast.simulation.value.Value;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * the {@link Computer} class represents the entirty of a NeetComputers computer and its subclasses.
 * <p>
 *     The {@link Computer} class has multiple steps required before it can start ticking
 * </p>
 *
 * <p>
 *     First initialize the computer instance, provide a {@link ComputerConfig} instance and implement abstract methods
 *     you can think of this as creating the <i>model</i> that the computer with use
 * </p>
 *
 * <p>
 *     then run {@code computer.load(params)}, there are multiple load function that load the computer differently, but this stage is critical
 *     to give the computer the data it needs to operate
 * </p>
 *
 * <p>
 *     from there you can use {@code computer.start()} or {@code computer.stop()} to control the computers state, and progress the computer using
 *     {@code computer.tick()}
 * </p>
 *
 * @see ComputerConfig
 * @see Runtime
 * @see ComputerFileSystem
 * @see DiskSystem
 * @see InternetManager
 */
public abstract class Computer implements BinaryGraphicsProvider {
    //the instance representing a computers runtime, cycles with computer restarts
    private Runtime runtime;
    //pointer for the folder that contains this computer's files
    private int pointer = 0;
    //determines if a 'load' function has been called, providing important information to computer, most methods won't run if this is false
    private boolean loaded = false;
    //stores the computers state
    private ComputerState state = ComputerState.OFF;
    private String crashMessage = null;
    //uuid representing the computer, acquired by chip.getUUID() in runtime. generated during loading
    private UUID uuid = null;
    //stores the default configuration for the file system
    private String template = "neetos";
    //marks if the computer should attempt to restart
    private boolean doReboot = false;
    //object representing the computers file interpreter
    private ComputerFileSystem fileSystem = null;
    //object that handles the computers events
    private final EventManager eventManager = new EventManager();
    //object that handles incoming and outgoing internet traffic
    private final InternetManager internetManager = new InternetManager(eventManager, this);
    //object representing the graphics render seen on some computer blocks/entity's
    private BinaryGraphicsArray BinGraphics;
    private final boolean doesBinaryGraphics;
    //increments every tick, loops back at 100
    private short clock = 0;
    private int timeExecuted = 0;
    //object representing colored graphics (gui)
    private final RGBGraphicsArray Graphics;
    private boolean graphicsDirty = false;
    //specify computer specifications
    private final ComputerConfig computerConfig;
    //list of cc computer accesses to remove later if needed (computer breaks, etc) (CC COMPAT)
    public final ArrayList<ComputerWrapper> computerAccesses;
    //value holding last time computer ticked
    private long tickTime;

    //abstract methods
    /**
     * commands parent object to register a saved nbt (mostly useful in blocks)
     * typically you should have this call markDirty() if it's a blockEntity parent or be otherwise trigger NBT saving
     */
    public abstract void saveNBT();

    /**
     * gets a World object from parent
     * @return World
     */
    public abstract boolean isClient();
    /**
     * triggers a reloading of the drawing of binary graphics
     * imagine this as say sending the updated graphics array to all clients to be rendered
     */
    public abstract void refreshBinaryGraphics();

    /**
     * this function should return whatever class is parent to the computer instance,
     * the output of this class is never used internally and is only meant for modders.
     * @return Object or null
     */
    public abstract @Nullable Object getParentEntity();

    public abstract World getWorld();

    public abstract int getLunarTime();

    //constructor
    public Computer(ComputerConfig computerConfig){
        Graphics = new RGBGraphicsArray(computerConfig.ColorGraphicsSize().x(), computerConfig.ColorGraphicsSize().y());
        this.computerConfig = computerConfig;
        doesBinaryGraphics = computerConfig.doesBinaryGraphics();
        BinGraphics = computerConfig.doesBinaryGraphics() ? new BinaryGraphicsArray(computerConfig.BinaryGraphicsSize().x(), computerConfig.BinaryGraphicsSize().y()) : null;
        tickTime = System.currentTimeMillis();
        this.computerAccesses = new ArrayList<>();
    }

    //Generics load function all other load functions call after implementing data
    private void load(){
        loaded = true;
        if (uuid==null) uuid = UUID.randomUUID();
        if (doesBinaryGraphics) refreshBinaryGraphics();
        maintainState();
    }
    //loads computer from NBT data
    public void load(NbtCompound nbt){
        if (!loaded){
            pointer = nbt.getInt("Address");
            state = ComputerState.OFF;
            if (nbt.contains("State")){
                state = ComputerState.values()[nbt.getShort("State")];
            }
            if (nbt.contains("ComputerID")){
                uuid = nbt.getUuid("ComputerID");
            }else{
                uuid = UUID.randomUUID();
            }
            if (nbt.contains("Template")) {
                template = nbt.getString("Template");
            }
            if (nbt.contains("crashMessage") && state == ComputerState.CRASHED) crashMessage = nbt.getString("crashMessage");
            load();
        }
    }
    //loads computer from data component
    public void load(ComputerDataComponent dataComponent){
        if (!loaded){
            pointer = dataComponent.address();
            state = dataComponent.isOn() ? ComputerState.ON : ComputerState.OFF;
            uuid = dataComponent.id();
            template = dataComponent.template();
            load();
        }
    }
    //generates a new computer from scratch
    public void load(MinecraftServer GameServer){
        if (!loaded){
            assert GameServer != null;
            pointer = NeetComputersServer.getNextPointer();
            state = ComputerState.OFF;
            load();
        }
    }

    /*'starts' the computer if its off, does nothing if its on
     * starts referring to building a new Runtime instance and marking its state as on
     */
    public void start(){
        if (loaded && fileSystem!=null && (state == ComputerState.OFF || state == ComputerState.PAUSED)){
            state = ComputerState.ON;
            doReboot = false;
            maintainState();
        }
    }

    //marks computer as off and overrides the runtime with null
    public void stop(){
        if (loaded && state != ComputerState.OFF){
            state = ComputerState.OFF;
            this.yield();
            maintainState();
            for (PlayerEntity p : getWorld().getPlayers())
                if (p.currentScreenHandler instanceof RGBScreenHandler g && g.comp == this)
                    ServerPlayNetworking.send((ServerPlayerEntity) p, new CloseRGBPayload());
        }
    }

    //marks computer as off
    public void pause(){
        if (loaded && fileSystem!=null && state != ComputerState.PAUSED){
            state = ComputerState.PAUSED;
            maintainState();
        }
    }

    //sets the computer to a crashed state
    public void crash(String message){
        if (loaded && state != ComputerState.CRASHED){
            state = ComputerState.CRASHED;
            if (runtime==null || runtime.isDead()){
                crashMessage = message;
            }else{
                crashMessage = runtime.getCurrentSource() == null ? message : runtime.getCurrentSource() + " " + message;
            }
            this.yield();
            maintainState();
            for (PlayerEntity p : getWorld().getPlayers())
                if (p.currentScreenHandler instanceof RGBScreenHandler g && g.comp == this)
                    ServerPlayNetworking.send((ServerPlayerEntity) p, new CloseRGBPayload());
        }
    }

    //re-initializes the runtime and sets the computer to be on
    public void reboot(){
        if (loaded && fileSystem!=null){
            state = ComputerState.OFF;
            this.yield();
            maintainState();
            doReboot = true;
        }
    }

    //yields the computer
    public void yield(){
        if (runtime!=null && runtime.isInTick() && runtime.getThread()!=null) runtime.getThread().yield();
    }

    //one line fetch methods
    public boolean isOn(){return state == ComputerState.ON;}
    public boolean isDead(){return state == ComputerState.OFF;}
    public boolean isCrashed(){return state == ComputerState.CRASHED;}
    public boolean isPaused(){return state == ComputerState.PAUSED;}
    public String getCrashMessage(){return isCrashed() ? crashMessage : null;}
    public boolean isLoaded(){return loaded;}
    public boolean hasBinaryGraphics() {return doesBinaryGraphics;}
    public DiskSystem getHomeDiskSystem() {return fileSystem.getHomeDisk();}
    public ComputerFileSystem getFileSystem() {return fileSystem;}
    public EventManager getEventManager() {return eventManager;}
    public InternetManager getInternetManager() {return internetManager;}
    public RGBGraphicsArray getGraphics() {
        return Graphics;
    }
    public @Nullable Runtime getRuntime() {
        return runtime;
    }
    public UUID getUuid() {return uuid;}
    public int getTimeExecuted() {return state==ComputerState.ON ? timeExecuted : 0;}
    public ComputerConfig getConfiguration(){
        return computerConfig;
    }
    public ComputerState getStatus(){
        return state;
    }
    public @Nullable BinaryGraphicsArray getBinaryGraphics() {
        return !doesBinaryGraphics ? null : BinGraphics;
    }

    public void renderColorGraphics(){graphicsDirty = true;}

    //set methods
    public void setBinaryGraphics(BinaryGraphicsArray graphics) {
        if (!doesBinaryGraphics) return;
        BinGraphics = graphics;
        refreshBinaryGraphics();
    }

    //ticks the computer
    public void tick(World world){
        short delta = (short) (System.currentTimeMillis() - tickTime);
        tickTime = System.currentTimeMillis();
        if (loaded){
            if (NeetComputersServer.worldPath!=null && fileSystem==null){
                try {
                    fileSystem = new ComputerFileSystem(pointer, template, getUuid());
                }catch (DiskError e) {
                    crash("Failed to load file system: "+e.getMessage());
                }
            }
            maintainState();
            if (fileSystem!=null && runtime!=null && !runtime.isDead() && state == ComputerState.ON) {
                //instruct the computer to be ticked
                timeExecuted += delta;
                runtime.instructTick(true);
                fileSystem.update();
            }
            if (state == ComputerState.ON)
                internetManager.progress(delta / 1000d);
            if (graphicsDirty && state == ComputerState.ON && clock%2==0) {
                ArrayList<PlayerEntity> players = new ArrayList<>();
                for (PlayerEntity p : world.getPlayers()) if (p.currentScreenHandler instanceof RGBScreenHandler g && g.comp == this) players.add(p);
                if (!players.isEmpty()){
                    RGBComputerPayload payload = new RGBComputerPayload(getGraphics());
                    for (PlayerEntity p : players) ServerPlayNetworking.send((ServerPlayerEntity) p, payload);
                }
                graphicsDirty = false;
            }
            if (clock%10==0 && doesBinaryGraphics) refreshBinaryGraphics();
            if (clock%30==0) saveNBT();
            clock += 1;
            clock %= 100;
        }
    }

    //gets a list of all peripheral providers on the system
    public abstract List<PeripheralProvider> getPeripheralProviders();
    public abstract void sendNetworkMessage(Value<?>... args);

    //queues an event to the event manager
    public void queueEvent(EventGeneric event, EventLabel queue){
        if (state == ComputerState.ON) eventManager.queueEvent(event, queue);
    }

    //writes current state to NBT tag
    public NbtCompound saveNBT(NbtCompound nbt){
        nbt.putInt("Address", pointer);
        nbt.putShort("State", (short) state.ordinal());
        nbt.putString("Template", template);
        if (uuid!=null) nbt.putUuid("ComputerID",uuid);
        if (isCrashed() && crashMessage!=null) nbt.putString("crashMessage", crashMessage);
        return nbt;
    }

    //writes current state to item
    public ComputerDataComponent saveToItem(){
        return new ComputerDataComponent(
                pointer,
                isOn(),
                uuid,
                template
        );
    }

    //maintenance function that detects a difference in the computers state and its actual state and corrects it
    private void maintainState(){
        boolean save = false;
        if (doReboot) {
            start();
        }
        if (state != ComputerState.ON && state != ComputerState.PAUSED) {
            graphicsDirty = false;
            timeExecuted = 0;
        }
        if (state != ComputerState.CRASHED && crashMessage != null) {
            crashMessage = null;
            save = true;
        }
        if (state == ComputerState.ON && runtime==null && fileSystem!=null) {
            if (doesBinaryGraphics){
                for (int x = 0; x < BinGraphics.getSize().x; x++){
                    for (int y = 0; y < BinGraphics.getSize().y; y++){
                        BinGraphics.set(x,y,false);
                    }
                }
            }

            Graphics.clear();
            internetManager.reset();
            eventManager.reset();

            runtime = new Runtime(this);

            new APILoader(this);
            runtime.load();
            save = true;
        }
        if (state == ComputerState.ON && runtime!=null && runtime.isDead()) {
            state = ComputerState.OFF;
            if (doesBinaryGraphics){
                for (int x = 0; x < BinGraphics.getSize().x; x++){
                    for (int y = 0; y < BinGraphics.getSize().y; y++){
                        BinGraphics.set(x,y,false);
                    }
                }
            }

            Graphics.clear();
            internetManager.reset();
            eventManager.reset();
            save = true;
        }
        if ((state == ComputerState.OFF || state == ComputerState.CRASHED) && runtime!=null) {
            internetManager.reset();
            eventManager.reset();
            runtime.getManagementThread().kill();
            runtime=null;
            save = true;
        }
        if (state == ComputerState.CRASHED && crashMessage == null) {
            crashMessage = "Unknown error [No Message Provided]";
            save = true;
        }
        if (fileSystem!=null) fileSystem.update();
        if (save) saveNBT();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Computer computer) return computer.getUuid().equals(getUuid());
        return false;
    }
}
