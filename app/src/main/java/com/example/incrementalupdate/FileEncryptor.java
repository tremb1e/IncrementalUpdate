package com.example.incrementalupdate;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.security.SecureRandom;
import java.security.Security;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;

import java.security.Provider;
import java.security.Security;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Cipher;


public class FileEncryptor {
    private static final int BLOCK_SIZE = 4 * 1024; // 4KB
    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORMATION = "SM4/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // bytes
    private static final int TAG_LENGTH = 16; // bytes (128 bits)

    public FileEncryptor() {
        Security.addProvider(new BouncyCastleProvider());
        Provider bcProvider = Security.getProvider("BC");
        if (bcProvider != null) {
            System.out.println("BC Provider available: " + bcProvider.getName());

            // 获取并输出所有支持的加密模式
            Set<Provider.Service> cipherServices = bcProvider.getServices().stream()
                    .filter(service -> "Cipher".equals(service.getType()))
                    .collect(Collectors.toSet());
            Set<String> modes = new HashSet<>();
            for (Provider.Service service : cipherServices) {
                String algorithm = service.getAlgorithm();
                String[] parts = algorithm.split("/");
                if (parts.length >= 2) {
                    modes.add(parts[1]);
                }
            }
            System.out.println("Supported encryption modes by BC: " + modes);

            // 检查特定算法支持
            try {
                Cipher.getInstance("SM4/GCM/NoPadding", "BC");
                System.out.println("SM4/GCM/NoPadding is supported by BC");
            } catch (Exception e) {
                System.out.println("SM4/GCM/NoPadding not supported: " + e.getMessage());
            }
        } else {
            System.out.println("BC Provider not found");
        }
    }

    public byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);
        return iv;
    }

    public File encryptFile(File inputFile, String password, byte[] iv) throws Exception {
        File outputFile = new File(inputFile.getParentFile(), inputFile.getName() + ".encrypted");
        System.out.println("Encrypting to: " + outputFile.getAbsolutePath());

        byte[] keyBytes = deriveKeyFromPassword(password);
        SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);

        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            long totalBytesWritten = 0;

            fos.write(iv);
            totalBytesWritten += iv.length;
            DataOutputStream dos = new DataOutputStream(fos);
            dos.writeLong(inputFile.length());
            totalBytesWritten += 8;

            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            int blockIndex = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] blockIv = Arrays.copyOf(iv, IV_LENGTH);
                for (int i = 0; i < 4 && i < blockIv.length; i++) {
                    blockIv[i] ^= (blockIndex >> (i * 8)) & 0xFF;
                }

                GCMParameterSpec gcmParams = new GCMParameterSpec(GCM_TAG_LENGTH, blockIv);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION, "BC");
                cipher.init(Cipher.ENCRYPT_MODE, key, gcmParams);

                byte[] dataToEncrypt = bytesRead == BLOCK_SIZE ? buffer : Arrays.copyOf(buffer, bytesRead);
                byte[] encryptedData = cipher.doFinal(dataToEncrypt);

                fos.write(blockIv);
                totalBytesWritten += blockIv.length;
                dos.writeInt(encryptedData.length);
                totalBytesWritten += 4;
                fos.write(encryptedData);
                totalBytesWritten += encryptedData.length;

                blockIndex++;
            }

            long remainder = totalBytesWritten % BLOCK_SIZE;
            if (remainder != 0) {
                int paddingNeeded = (int) (BLOCK_SIZE - remainder);
                byte[] padding = new byte[paddingNeeded];
                fos.write(padding);
            }
        }

        if (outputFile.exists()) {
            System.out.println("Encrypted file created successfully: " + outputFile.getAbsolutePath());
        } else {
            System.out.println("Failed to create encrypted file: " + outputFile.getAbsolutePath());
        }
        return outputFile;
    }


    public File decryptFile(File inputFile, String password) throws Exception {
        File outputFile = new File(inputFile.getParentFile(),
                inputFile.getName().replace(".encrypted", ""));

        try (FileInputStream fis = new FileInputStream(inputFile);
             DataInputStream dis = new DataInputStream(fis);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            // 读取头部
            byte[] iv = new byte[IV_LENGTH];
            dis.readFully(iv);
            long originalFileSize = dis.readLong();

            // 初始化密钥
            byte[] keyBytes = deriveKeyFromPassword(password);
            SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);

            // 跟踪已解密的数据大小
            long totalBytesDecrypted = 0;
            int blockIndex = 0;

            // 依次解密每个块
            while (totalBytesDecrypted < originalFileSize) {
                // 读取块IV
                byte[] blockIv = new byte[IV_LENGTH];
                dis.readFully(blockIv);

                // 读取加密块大小
                int encryptedBlockSize = dis.readInt();

                // 读取加密数据
                byte[] encryptedBlock = new byte[encryptedBlockSize];
                dis.readFully(encryptedBlock);

                // 初始化解密器
                GCMParameterSpec gcmParams = new GCMParameterSpec(GCM_TAG_LENGTH, blockIv);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION, "BC");
                cipher.init(Cipher.DECRYPT_MODE, key, gcmParams);

                // 解密数据
                byte[] decryptedBlock = cipher.doFinal(encryptedBlock);

                // 写入解密后的数据，但不超过原始文件大小
                int bytesToWrite = (int) Math.min(decryptedBlock.length, originalFileSize - totalBytesDecrypted);
                fos.write(decryptedBlock, 0, bytesToWrite);

                // 更新已解密字节计数
                totalBytesDecrypted += bytesToWrite;

                // 如果这是最后一块且有填充，需要跳过这些填充字节
                if (encryptedBlockSize % BLOCK_SIZE != 0) {
                    int paddingSize = BLOCK_SIZE - (encryptedBlockSize % BLOCK_SIZE);
                    if (paddingSize > 0 && paddingSize < BLOCK_SIZE) {
                        dis.skipBytes(paddingSize);
                    }
                }

                blockIndex++;
            }
        }

        return outputFile;
    }

    private byte[] deriveKeyFromPassword(String password) {
        SM3Digest digest = new SM3Digest();
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        digest.update(passwordBytes, 0, passwordBytes.length);
        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);
        return Arrays.copyOf(hash, 16);
    }

    public File encryptStream(InputStream inputStream, File outputFile, String password, byte[] iv) throws Exception {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }

        System.out.println("Encrypting to: " + outputFile.getAbsolutePath());

        byte[] keyBytes = deriveKeyFromPassword(password);
        SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            long totalBytesWritten = 0;

            fos.write(iv);
            totalBytesWritten += iv.length;

            ByteArrayOutputStream tempBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                tempBuffer.write(buffer, 0, bytesRead);
            }
            inputStream.close();

            byte[] inputData = tempBuffer.toByteArray();
            long originalSize = inputData.length;

            DataOutputStream dos = new DataOutputStream(fos);
            dos.writeLong(originalSize);
            totalBytesWritten += 8;

            int blockIndex = 0;
            int offset = 0;

            while (offset < inputData.length) {
                int currentBlockSize = Math.min(BLOCK_SIZE, inputData.length - offset);
                byte[] blockData = Arrays.copyOfRange(inputData, offset, offset + currentBlockSize);

                byte[] blockIv = Arrays.copyOf(iv, IV_LENGTH);
                for (int i = 0; i < 4 && i < blockIv.length; i++) {
                    blockIv[i] ^= (blockIndex >> (i * 8)) & 0xFF;
                }

                GCMParameterSpec gcmParams = new GCMParameterSpec(GCM_TAG_LENGTH, blockIv);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION, "BC");
                cipher.init(Cipher.ENCRYPT_MODE, key, gcmParams);

                byte[] encryptedData = cipher.doFinal(blockData);

                fos.write(blockIv);
                totalBytesWritten += blockIv.length;
                dos.writeInt(encryptedData.length);
                totalBytesWritten += 4;
                fos.write(encryptedData);
                totalBytesWritten += encryptedData.length;

                offset += currentBlockSize;
                blockIndex++;
            }

            long remainder = totalBytesWritten % BLOCK_SIZE;
            if (remainder != 0) {
                int paddingNeeded = (int) (BLOCK_SIZE - remainder);
                byte[] padding = new byte[paddingNeeded];
                fos.write(padding);
            }
        }

        if (outputFile.exists()) {
            System.out.println("Encrypted file created successfully: " + outputFile.getAbsolutePath());
            return outputFile;
        } else {
            System.out.println("Failed to create encrypted file: " + outputFile.getAbsolutePath());
            return null;
        }

    }
}
