package com.redtoast.simulation.FS;

import com.redtoast.neet.BinaryLoader;

import java.util.UUID;

public class ComputerFileSystem extends DiskManager{
    private final DiskSystem homedisk;

    public ComputerFileSystem(int homePointer, String template, UUID computerID, String diskid) throws DiskError {
        super();
        homedisk = createDisk(homePointer, template, computerID, diskid);
        if (homedisk instanceof DiskSystem diskSystem && diskSystem.getBuild().language==null) {
            if (diskSystem.getBuild().isLua && BinaryLoader.loaded){
                throw new DiskError("Lua failed to load (check logs, it might be possible to download functionality for your machine)");
            }else throw new DiskError("Language does not exist");
        }
        if (!homedisk.isBootable()) throw new DiskError("System not bootable");
    }

    public DiskSystem getHomeDisk() {return homedisk;}

    @Override
    public boolean removeDisk(GenericSystem disk) {
        if (disk.equals(homedisk)) return false;
        return super.removeDisk(disk);
    }
}
