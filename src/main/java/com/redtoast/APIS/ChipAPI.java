package com.redtoast.APIS;

import com.redtoast.Computer;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Table;

public class ChipAPI implements API {
    Computer computer;
    Runtime vm;

    public ChipAPI(Computer parent) {
        computer = parent;
        vm = computer.getRuntime();
    }

    @Exposed
    public double getUnixTime(){
        return System.currentTimeMillis() / 1000d;
    }

    @Exposed
    public double getTime(){return computer.getTimeExecuted() / 1000d;}

    @Exposed
    public double getMonotonic(){
        return System.nanoTime() / 1_000_000_000d;
    }

    @Exposed
    public int getLunarTime(){return computer.getLunarTime();}

    @Exposed
    public String getUUID(){
        return computer.getUuid().toString();
    }

    @Exposed
    public String getMachine(){
        return computer.getConfiguration().modelName();
    }

    @Exposed
    public void shutdown(){
        computer.stop();
    }

    @Exposed
    public void reboot(){
        computer.reboot();
    }

    @Exposed
    public void crash(String message){computer.crash(message);}

    @Exposed
    public String version(){
        return NeetComputersServer.version;
    }

    @Exposed
    public boolean isViewed() {
        return computer.isBeingViewed();
    }

    @Exposed
    public Table getMemory() {
        java.lang.Runtime runtime = java.lang.Runtime.getRuntime();
        Table table = new Table();
        table.put("used", Value.of((double) (runtime.totalMemory() - runtime.freeMemory())));
        table.put("total", Value.of((double) runtime.maxMemory()));
        return table;
    }

    @Exposed
    public void sleep(double milliseconds) {
        if (milliseconds <= 0) return;
        long deadline = System.currentTimeMillis() + (long) Math.min(milliseconds, Integer.MAX_VALUE);
        try {
            while (true) {
                if (!computer.isOn() || computer.isCrashed()) return;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return;
                Thread.sleep(Math.min(remaining, 20L));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String getLabel() {
        return "chip";
    }

}
