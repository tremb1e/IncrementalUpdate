package com.example.incrementalupdate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class MetadataCollector {

    public String getFileName(File file) {
        return file.getName();
    }

    public long getCreationTime(File file) {
        try {
            Path path = file.toPath();
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return attrs.creationTime().toMillis();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果无法获取创建时间，返回最后修改时间
            return file.lastModified();
        }
    }

    public long getModificationTime(File file) {
        return file.lastModified();
    }

    // 获取完整的文件元数据
    public FileMetadata getFileMetadata(File file) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileName(getFileName(file));
        metadata.setCreationTime(getCreationTime(file));
        metadata.setModificationTime(getModificationTime(file));
        metadata.setSize(file.length());
        return metadata;
    }

    // 文件元数据类
    public static class FileMetadata {
        private String fileName;
        private long creationTime;
        private long modificationTime;
        private long size;

        // Getters and setters
        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public void setCreationTime(long creationTime) {
            this.creationTime = creationTime;
        }

        public long getModificationTime() {
            return modificationTime;
        }

        public void setModificationTime(long modificationTime) {
            this.modificationTime = modificationTime;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }
    }
}
