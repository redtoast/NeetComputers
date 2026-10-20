package com.redtoast.simulation.FS.FileImplementations;

import com.redtoast.simulation.FS.DataNode;
import com.redtoast.simulation.FS.FileHelper;
import com.redtoast.simulation.FS.DiskSystem;

import java.io.IOException;

public class DataFilepath implements Filepath {
    private final String path;
    private String relPath;
    private final DiskSystem fs;
    private final boolean invalid;
    public final DataNode node;

    public DataFilepath(String pointer, String path, DiskSystem system) {
        this.path = FileHelper.normalize(path);
        relPath = FileHelper.deAbsolutize(this.path).replace('\\', '/');
        if (relPath.endsWith("/")) {
            relPath = relPath.substring(0, relPath.length() - 1);
        }
        invalid = !FileHelper.validatePathStatic(this.path);
        fs = system;
        node = invalid ? null : DataNode.getNode(pointer, relPath);
    }

    @Override
    public boolean isInvalid() {
        return invalid;
    }

    @Override
    public String getName() {
        String[] parts = path.split("\\\\");
        return parts[parts.length - 1];
    }

    @Override
    public boolean canRead() {
        return isFile();
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean exists() {
        if (isInvalid())
            return false;
        if (fs.blacklist.contains(getPath()))
            return false;
        return node != null;
    }

    @Override
    public boolean isDirectory() {
        if (!exists())
            return false;
        return node.isDirectory();
    }

    @Override
    public boolean isFile() {
        if (!exists())
            return false;
        return !node.isDirectory();
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        return false;
    }

    @Override
    public String readAll() throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        if (!exists() || node.isDirectory())
            return null;
        return node.getText();
    }

    @Override
    public boolean write(byte[] bytes) throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        return false;
    }

    @Override
    public boolean append(byte[] bytes) throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        return false;
    }

    @Override
    public byte[] readAllBinary() throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        if (!exists() || node.isDirectory())
            return null;
        return node.getBytes();
    }

    @Override
    public boolean writeBinary(byte[] bytes) throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        return false;
    }

    @Override
    public boolean appendBinary(byte[] bytes) throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        return false;
    }

    @Override
    public boolean delete() throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        return false;
    }

    @Override
    public String[] listFiles() throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        if (!isDirectory())
            return null;
        return node.getChildren(fs.blacklist);
    }

    @Override
    public boolean mkdirs() throws IOException {
        if (invalid)
            throw new IOException("Invalid file path");
        return false;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Filepath path) {
            return getPath().equals(path.getPath());
        }
        return super.equals(obj);
    }
}
