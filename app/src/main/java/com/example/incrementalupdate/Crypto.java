package com.example.incrementalupdate;

import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.security.SecureRandom;

public class SM4 {
    private static final int TAG_LENGTH = 128; // 认证标签长度，单位为比特
    private static final int IV_LENGTH = 12; // IV长度，GCM模式推荐12字节

    /**
     * 生成随机IV
     *
     * @return 随机生成的IV
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    /**
     * 使用SM4-GCM模式加密数据
     *
     * @param data 要加密的数据
     * @param key 密钥
     * @param iv 初始化向量
     * @return 加密后的数据
     * @throws Exception 加密过程中的异常
     */
    public static byte[] encrypt(byte[] data, byte[] key, byte[] iv) throws Exception {
        GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
        AEADParameters parameters = new AEADParameters(new KeyParameter(key), TAG_LENGTH, iv);

        cipher.init(true, parameters);

        byte[] output = new byte[cipher.getOutputSize(data.length)];
        int offset = cipher.processBytes(data, 0, data.length, output, 0);
        cipher.doFinal(output, offset);

        return output;
    }

    /**
     * 使用SM4-GCM模式解密数据
     *
     * @param encryptedData 加密的数据
     * @param key 密钥
     * @param iv 初始化向量
     * @return 解密后的数据
     * @throws Exception 解密过程中的异常
     */
    public static byte[] decrypt(byte[] encryptedData, byte[] key, byte[] iv) throws Exception {
        GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
        AEADParameters parameters = new AEADParameters(new KeyParameter(key), TAG_LENGTH, iv);

        cipher.init(false, parameters);

        byte[] output = new byte[cipher.getOutputSize(encryptedData.length)];
        int offset = cipher.processBytes(encryptedData, 0, encryptedData.length, output, 0);
        cipher.doFinal(output, offset);

        return output;
    }

    /**
     * 从外部获取IV的方法
     *
     * @return 从外部获取的IV
     */
    public static byte[] getExternalIV() {
        // 此方法留空，根据实际需求实现
        return new byte[IV_LENGTH]; // 返回空IV，实际应用中应替换为实际实现
    }
}
