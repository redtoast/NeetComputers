package com.redtoast.simulation;

import com.redtoast.Computer;
import com.redtoast.simulation.FS.*;
import com.redtoast.simulation.base.LangThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * represents the code execution of a computer, and ticks on the computer ticking thread
 */
public class Runtime {
    //resources
    private final ComputerFileSystem fs;
    private final Computer parent;
    private LangThread thread = null;
    private RuntimeThread management;
    public APILoader loader = null;

    //state info
    private volatile boolean inTick = false;
    private boolean kill = false;
    private boolean wantaTick = false;

    private volatile boolean killFlag = false;

    public Runtime(Computer Parent){
        fs = Parent.getFileSystem();
        parent = Parent;
        management = new RuntimeThread(this);
        management.setPriority(10);
        management.start();
    }

    public boolean isInTick() {
        return inTick;
    }

    /**
        Fetch this runtime's file access
     */
    public ComputerFileSystem getFileSpace(){
        return fs;
    }

    /**
        Fetch the parent computer running this process
     */
    public Computer getParent() {
        return parent;
    }

    /**
     * Creates the runtimes initial thread, automatically ran by computer parent class
     */
    public void load(){
        DiskSystem diskSystem = fs.getHomeDisk();
        try{
            thread = diskSystem.getLanguage().createThread(diskSystem.getEntrypoint().readAll(), this, parent, parent.getConfiguration());
            if (!thread.isAlive()) {
                if (thread.getErrorMessage()==null) {
                    parent.stop();
                }else{
                    parent.crash(thread.getErrorMessage());
                }
            }
        }catch (Throwable e){
            if (e instanceof DiskError){
                parent.crash(e.getMessage());
            }else{
                APILoader.printJavaError(e);
                parent.crash("Unknown failure during boot (check logs)");
            }
            kill=true;
            management.kill();
            management = null;
        }
    }

    @ApiStatus.Internal
    public boolean shouldDie(){
        boolean temp = killFlag;
        if (temp) killFlag = false;
        return temp;
    }

    @ApiStatus.Internal
    public void instructTick(boolean state) {
        wantaTick = state;
        if (state) {
            management.signal();
        }
    }

    @ApiStatus.Internal
    public boolean wantsToTick() {
        return wantaTick;
    }

    public void releaseManagementThread() {
        if (management != null) {
            management.kill();
            management = null;
        }
    }

    public void renewManagementThread() {
        if (management != null) management.kill();
        management = new RuntimeThread(this);
    }

    public @Nullable RuntimeThread getManagementThread() {
        return management;
    }

    /**
     * ticks the process forward once and performs state maintenance
     */
    public void tick(){
        if (management==null) renewManagementThread();
        String errorMessage = null;
        if (!kill) {
            if (thread == null) {
                kill = true;
                releaseManagementThread();
                return;
            }
            if (thread.isAlive()) {
                inTick = true;
                try {
                    if (parent.isCrashed()) return;
                    thread.tick();
                } finally {
                    inTick = false;
                }
                if (!thread.isAlive()) {
                    errorMessage = thread.getErrorMessage();
                    if (errorMessage != null) errorMessage = errorMessage.replaceAll("\t", "    ");
                    thread = null;
                    //releaseManagementThread(); //load-bearing error, stops a mystery memory leak
                }
            }
        }
        if (shouldDie()){
            if (errorMessage==null) {
                parent.stop();
            }else{
                parent.crash(errorMessage);
            }
            releaseManagementThread();
        } else if (errorMessage!=null) {
            parent.crash(errorMessage);
            releaseManagementThread();
        }
    }

    /**
     * gets the source of the running thread for error readouts
     */
    public String getCurrentSource(){
        if (!inTick) return null;
        return thread.getSource();
    }

    /**
     * returns the state of the runtime
     * @return if the instance is dead or alive
     */
    public boolean isDead(){
        return kill;
    }

    /**
     * gets runtimes current thread, or null of thread if dead
     * @return LangThread instance or null
     */
    public @Nullable LangThread getThread() {
        return kill ? null : thread;
    }
}
