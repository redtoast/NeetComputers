package com.redtoast.simulation.base;

import com.redtoast.neet.NeetComputersServer;
import com.redtoast.simulation.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public abstract class LangThread {
    private static Logger logger = LoggerFactory.getLogger("NeetComputers: Threads");
    private int javaLag = 0;
    private boolean killed = false;
    protected String errorMessage = null;
    private UUID uuid;
    public LangThread(){
        uuid = UUID.randomUUID();
    }
    public void log(String message){
        if (NeetComputersServer.DO_LOGGING) logger.info(message);
    }
    public static void error(String message){
        if (NeetComputersServer.DO_LOGGING) logger.warn(message);
    }
    public void kill(String message){
        errorMessage = message;
        killed = true;
    }
    public void kill(){
        killed = true;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public UUID getUuid(){
        return uuid;
    }
    public boolean isAlive(){return !killed;}
    public abstract LanguageGeneric getLang();
    public abstract void yield();
    public abstract void tick();
    public abstract String getSource();
    public abstract void insert(String key, Value value);
    public void taxJavaLag(short lagTime){
        javaLag += lagTime;
    }
    public int getJavaTaxBulk(short batch){
        int i = javaLag / batch;
        javaLag %= batch;
        return i;
    }
    public void clearJavaLag(){
        javaLag=0;
    }
}