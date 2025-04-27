package com.example.incrementalupdate;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

public class NetworkManager {
    private final String serverIp;
    private final int serverPort;

    public NetworkManager(String serverIp, int serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    public void sendMetadataAndHashes(String fileName, long creationTime,
                                      long modificationTime, List<String> blockHashes)
            throws IOException {
        try (Socket socket = new Socket(serverIp, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            // 发送操作类型：元数据和哈希
            dos.writeInt(1);

            // 发送文件名
            dos.writeUTF(fileName);

            // 发送时间戳
            dos.writeLong(creationTime);
            dos.writeLong(modificationTime);

            // 发送哈希值数量和每个哈希值
            dos.writeInt(blockHashes.size());
            for (String hash : blockHashes) {
                dos.writeUTF(hash);
            }

            dos.flush();
        }
    }

    public void sendFileBlocks(String fileName, byte[] fileData, int startBlock)
            throws IOException {
        try (Socket socket = new Socket(serverIp, serverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            // 发送操作类型：文件块
            dos.writeInt(2);

            // 发送文件名
            dos.writeUTF(fileName);

            // 发送起始块索引
            dos.writeInt(startBlock);

            // 发送数据长度和数据
            dos.writeInt(fileData.length);
            dos.write(fileData);

            dos.flush();
        }
    }

    public void sendFile(OutputStream outputStream, byte[] data) throws IOException {
        outputStream.write(data);
        outputStream.flush();
    }
}
