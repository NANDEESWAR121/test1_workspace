package com.scaloz.superadmin.service;

import com.scaloz.superadmin.security.AesUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CryptoMetadataService {

    @Value("${scaloz.app.encryptionKey}")
    private String encryptionKey;

    public String encrypt(String plainText) {
        try {
            return AesUtils.encrypt(plainText, encryptionKey);
        } catch (Exception e) {
            return plainText; // Safe fallback if encryption fails
        }
    }

    public String decrypt(String cipherText) {
        try {
            return AesUtils.decrypt(cipherText, encryptionKey);
        } catch (Exception e) {
            return cipherText; // Return original on failure
        }
    }
}
