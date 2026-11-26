package com.redtoast.simulation.FS.FileImplementations;

import com.redtoast.simulation.FS.FileHelper;
import com.redtoast.simulation.FS.DiskSystem;
import com.redtoast.simulation.FS.Partition;
import org.apache.commons.io.FileUtils;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RealFilepath implements Filepath {
    private final Path root;
    private final String path;
    private final String relPath;
    private final DiskSystem fs;
    private boolean invalid = false;

    public RealFilepath(Path Root, String Path, DiskSystem system){
        root = Root;
        path = FileHelper.normalize(Path);
        relPath = FileHelper.deAbsolutize(path).replace('\\','/').substring(1);
        invalid = !FileHelper.validatePathStatic(path);
        if (!invalid){
            Path spath = root.resolve(relPath).normalize();
            try {
                if (!spath.startsWith(root)) {
                    invalid = false;
                }
                if (!spath.toFile().exists()){
                    invalid = false;
                }
                if (!spath.toRealPath().startsWith(root.toRealPath())){
                    invalid = false;
                }
            }catch (Exception e){
                invalid = false;
            }
        }
        fs = system;
    }
    @Override
    public boolean isInvalid() {
        return invalid;
    }

    @Override
    public String getName() {
        String[] parts = path.split("\\\\");
        return parts[parts.length-1];
    }

    @Override
    public boolean canRead() {
        return isFile();
    }

    @Override
    public boolean canWrite() {
        if (invalid) return false;
        String[] components = path.split(":");
        Partition partition = fs.getPartition(components[0]);
        if (partition==null) return false;
        return !partition.readOnly();
    }

    @Override
    public boolean exists() {
        if (invalid) return false;
        Path spath = root.resolve(relPath);
        return spath.toFile().exists();
    }

    @Override
    public boolean isDirectory() {
        if (!exists()) return false;
        Path spath = root.resolve(relPath);
        return Files.isDirectory(spath);
    }

    @Override
    public boolean isFile() {
        if (!exists()) return false;
        return !isDirectory();
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!exists()){
            mkdirs(1);
            Path spath = root.resolve(relPath);
            return spath.toFile().createNewFile();
        }
        return false;
    }

    @Override
    public String readAll() throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!isFile()) return null;
        Path spath = root.resolve(relPath);
        try{
            return Files.readString(spath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean write(byte[] bytes) throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!isFile()) return false;
        if (!canWrite()) return false;
        Path spath = root.resolve(relPath);
        String text = new String(bytes, StandardCharsets.UTF_8);
        long growth = text.getBytes(StandardCharsets.UTF_8).length - currentSize();
        requireSpace(growth);
        FileWriter writer = new FileWriter(spath.normalize().toFile(), false);
        writer.write(text);
        writer.close();
        fs.accountSpace(growth);
        return true;
    }

    @Override
    public boolean append(byte[] bytes) throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!isFile()) return false;
        if (!canWrite()) return false;
        Path spath = root.resolve(relPath);
        String text = new String(bytes, StandardCharsets.UTF_8);
        long growth = text.getBytes(StandardCharsets.UTF_8).length;
        requireSpace(growth);
        FileWriter writer = new FileWriter(spath.normalize().toFile(), true);
        writer.write(text);
        writer.close();
        fs.accountSpace(growth);
        return true;
    }

    private long currentSize() {
        Path spath = root.resolve(relPath);
        try {
            return Files.isRegularFile(spath) ? Files.size(spath) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private void requireSpace(long growth) throws IOException {
        if (growth > 0 && !fs.hasSpaceFor(growth)) throw new IOException("Disk full");
    }

    @Override
    public byte[] readAllBinary() throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!isFile()) return null;
        Path spath = root.resolve(relPath);
        try {
            return Files.readAllBytes(spath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean writeBinary(byte[] bytes) throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!isFile()) return false;
        if (!canWrite()) return false;
        Path spath = root.resolve(relPath);
        long growth = (long) bytes.length - currentSize();
        requireSpace(growth);
        try (OutputStream out = new FileOutputStream(spath.toFile(), false)) {
            out.write(bytes);
            fs.accountSpace(growth);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean appendBinary(byte[] bytes) throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!isFile()) return false;
        if (!canWrite()) return false;
        Path spath = root.resolve(relPath);
        requireSpace(bytes.length);
        try (OutputStream out = new FileOutputStream(spath.toFile(), true)) {
            out.write(bytes);
            fs.accountSpace(bytes.length);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete() throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!exists()) return false;
        if (!canWrite()) return false;
        Path spath = root.resolve(relPath);
        long size = currentSize();
        boolean deleted = FileUtils.deleteQuietly(spath.toFile());
        if (deleted) fs.accountSpace(-size);
        return deleted;
    }

    @Override
    public String[] listFiles() throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (!isDirectory()) return new String[0];
        java.io.File[] files = root.resolve(relPath).toFile().listFiles();
        if (files==null) return new String[0];
        String[] files2 = new String[files.length];
        for (int i = 0; i < files.length; i++){
            files2[i] = files[i].getName();
        }
        return files2;
    }

    private boolean mkdirs(int offset) throws IOException {
        if (invalid) throw new IOException("Invalid file path");
        if (exists()) return false;
        if (path.split("\\.").length>1 && offset==0) throw new IOException("Invalid file path");
        String[] parts = relPath.split("/");
        Path resolve = root;
        for (int i = 0; i < parts.length-offset; i++){
            resolve =resolve.resolve(parts[i]);
            resolve.toFile().mkdir();
        }
        return true;
    }

    @Override
    public boolean mkdirs() throws IOException {
        return mkdirs(0);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object obj){
        if (obj instanceof Filepath path){
            return getPath().equals(path.getPath());
        }
        return super.equals(obj);
    }
}
