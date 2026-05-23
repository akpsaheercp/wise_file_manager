package com.wise.file_manager.db;

import androidx.room.*;
import java.util.List;

@Dao
public interface FileDao {
    @Query("SELECT * FROM file_metadata WHERE parentPath = :parentPath")
    List<FileMetadata> getFilesByParent(String parentPath);

    @Query("SELECT * FROM file_metadata WHERE path = :path LIMIT 1")
    FileMetadata getFileByPath(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FileMetadata> files);

    @Query("DELETE FROM file_metadata WHERE parentPath = :parentPath")
    void deleteByParent(String parentPath);

    @Query("DELETE FROM file_metadata")
    void clearAll();

    @Query("SELECT COUNT(*) FROM file_metadata")
    int getCount();

    @Query("SELECT * FROM file_metadata WHERE name LIKE :query")
    List<FileMetadata> search(String query);
}
