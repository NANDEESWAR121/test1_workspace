package com.scaloz.superadmin.security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public class AesUtils {

    private static final int IV_SIZE = 12; // 12 bytes for GCM
    private static final int TAG_BIT_LENGTH = 128; // 128 bits for GCM auth tag
    private static final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    /** Utility class — not meant to be instantiated. */
    private AesUtils() {
        throw new UnsupportedOperationException("AesUtils is a utility class");
    }

    public static String encrypt(String plainText, String secretKey)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
                   InvalidKeyException, InvalidAlgorithmParameterException,
                   IllegalBlockSizeException, BadPaddingException {
        if (plainText == null || plainText.trim().isEmpty() || secretKey == null || secretKey.isEmpty()) {
            return plainText;
        }

        // Derive key bytes
        String keyStr = secretKey.substring(0, Math.min(secretKey.length(), 16));
        if (keyStr.length() < 16) {
            keyStr = String.format("%-16s", keyStr).replace(' ', '0');
        }
        byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        // Generate random IV
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);

        // Encrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_BIT_LENGTH, iv));
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Combine IV and ciphertext
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        // Encode to Base64
        return Base64.getEncoder().encodeToString(combined);
    }

    public static String decrypt(String encryptedBase64, String secretKey)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
                   InvalidKeyException, InvalidAlgorithmParameterException,
                   IllegalBlockSizeException, BadPaddingException {
        if (encryptedBase64 == null || encryptedBase64.trim().isEmpty() || secretKey == null || secretKey.isEmpty()) {
            return encryptedBase64;
        }

        // Decode Base64
        byte[] cipherTextWithIv = Base64.getDecoder().decode(encryptedBase64.trim());
        if (cipherTextWithIv.length <= IV_SIZE) {
            throw new IllegalArgumentException("Ciphertext is too short.");
        }

        // Extract IV (first 12 bytes)
        byte[] iv = Arrays.copyOfRange(cipherTextWithIv, 0, IV_SIZE);

        // Extract ciphertext (remaining bytes)
        byte[] cipherText = Arrays.copyOfRange(cipherTextWithIv, IV_SIZE, cipherTextWithIv.length);

        // Derive standard 16-byte key by padding or truncating to 16 bytes
        String keyStr = secretKey.substring(0, Math.min(secretKey.length(), 16));
        if (keyStr.length() < 16) {
            keyStr = String.format("%-16s", keyStr).replace(' ', '0');
        }
        byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        // Decrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_BIT_LENGTH, iv));
        byte[] decryptedBytes = cipher.doFinal(cipherText);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
