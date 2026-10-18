package com.redtoast.simulation.FS;

import com.redtoast.neet.NeetComputersServer;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DiskManager {
    private final Map<Integer, GenericSystem> disks = new ConcurrentHashMap<>();

    public GenericSystem getDisk(int disk) {
        return disks.get(disk);
    }

    public boolean removeDisk(GenericSystem disk) {
        for (Map.Entry<Integer, GenericSystem> entry : disks.entrySet()) {
            if (entry.getValue().equals(disk)) {
                disks.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public boolean addDisk(GenericSystem disk) {
        boolean check = false;
        for (Map.Entry<Integer, GenericSystem> entry : disks.entrySet()) {
            if (entry.getValue().equals(disk)) {
                check = true;
                break;
            }
        }
        if (check){
            return false;
        }else{
            int place = 0;
            while (disks.containsKey(place)) {
                place++;
            }
            disks.put(place, disk);
            return true;
        }
    }

    public DiskSystem createDisk(int pointer, String template, UUID uuid) throws DiskError {
        Path path = NeetComputersServer.worldPath.resolve("neetcomputers").resolve(String.valueOf(pointer)).normalize();
        for (Map.Entry<Integer, GenericSystem> entry : disks.entrySet()) {
            if (entry.getValue().equals(path)) throw new DiskError("Disk already exists in system");
        }
        DiskSystem disk = new DiskSystem(pointer, template, uuid);
        addDisk(disk);
        return disk;
    }

    public int size(){
        return disks.size();
    }

    public int[] diskNumbers(){
        int[] arr = new int[size()];
        int place = 0;
        for (Map.Entry<Integer, GenericSystem> entry : disks.entrySet()) {
            arr[place] = entry.getKey();
            place++;
        }
        return arr;
    }

    public boolean diskExists(int disk) {
        return disks.containsKey(disk);
    }

    public void update(){
        for (Map.Entry<Integer, GenericSystem> entry : disks.entrySet()) entry.getValue().update();
    }
}
