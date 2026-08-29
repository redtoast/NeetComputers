package com.redtoast.simulation;

import com.redtoast.simulation.base.LangThread;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class RuntimeThread extends Thread {
    private final Runtime runtime;
    private final CompletableFuture<Integer> block = new CompletableFuture<>();

    private static final LinkedList<RuntimeThread> threads = new LinkedList<>();

    public RuntimeThread(Runtime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void run(){
        threads.add(this);
        try{
            while (true) {
                try {
                    if (block.get(3, TimeUnit.SECONDS)==1) break;
                    if (runtime.wantsToTick()) {
                        runtime.instructTick(false);
                        runtime.tick();
                    }
                } catch (TimeoutException e) {
                    if (!runtime.isDead() && !runtime.isInTick()) {
                        runtime.getThread().kill("Runtime timed out");
                    }
                    LangThread.error("Thread for computer "+runtime.getParent().getUuid()+" timed out!");
                    threads.remove(this);
                    break;
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void signal() {
        block.complete(0);
    }

    public void kill() {
        block.complete(1);
        threads.remove(this);
    }

    public static RuntimeThread getThread(Runtime runtime) {
        return threads.stream().filter(thread -> thread.equals(runtime)).findFirst().orElse(null);
    }

    public static void interatate(Consumer<RuntimeThread> consumer){
        threads.forEach(consumer);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RuntimeThread thread) return thread.runtime.getParent().equals(runtime.getParent());
        if (obj instanceof Runtime runtime) return runtime.getParent().equals(this.runtime.getParent());
        return false;
    }
}
