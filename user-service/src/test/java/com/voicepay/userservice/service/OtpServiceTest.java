package com.voicepay.userservice.service;

import com.voicepay.userservice.dto.OtpGenerateResponse;
import com.voicepay.userservice.dto.OtpValidateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService();
    }

    @Test
    void generateOtp_ShouldCreateCodeWithCorrectLength() {
        OtpGenerateResponse response4 = otpService.generateOtp("user4", 4, 3);
        assertNotNull(response4);
        assertEquals("user4", response4.getIdentifier());
        assertEquals(4, response4.getCode().length());
        assertTrue(response4.getCode().matches("\\d{4}"));
        assertTrue(response4.getExpiryTime().isAfter(LocalDateTime.now()));

        OtpGenerateResponse response6 = otpService.generateOtp("user6", 6, 3);
        assertNotNull(response6);
        assertEquals("user6", response6.getIdentifier());
        assertEquals(6, response6.getCode().length());
        assertTrue(response6.getCode().matches("\\d{6}"));
    }

    @Test
    void generateOtp_ShouldThrowExceptionOnInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> otpService.generateOtp("user", 5, 3));
        assertThrows(IllegalArgumentException.class, () -> otpService.generateOtp("user", 3, 3));
        assertThrows(IllegalArgumentException.class, () -> otpService.generateOtp("user", 7, 3));
    }

    @Test
    void generateOtp_ShouldThrowExceptionOnInvalidTtl() {
        assertThrows(IllegalArgumentException.class, () -> otpService.generateOtp("user", 6, 0));
        assertThrows(IllegalArgumentException.class, () -> otpService.generateOtp("user", 6, -1));
    }

    @Test
    void validateOtp_Success_ShouldInvalidateCode() {
        OtpGenerateResponse generateResponse = otpService.generateOtp("user1", 6, 3);
        String code = generateResponse.getCode();

        OtpValidateResponse validateResponse = otpService.validateOtp("user1", code);
        assertTrue(validateResponse.isValid());
        assertEquals("OTP validado con éxito", validateResponse.getMessage());

        // Validate again (second use should fail since it was invalidated)
        OtpValidateResponse validateResponse2 = otpService.validateOtp("user1", code);
        assertFalse(validateResponse2.isValid());
    }

    @Test
    void validateOtp_WrongCode_ShouldNotInvalidateCode() {
        OtpGenerateResponse generateResponse = otpService.generateOtp("user2", 6, 3);
        String code = generateResponse.getCode();
        String wrongCode = code.equals("123456") ? "654321" : "123456";

        OtpValidateResponse validateResponse = otpService.validateOtp("user2", wrongCode);
        assertFalse(validateResponse.isValid());
        assertEquals("Código OTP incorrecto", validateResponse.getMessage());

        // Validate again with the correct code (should succeed)
        OtpValidateResponse validateResponse2 = otpService.validateOtp("user2", code);
        assertTrue(validateResponse2.isValid());
    }

    @Test
    void validateOtp_Expired_ShouldReturnFalseAndRemove() {
        // We manually insert an expired entry into the protected cache for test isolation
        otpService.getOtpCache().put("user-exp", new OtpService.OtpDetails("1234", LocalDateTime.now().minusSeconds(1)));

        OtpValidateResponse validateResponse = otpService.validateOtp("user-exp", "1234");
        assertFalse(validateResponse.isValid());
        assertEquals("El código OTP ha expirado", validateResponse.getMessage());
        assertFalse(otpService.getOtpCache().containsKey("user-exp"));
    }

    @Test
    void cleanExpiredOtps_ShouldRemoveExpiredEntriesOnly() {
        otpService.getOtpCache().put("valid", new OtpService.OtpDetails("1111", LocalDateTime.now().plusMinutes(5)));
        otpService.getOtpCache().put("expired", new OtpService.OtpDetails("2222", LocalDateTime.now().minusMinutes(1)));

        otpService.cleanExpiredOtps();

        assertTrue(otpService.getOtpCache().containsKey("valid"));
        assertFalse(otpService.getOtpCache().containsKey("expired"));
    }
}
