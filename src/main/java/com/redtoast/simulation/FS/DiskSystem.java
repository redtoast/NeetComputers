package com.redtoast.simulation.FS;

import com.google.gson.JsonSyntaxException;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.simulation.FS.FileImplementations.Filepath;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.base.LanguageGeneric;
import com.redtoast.simulation.value.ValueTypes.List;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;
import java.util.stream.Stream;

public class DiskSystem implements GenericSystem {
    public final DiskTable build;
    public final Path basePath;
    public final LinkedList<String> blacklist = new LinkedList<>();
    public final UUID uuid;

    private boolean saveBuild = false;
    private boolean saveBlacklist = false;

    public DiskSystem(int pointer, String template, UUID uuid) throws DiskError {
        this.uuid = uuid;
        Path path = NeetComputersServer.worldPath.resolve("neetcomputers").resolve(String.valueOf(pointer));
        path = path.normalize();
        basePath = path;
        basePath.toFile().mkdir();
        File buildFile = basePath.resolve("build.json").toFile();
        if (!buildFile.exists()){
            try {
                FileWriter writer = new FileWriter(buildFile);
                BufferedReader reader = NeetComputersServer.datahandling.getResource(Identifier.of("neetcomputers", "neet/builds/"+template+".json")).get().getReader();
                reader.lines().forEach(str -> {
                    try {
                        writer.write(str);
                        writer.append('\n');
                    } catch (IOException e) {
                        throw new RuntimeException(new DiskError("Failed to copy build template"));
                    }
                });
                writer.close();
                reader.close();
            } catch (IOException e) {
                throw new DiskError("Failed to copy build template");
            } catch (RuntimeException e){
                if (e.getCause() instanceof DiskError error) throw error;
                throw e;
            }
        }
        try{
            build = new DiskTable(Files.readString(buildFile.toPath()));
        }catch (IOException e) {
            throw new DiskError("Failed to read build");
        }catch (JsonSyntaxException e) {
            throw new DiskError(e.getMessage());
        }
        File exclusionsFile = basePath.resolve("exclusions.txt").toFile();
        if (exclusionsFile.exists()) {
            try (Stream<String> strings = Files.lines(exclusionsFile.toPath())) {
                strings.forEach(blacklist::add);
            } catch (IOException e) {
                throw new DiskError("Failed to read file path exclusions");
            }
        }
        for (Partition partition : build.partitions){
            basePath.resolve(partition.path()).toFile().mkdir();
        }
    }

    public void saveBuild() {saveBuild = true;}
    public void saveBlacklist() {saveBlacklist = true;}

    public DiskTable getBuild() {return build;}

    @Override
    public void update() {
        if (saveBuild) {
            try (FileWriter writer = new FileWriter(basePath.resolve("build.json").toFile())){
                writer.write(build.toJson());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            saveBuild = false;
        }
        if (saveBlacklist) {
            if (blacklist.isEmpty()) {
                basePath.resolve("exclusions.txt").toFile().delete();
            }else{
                boolean addNL = false;
                try (FileWriter writer = new FileWriter(basePath.resolve("exclusions.txt").toFile())){
                    for (String item : blacklist) {
                        if (addNL) writer.append('\n');
                        writer.write(item);
                        addNL = true;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            saveBlacklist = false;
        }
    }

    @Override
    public Filepath getFile(String path){
        return FileHelper.getFile(this, path);
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public boolean partitionExists(String path){
        for (Partition partition : build.partitions){
            if (partition.path().equalsIgnoreCase(path)){
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable Partition getPartition(String path){
        for (Partition partition : build.partitions){
            if (partition.path().equalsIgnoreCase(path)){
                return partition;
            }
        }
        return null;
    }

    @Override
    public boolean createPartition(String name){
        boolean marker = FileHelper.validatePathStatic(name+":\\");
        if (!marker) return false;
        if (partitionExists(name)) return false;
        basePath.resolve(name).toFile().mkdir();
        build.partitions.add(new Partition(name, false, false, ""));
        saveBuild();
        return true;
    }

    @Override
    public boolean setPartitionHidden(String name, boolean state){
        for (int i = 0; i < build.partitions.size(); i++){
            if (build.partitions.get(i).path().equalsIgnoreCase(name)){
                build.partitions.set(i, new Partition(name, build.partitions.get(i).readOnly(), state, build.partitions.get(i).source()));
                saveBuild();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setPartitionReadOnly(String name, boolean state){
        for (int i = 0; i < build.partitions.size(); i++){
            if (build.partitions.get(i).path().equalsIgnoreCase(name)){
                build.partitions.set(i, new Partition(name, state, build.partitions.get(i).hidden(), build.partitions.get(i).source()));
                saveBuild();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean deletePartition(String name){
        for (int i = 0; i < build.partitions.size(); i++){
            if (build.partitions.get(i).path().equalsIgnoreCase(name)){
                if (build.partitions.get(i).readOnly()) throw new ExposedError("Access denied");
                build.partitions.remove(i);
                FileUtils.deleteQuietly(basePath.resolve(name).toFile());
                saveBuild();
                for (int j = blacklist.size()-1; j >= 0; j--) {
                    if (blacklist.get(i).split(":")[0].equals(name)) {
                        blacklist.remove(i);
                        saveBlacklist();
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public java.util.List<Partition> getPartitions() {
        return build.partitions;
    }

    @Override
    public boolean exists(String path){
        Filepath file = getFile(path);
        return file.exists();
    }

    @Override
    public boolean makeDir(String path){
        Filepath file = getFile(path);
        if (!file.canWrite()) throw new ExposedError("Access denied");
        try{
            return file.mkdirs();
        }catch (Exception exception){
            if (exception instanceof IOException){
                throw new ExposedError(exception.getMessage());
            }else{
                throw new RuntimeException(exception);
            }
        }
    }

    @Override
    public boolean isDir(String path){
        Filepath file = getFile(path);
        return file.isDirectory();
    }

    @Override
    public boolean isFile(String path){
        Filepath file = getFile(path);
        if (!file.exists()) return false;
        return !file.isDirectory();
    }

    @Override
    public List getChildren(String path){
        Filepath file = getFile(path);
        if (file.isInvalid()) throw new ExposedError("Invalid file path");
        if (!file.exists()) throw new ExposedError("File does not exist");
        if (!file.isDirectory()) throw new ExposedError("Not a directory");
        try{
            String[] paths = file.listFiles();
            Arrays.sort(paths);
            return new List((Object[]) paths);
        }catch (Exception exception){
            if (exception instanceof IOException){
                throw new ExposedError(exception.getMessage());
            }else{
                throw new RuntimeException(exception);
            }
        }
    }

    @Override
    public boolean delete(String path){
        if (FileHelper.normalize(path).equals(FileHelper.normalize(build.entrypoint))) throw new ExposedError("Access denied");
        Filepath file = getFile(path);
        if (file.isInvalid()) throw new ExposedError("Invalid file path");
        if (!file.exists()) throw new ExposedError("File does not exist");
        if (!file.canWrite()) throw new ExposedError("Access denied");
        try{
            return file.delete();
        }catch (Exception exception){
            if (exception instanceof IOException){
                throw new ExposedError(exception.getMessage());
            }else{
                throw new RuntimeException(exception);
            }
        }
    }

    @Override
    public boolean isBootable() {
        return build.isBootable();
    }

    @Override
    public void makeBootable(String entrypoint, LanguageGeneric language) throws DiskError {
        String path = FileHelper.normalize(entrypoint);
        if (!FileHelper.isAbsolute(path) || !FileHelper.validatePathStatic(path)){
            throw new DiskError("Invalid entrypoint");
        }

        boolean check = false;
        String root = path.split(":")[0];
        for (Partition partition : build.partitions){
            if (partition.path().equals(root)) {
                check = true;
                break;
            }
        }
        if (!check) throw new DiskError("entrypoint doesn't refer to an assigned partition");
        build.entrypoint = entrypoint;
        build.language = language;
        saveBuild();
    }

    @Override
    public String getEntrypointPath() {
        if (!isBootable()) throw new ExposedError("Disk not bootable");
        return build.entrypoint;
    }

    @Override
    public Filepath getEntrypoint() throws DiskError{
        if (!isBootable()) throw new DiskError("Disk not bootable");
        Filepath file = getFile(build.entrypoint);
        if (!file.isFile()) throw new DiskError("Entrypoint not found");
        return file;
    }

    @Override
    public LanguageGeneric getLanguage() throws DiskError{
        if (!isBootable()) throw new DiskError("Disk not bootable");
        return build.language;
    }

    @Override
    public void removeBootability() {
        build.entrypoint = null;
        build.language = null;
        saveBuild();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof DiskSystem disk) {
            return basePath.equals(disk.basePath);
        }
        if (object instanceof Path path) {
            return path.equals(basePath);
        }
        return false;
    }
}