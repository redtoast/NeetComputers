package com.redtoast.APIS;

import com.redtoast.Computer;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.FS.*;
import com.redtoast.simulation.FS.FileImplementations.Filepath;
import com.redtoast.simulation.annotations.CanBeNull;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.List;
import com.redtoast.simulation.value.ValueTypes.Table;

public class FilesAPI implements API {
    private final DiskManager diskManager;
    private final Computer parent;

    public FilesAPI(Computer computer) {
        diskManager = computer.getFileSystem();
        parent = computer;
    }

    public GenericSystem getDisk(Integer disk) {
        if (disk==null) return diskManager.getDisk(0);
        if (disk < 0 || disk >= diskManager.size()) throw new ExposedError("Disk not found");
        return diskManager.getDisk(disk);
    }

    @Exposed
    public List getPartitions(@CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        List partitionsStrings = new List();
        for (Partition partition : diskSystem.getPartitions()){
            partitionsStrings.add(Value.of(partition.path()));
        }
        return partitionsStrings;
    }

    @Exposed
    public Table getPartition(String name, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        Partition partition = diskSystem.getPartition(name);
        if (partition==null) return null;
        Table table = new Table();
        table.put("name", partition.path());
        table.put("readonly", Value.of(partition.readOnly()));
        table.put("hidden", Value.of(partition.hidden()));
        return table;
    }

    @Exposed
    public boolean createPartition(String name, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.createPartition(name);
    }

    @Exposed
    public boolean setPartitionHidden(String name, boolean state, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.setPartitionHidden(name, state);
    }

    @Exposed
    public boolean setPartitionReadOnly(String name, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.setPartitionReadOnly(name, true);
    }

    @Exposed
    public boolean deletePartition(String name, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.deletePartition(name);
    }

    @Exposed
    public Table open(String path, @CanBeNull String mode, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        OpeningMode openingMode = FileHelper.getMode(mode==null ? "r" : mode);
        Filepath filepath = diskSystem.getFile(path);
        if (filepath.isDirectory()) throw new ExposedError("Not a file");
        if (openingMode.invalid()) throw new ExposedError("Invalid open mode");
        if (!filepath.exists() && !openingMode.create()) throw new ExposedError("Not a file");
        if (openingMode.canRead() && !filepath.canRead()) throw new ExposedError("Access denied");
        if (openingMode.canWrite() && !filepath.canWrite()) throw new ExposedError("Access denied");
        return APILoader.TableizeAPI(new FileHeader(filepath, diskSystem, openingMode), parent.getRuntime());
    }

    @Exposed
    public List getChildren(String path, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.getChildren(path);
    }

    @Exposed
    public boolean makeDir(String path, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.makeDir(path);
    }

    @Exposed
    public boolean exists(String path, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.exists(path);
    }

    @Exposed
    public boolean isFile(String path, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.isFile(path);
    }

    @Exposed
    public boolean isDir(String path, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.isDir(path);
    }

    @Exposed
    public boolean delete(String path, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.delete(path);
    }

    @Exposed
    public int getNumberOfDisks() {
        return diskManager.size();
    }

    @Exposed
    public List getDisks(){
        List ls = new List();
        for (int disk : diskManager.diskNumbers()) ls.add(Value.of(disk));
        return ls;
    }

    @Exposed
    public String getDiskID(int disk) {
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.getUuid().toString();
    }

    @Exposed
    public boolean removeDisk(int disk) {
        GenericSystem diskSystem = getDisk(disk);
        return diskManager.removeDisk(diskSystem);
    }

    @Exposed
    public String getBootPath(int disk) {
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.isBootable() ? FileHelper.normalize(diskSystem.getEntrypointPath()) : null;
    }

    @Exposed
    public boolean setBoot(String entrypoint, int disk) {
        GenericSystem diskSystem = getDisk(disk);
        if (disk==0 && !exists(entrypoint, 0)) {
            return false;
        }
        try{
            diskSystem.makeBootable(entrypoint, parent.getHomeDiskSystem().getLanguage());
        } catch (DiskError e) {
            if (e.getMessage().equals("Disk not bootable")){
                parent.crash("Failed to load file system: Disk not bootable");
                return false;
            }else{
                throw new ExposedError(e.getMessage());
            }
        }
        return true;
    }

    @Exposed
    public boolean setBoot(int disk) {
        GenericSystem diskSystem = getDisk(disk);
        if (disk==0) {
            return false;
        }
        boolean buffer = diskSystem.isBootable();
        diskSystem.removeBootability();
        return buffer;
    }

    @Override
    public String getLabel() {
        return "files";
    }
}
