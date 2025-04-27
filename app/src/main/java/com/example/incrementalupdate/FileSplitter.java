package com.example.incrementalupdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileSplitter {
    private static final int BLOCK_SIZE = 4 * 1024; // 4KB
    private final NetworkManager networkManager;

    public FileSplitter(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public void splitAndSendFile(File file, int startBlock) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IOException("文件不存在或不是一个有效的文件");
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            // 跳过指定块前面的数据
            long bytesToSkip = (long) startBlock * BLOCK_SIZE;
            long skipped = 0;

            while (skipped < bytesToSkip) {
                long currentSkip = fis.skip(bytesToSkip - skipped);
                if (currentSkip <= 0) {
                    break;
                }
                skipped += currentSkip;
            }

            if (skipped < bytesToSkip) {
                throw new IOException("无法跳过指定字节数，文件可能太小");
            }

            // 读取剩余部分并发送
            byte[] remainingData = fis.readAllBytes();
            networkManager.sendFileBlocks(file.getName(), remainingData, startBlock);
        }
    }

    public byte[] getFileBlock(File file, int blockIndex) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            // 跳过之前的块
            long bytesToSkip = (long) blockIndex * BLOCK_SIZE;
            long skipped = 0;

            while (skipped < bytesToSkip) {
                long currentSkip = fis.skip(bytesToSkip - skipped);
                if (currentSkip <= 0) {
                    throw new IOException("无法跳过到指定块");
                }
                skipped += currentSkip;
            }

            // 读取一个块的数据
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead = fis.read(buffer);

            if (bytesRead <= 0) {
                throw new IOException("无法读取指定块的数据");
            }

            // 如果读取的数据不足一个完整块，则调整大小
            if (bytesRead < BLOCK_SIZE) {
                byte[] result = new byte[bytesRead];
                System.arraycopy(buffer, 0, result, 0, bytesRead);
                return result;
            }

            return buffer;
        }
    }
}
