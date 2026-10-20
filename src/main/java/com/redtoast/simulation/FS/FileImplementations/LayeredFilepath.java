package com.redtoast.simulation.FS.FileImplementations;

import com.redtoast.simulation.FS.DiskSystem;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

public class LayeredFilepath implements Filepath {
    private final RealFilepath mainFilepath;
    private final DataFilepath layoverFilepath;
    private final DiskSystem fs;
    public LayeredFilepath(RealFilepath mainFilepath, DataFilepath layoverFilepath, DiskSystem fs) {
        this.layoverFilepath = layoverFilepath;
        this.mainFilepath = mainFilepath;
        this.fs = fs;
        if (mainFilepath.exists()) {
            fs.blacklist.remove(getPath());
            fs.saveBlacklist();
        }
    }

    @Override
    public boolean isInvalid() {
        return layoverFilepath.isInvalid();
    }

    @Override
    public String getName() {
        return layoverFilepath.getName();
    }

    @Override
    public boolean canRead() {
        return mainFilepath.exists() ? mainFilepath.canRead() : layoverFilepath.canRead();
    }

    @Override
    public boolean canWrite() {
        return mainFilepath.canWrite();
    }

    @Override
    public boolean exists() {
        if (isInvalid()) return false;
        if (fs.blacklist.contains(getPath())) return false;
        return layoverFilepath.exists() || mainFilepath.exists();
    }

    @Override
    public boolean isDirectory() {
        return mainFilepath.exists() ? mainFilepath.isDirectory() : layoverFilepath.isDirectory();
    }

    @Override
    public boolean isFile() {
        return mainFilepath.exists() ? mainFilepath.isFile() : layoverFilepath.isFile();
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (exists()) return false;
        fs.blacklist.remove(getPath());
        fs.saveBlacklist();
        return mainFilepath.createNewFile();
    }

    @Override
    public String readAll() throws IOException {
        return mainFilepath.exists() ? mainFilepath.readAll() : layoverFilepath.readAll();
    }

    @Override
    public boolean write(byte[] bytes) throws IOException {
        if (!mainFilepath.exists()) mainFilepath.createNewFile();
        return mainFilepath.write(bytes);
    }

    @Override
    public boolean append(byte[] bytes) throws IOException {
        if (isInvalid()) throw new IOException("Invalid file path");
        if (mainFilepath.exists()){
            return mainFilepath.append(bytes);
        }else{
            boolean result = mainFilepath.writeBinary(layoverFilepath.readAllBinary());
            if (result){
                return mainFilepath.append(bytes);
            }else{
                return false;
            }
        }
    }

    @Override
    public byte[] readAllBinary() throws IOException {
        return mainFilepath.exists() ? mainFilepath.readAllBinary() : layoverFilepath.readAllBinary();
    }

    @Override
    public boolean writeBinary(byte[] bytes) throws IOException {
        if (!mainFilepath.exists()) mainFilepath.createNewFile();
        return mainFilepath.writeBinary(bytes);
    }

    @Override
    public boolean appendBinary(byte[] bytes) throws IOException {
        if (isInvalid()) throw new IOException("Invalid file path");
        if (mainFilepath.exists()){
            return mainFilepath.appendBinary(bytes);
        }else{
            boolean result = mainFilepath.writeBinary(layoverFilepath.readAllBinary());
            if (result){
                return mainFilepath.appendBinary(bytes);
            }else{
                return false;
            }
        }
    }

    @Override
    public boolean delete() throws IOException {
        if (isInvalid()) throw new IOException("Invalid file path");
        if (fs.blacklist.contains(getPath())) return false;
        if (mainFilepath.exists()) {
            boolean check = mainFilepath.delete();
            if (!check) return false;
        }
        fs.blacklist.add(getPath());
        fs.saveBlacklist();
        return true;
    }

    @Override
    public String[] listFiles() throws IOException {
        if (!mainFilepath.exists()) return layoverFilepath.listFiles();
        String[] layoverFiles = layoverFilepath.listFiles();
        String[] mainFiles = mainFilepath.listFiles();
        LinkedList<String> files = new LinkedList<>(Arrays.asList(layoverFiles != null ? layoverFiles : new String[0]));
        for (String file : (mainFiles != null ? mainFiles : new String[0])){
            if (!files.contains(file) && !fs.blacklist.contains(file)) files.add(file);
        }
        return files.toArray(new String[]{});
    }

    @Override
    public boolean mkdirs() throws IOException {
        return mainFilepath.mkdirs();
    }

    @Override
    public String getPath() {
        return layoverFilepath.getPath();
    }

    @Override
    public boolean equals(Object obj){
        if (obj instanceof Filepath path){
            return getPath().equals(path.getPath());
        }
        return super.equals(obj);
    }
}
