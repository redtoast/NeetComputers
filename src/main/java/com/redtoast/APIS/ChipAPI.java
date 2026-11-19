package com.redtoast.APIS;

import com.redtoast.Computer;
import com.redtoast.graphics.screens.BoilerplateScreen;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.annotations.Index;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.Runtime;

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

    @Override
    public String getLabel() {
        return "chip";
    }

}
