package com.voicepay.userservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionUtil Unit Tests - User Service")
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
    @DisplayName("Standard Encrypt and Decrypt — Should work successfully")
    void standardEncryptAndDecrypt_ShouldWorkSuccessfully() {
        String originalText = "Sensitive User Information";
        
        // Encrypt (Standard uses random IV, so encrypting twice gives different results)
        String encryptedText1 = encryptionUtil.encrypt(originalText);
        String encryptedText2 = encryptionUtil.encrypt(originalText);
        
        assertNotNull(encryptedText1);
        assertNotEquals(originalText, encryptedText1);
        assertNotEquals(encryptedText1, encryptedText2); // Random IV means different ciphertexts!

        // Decrypt
        String decryptedText1 = encryptionUtil.decrypt(encryptedText1);
        String decryptedText2 = encryptionUtil.decrypt(encryptedText2);
        
        assertEquals(originalText, decryptedText1);
        assertEquals(originalText, decryptedText2);
    }

    @Test
    @DisplayName("Deterministic Encrypt — Should produce same ciphertext for same plaintext")
    void deterministicEncrypt_ShouldProduceSameCiphertext() {
        String originalText = "cristian";
        
        // Deterministic encrypt
        String encryptedText1 = encryptionUtil.encryptDeterministic(originalText);
        String encryptedText2 = encryptionUtil.encryptDeterministic(originalText);
        
        System.out.println("DEBUG - ORIGINAL: " + originalText);
        System.out.println("DEBUG - ENCRYPTED1: " + encryptedText1);
        
        assertNotNull(encryptedText1);
        assertNotEquals(originalText, encryptedText1);
        assertEquals(encryptedText1, encryptedText2); // Deterministic! Same plaintext = same ciphertext

        // Decrypt
        String decryptedText = encryptionUtil.decrypt(encryptedText1);
        System.out.println("DEBUG - DECRYPTED: " + decryptedText);
        
        assertEquals(originalText, decryptedText);
    }

    @Test
    @DisplayName("Encrypt methods — Should return null when input is null")
    void encryptMethods_ShouldReturnNull_WhenInputIsNull() {
        assertNull(encryptionUtil.encrypt(null));
        assertNull(encryptionUtil.encryptDeterministic(null));
    }

    @Test
    @DisplayName("Decrypt — Should handle invalid or plaintext input gracefully")
    void decrypt_ShouldHandleInvalidInputGracefully() {
        // Plain text
        String plainText = "Unencrypted username";
        assertEquals(plainText, encryptionUtil.decrypt(plainText));
        
        // Null / Empty
        assertNull(encryptionUtil.decrypt(null));
        assertEquals("", encryptionUtil.decrypt(""));
    }
}
