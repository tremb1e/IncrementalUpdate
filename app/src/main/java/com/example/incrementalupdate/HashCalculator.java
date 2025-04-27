package com.example.incrementalupdate;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HashCalculator {
    private static final int BLOCK_SIZE = 4 * 1024; // 4KB

    public byte[] calculateSM3(byte[] input) {
        SM3Digest sm3 = new SM3Digest();
        sm3.update(input, 0, input.length);
        byte[] hash = new byte[sm3.getDigestSize()];
        sm3.doFinal(hash, 0);
        return hash;
    }

    public String calculateSM3AsHex(byte[] input) {
        byte[] hash = calculateSM3(input);
        return Hex.toHexString(hash);
    }

    public List<String> calculateFileBlockHashes(File file) throws IOException {
        List<String> blockHashes = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            int blockNumber = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                // 如果不是整块，只计算实际读取的数据
                byte[] blockData = bytesRead == BLOCK_SIZE ?
                        buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                // 计算该块的SM3哈希
                String blockHash = calculateSM3AsHex(blockData);
                blockHashes.add(blockHash);

                blockNumber++;
            }
        }

        return blockHashes;
    }
}
