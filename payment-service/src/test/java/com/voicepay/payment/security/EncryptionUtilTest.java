package com.voicepay.payment.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionUtil Unit Tests")
public class EncryptionUtilTest {

    private static final String VALID_KEY = "v0ic3P4yS3cur3Key2026Encryption!";
    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil(VALID_KEY);
    }

    @Test
    @DisplayName("Constructor — Should throw exception when key length is invalid")
    void constructor_ShouldThrowException_WhenKeyLengthIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionUtil("too-short-key"));
        assertThrows(IllegalArgumentException.class, () -> new EncryptionUtil(null));
    }

    @Test
    @DisplayName("Encrypt and Decrypt — Should encrypt and decrypt string successfully")
    void encryptAndDecrypt_ShouldWorkSuccessfully() {
        String originalText = "Sensitive Payment Data - Card Number: 1234-5678-9012-3456";
        
        // Encrypt
        String encryptedText = encryptionUtil.encrypt(originalText);
        assertNotNull(encryptedText);
        assertNotEquals(originalText, encryptedText);
        assertFalse(encryptedText.isEmpty());

        // Decrypt
        String decryptedText = encryptionUtil.decrypt(encryptedText);
        assertEquals(originalText, decryptedText);
    }

    @Test
    @DisplayName("Encrypt — Should return null when input is null")
    void encrypt_ShouldReturnNull_WhenInputIsNull() {
        assertNull(encryptionUtil.encrypt(null));
    }

    @Test
    @DisplayName("Decrypt — Should return original input when decryption fails or is plain text")
    void decrypt_ShouldReturnOriginalInput_WhenNotEncrypted() {
        String plainText = "Plain unencrypted data";
        String decrypted = encryptionUtil.decrypt(plainText);
        assertEquals(plainText, decrypted);
    }

    @Test
    @DisplayName("Decrypt — Should return null or empty when input is null or empty")
    void decrypt_ShouldReturnNullOrEmpty_WhenInputIsNullOrEmpty() {
        assertNull(encryptionUtil.decrypt(null));
        assertEquals("", encryptionUtil.decrypt(""));
    }
}
