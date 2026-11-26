package com.redtoast.simulation.FS;

import com.redtoast.simulation.FS.FileImplementations.Filepath;
import com.redtoast.simulation.base.LanguageGeneric;
import com.redtoast.simulation.value.ValueTypes.List;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface GenericSystem {
    void update();
    Filepath getFile(String path);
    UUID getUuid();
    String getDiskId();
    long getCapacity();
    long getFreeSpace();
    boolean hasSpaceFor(long bytes);
    boolean partitionExists(String path);
    @Nullable Partition getPartition(String path);
    boolean createPartition(String name);
    boolean setPartitionHidden(String name, boolean state);
    boolean setPartitionReadOnly(String name, boolean state);
    boolean deletePartition(String name);
    java.util.List<Partition> getPartitions();
    boolean exists(String path);
    boolean makeDir(String path);
    boolean isDir(String path);
    boolean isFile(String path);
    List getChildren(String path);
    boolean delete(String path);
    boolean isBootable();
    void makeBootable(String entrypoint, LanguageGeneric language) throws DiskError;
    String getEntrypointPath();
    Filepath getEntrypoint() throws DiskError;
    LanguageGeneric getLanguage() throws DiskError;
    void removeBootability();

}
