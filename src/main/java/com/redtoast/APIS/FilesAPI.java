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
import com.redtoast.simulation.value.ValueTypes.Tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilesAPI implements API {
    private final DiskManager diskManager;
    private final Computer parent;

    public FilesAPI(Computer computer) {
        diskManager = computer.getFileSystem();
        parent = computer;
    }

    public GenericSystem getDisk(Integer disk) {
        if (disk==null) return diskManager.getDisk(0);
        if (!diskManager.diskExists(disk)) throw new ExposedError("Disk not found");
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

    private Table openFile(Filepath filepath, GenericSystem diskSystem, OpeningMode openingMode){
        if (filepath.isDirectory()) throw new ExposedError("Not a file");
        if (openingMode.invalid()) throw new ExposedError("Invalid open mode");
        if (!filepath.exists() && !openingMode.create()) throw new ExposedError("Not a file");
        if (openingMode.canRead() && !filepath.canRead()) throw new ExposedError("Access denied");
        if (openingMode.canWrite() && !filepath.canWrite()) throw new ExposedError("Access denied");
        return APILoader.TableizeAPI(new FileHeader(filepath, diskSystem, openingMode), parent.getRuntime());
    }

    @Exposed
    public Table open(String path, @CanBeNull String mode, @CanBeNull Object flagsOrDisk, @CanBeNull Integer disk){
        Table flags = null;
        Integer resolvedDisk = disk;
        if (flagsOrDisk instanceof Table table){
            flags = table;
        }else if (flagsOrDisk != null){
            if (!(flagsOrDisk instanceof Integer) && !(flagsOrDisk instanceof Double) && !(flagsOrDisk instanceof Float))
                throw new ExposedError("Invalid open flags");
            resolvedDisk = ((Number) flagsOrDisk).intValue();
        }

        GenericSystem diskSystem = getDisk(resolvedDisk);
        OpeningMode openingMode = FileHelper.getMode(mode==null ? "r" : mode);
        if (openingMode.invalid()) throw new ExposedError("Invalid open mode");
        Filepath filepath = diskSystem.getFile(path);

        if (flags == null) return openFile(filepath, diskSystem, openingMode);

        boolean exclusive = false;
        boolean follow = true;
        boolean directory = false;
        boolean pathOnly = false;
        Value<?> option = flags.get("exclusive");
        exclusive = !option.isNull() && Boolean.TRUE.equals(option.toBool());
        option = flags.get("follow");
        follow = option.isNull() || Boolean.TRUE.equals(option.toBool());
        option = flags.get("directory");
        directory = !option.isNull() && Boolean.TRUE.equals(option.toBool());
        option = flags.get("path");
        pathOnly = !option.isNull() && Boolean.TRUE.equals(option.toBool());

        if (directory){
            if (!filepath.isDirectory()) throw new ExposedError("Not a directory");
            throw new ExposedError("Not a file");
        }
        if (!follow){
            Path host = resolveHost(diskSystem, path);
            if (host != null && Files.isSymbolicLink(host)) throw new ExposedError("Operation not supported");
        }
        if (pathOnly && !filepath.exists()) throw new ExposedError("Not a file");
        if (exclusive){
            if (filepath.exists()) throw new ExposedError("File exists");
            try{
                filepath.createNewFile();
            }catch (IOException e){
                throw new ExposedError(e.getMessage());
            }
        }
        return openFile(filepath, diskSystem, openingMode);
    }

    private Path partitionRoot(GenericSystem diskSystem, @CanBeNull String partition){
        if (!(diskSystem instanceof DiskSystem disk)) return null;
        if (partition == null || partition.isBlank()) return disk.basePath;
        if (partition.indexOf('/') != -1 || partition.indexOf('\\') != -1 || partition.contains("..")) return null;
        return disk.basePath.resolve(partition);
    }

    private Path resolveHost(GenericSystem diskSystem, String path){
        if (!(diskSystem instanceof DiskSystem disk)) return null;
        String normalized = FileHelper.normalize(path);
        if (diskSystem.getPartitions().size() == 1 && normalized.indexOf(':') == -1){
            normalized = diskSystem.getPartitions().get(0).path() + ':' + normalized;
        }
        if (!FileHelper.validatePathStatic(normalized)) return null;
        int colon = normalized.indexOf(':');
        if (colon < 0) return null;
        String partition = normalized.substring(0, colon);
        if (!diskSystem.partitionExists(partition)) return null;
        Path root = disk.basePath.resolve(partition);
        String relative = normalized.substring(colon + 1).replace('\\', '/');
        while (relative.startsWith("/")) relative = relative.substring(1);
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) return null;
        return resolved;
    }

    @Exposed
    public Table fsSize(String path, @CanBeNull Integer disk){
        GenericSystem diskSystem = getDisk(disk);
        Path host = resolveHost(diskSystem, path);
        if (host == null) throw new ExposedError("Invalid file path");
        if (!Files.exists(host)) throw new ExposedError("File does not exist");
        Table table = new Table();
        table.put("total", Value.of((double) diskSystem.getCapacity()));
        table.put("free", Value.of((double) diskSystem.getFreeSpace()));
        return table;
    }

    private Tuple partitionBytes(@CanBeNull Integer disk, @CanBeNull String partition, boolean total){
        GenericSystem diskSystem = getDisk(disk);
        Path root = partitionRoot(diskSystem, partition);
        if (root == null) return new Tuple(Value.NULL, Value.of("invalid partition"));
        if (!Files.isDirectory(root)) return new Tuple(Value.NULL, Value.of("no such partition"));
        double bytes = total ? diskSystem.getCapacity() : diskSystem.getFreeSpace();
        return new Tuple(Value.of(bytes));
    }

    @Exposed
    public Tuple getCapacity(@CanBeNull Integer disk, @CanBeNull String partition){
        return partitionBytes(disk, partition, true);
    }

    @Exposed
    public Tuple getFreeSpace(@CanBeNull Integer disk, @CanBeNull String partition){
        return partitionBytes(disk, partition, false);
    }

    @Exposed
    public boolean truncate(String path, int size, @CanBeNull Integer disk){
        if (size < 0) throw new ExposedError("Invalid size");
        GenericSystem diskSystem = getDisk(disk);
        Filepath filepath = diskSystem.getFile(path);
        if (filepath.isInvalid()) throw new ExposedError("Invalid file path");
        if (!filepath.exists()) throw new ExposedError("File does not exist");
        if (!filepath.isFile()) throw new ExposedError("Not a file");
        if (!filepath.canWrite()) throw new ExposedError("Access denied");
        try{
            byte[] current = filepath.readAllBinary();
            int existing = current == null ? 0 : current.length;
            if (size > existing && !diskSystem.hasSpaceFor(size - existing)) throw new ExposedError("Disk full");
            byte[] resized = new byte[size];
            if (current != null) System.arraycopy(current, 0, resized, 0, Math.min(current.length, size));
            if (!filepath.writeBinary(resized)) throw new ExposedError("truncate failed");
        }catch (IOException e){
            throw new ExposedError(e.getMessage());
        }
        return true;
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
    public String getDiskReaderID(int disk) {
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.getUuid().toString();
    }

    @Exposed
    public String getDiskID(@CanBeNull Integer disk) {
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.getDiskId();
    }

    @Exposed
    public boolean diskExists(int disk) {
        return diskManager.diskExists(disk);
    }

    @Exposed
    public boolean removeDisk(@CanBeNull Integer disk) {
        GenericSystem diskSystem = getDisk(disk);
        return diskManager.removeDisk(diskSystem);
    }

    @Exposed
    public String getBootPath(@CanBeNull Integer disk) {
        GenericSystem diskSystem = getDisk(disk);
        return diskSystem.isBootable() ? FileHelper.normalize(diskSystem.getEntrypointPath()) : null;
    }

    @Exposed
    public boolean setBoot(String entrypoint, @CanBeNull Integer disk) {
        Integer target = disk == null ? 0 : disk;
        GenericSystem diskSystem = getDisk(target);
        if (target==0 && !exists(entrypoint, 0)) {
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
