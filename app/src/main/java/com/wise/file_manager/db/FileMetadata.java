package com.wise.file_manager.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "file_metadata")
public class FileMetadata {
    @PrimaryKey
    @NonNull
    public String path;
    public String parentPath;
    public String name;
    public boolean isDirectory;
    public long size;
    public long lastModified;
    public String childCount;

    public FileMetadata() {}

    @Ignore
    public FileMetadata(String path, String parentPath, String name, boolean isDirectory, long size, long lastModified, String childCount) {
        this.path = path;
        this.parentPath = parentPath;
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.childCount = childCount;
    }
}
