package com.voicepay.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyExchangeService Unit Tests")
@SuppressWarnings("null")
class CurrencyExchangeServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private CurrencyExchangeService currencyExchangeService;

    private CurrencyExchangeService.ExchangeRateResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = new CurrencyExchangeService.ExchangeRateResponse();
        mockResponse.setResult("success");
        mockResponse.setBaseCode("EUR");

        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("EUR", new BigDecimal("1.0000"));
        rates.put("USD", new BigDecimal("1.1000")); // 1 EUR = 1.10 USD
        rates.put("GBP", new BigDecimal("0.8000")); // 1 EUR = 0.80 GBP
        rates.put("MXN", new BigDecimal("20.0000")); // 1 EUR = 20.00 MXN

        mockResponse.setRates(rates);
    }

    @Test
    @DisplayName("updateExchangeRates — Success Scenario")
    void whenApiSucceeds_thenPopulateCache() {
        // GIVEN
        when(restTemplate.getForObject(any(String.class), eq(CurrencyExchangeService.ExchangeRateResponse.class)))
                .thenReturn(mockResponse);

        // WHEN
        currencyExchangeService.updateExchangeRates();

        // THEN
        assertThat(currencyExchangeService.getRate("USD")).isEqualByComparingTo("1.1000");
        assertThat(currencyExchangeService.getRate("GBP")).isEqualByComparingTo("0.8000");
        assertThat(currencyExchangeService.getRate("EUR")).isEqualByComparingTo("1.0000");
        assertThat(currencyExchangeService.getLastUpdated()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("updateExchangeRates — Failure Scenario (Uses Fallback)")
    void whenApiFails_thenUseFallbackRates() {
        // GIVEN
        when(restTemplate.getForObject(any(String.class), eq(CurrencyExchangeService.ExchangeRateResponse.class)))
                .thenThrow(new RuntimeException("API Down"));

        // WHEN
        currencyExchangeService.updateExchangeRates();

        // THEN
        // Debería caer al fallback de contingencia (ej. USD: 1.0850, GBP: 0.8520)
        assertThat(currencyExchangeService.getRate("USD")).isEqualByComparingTo("1.0850");
        assertThat(currencyExchangeService.getRate("GBP")).isEqualByComparingTo("0.8520");
        assertThat(currencyExchangeService.getRate("EUR")).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("convert — Standard conversion with high precision")
    void whenConvertingBetweenCurrencies_thenCalculateWithPrecision() {
        // GIVEN
        when(restTemplate.getForObject(any(String.class), eq(CurrencyExchangeService.ExchangeRateResponse.class)))
                .thenReturn(mockResponse);
        currencyExchangeService.updateExchangeRates();

        // Convertir 11.00 USD a EUR (1 EUR = 1.10 USD)
        // 11.00 / 1.10 = 10.00 EUR
        BigDecimal usdToEur = currencyExchangeService.convert(new BigDecimal("11.00"), "USD", "EUR");
        assertThat(usdToEur).isEqualByComparingTo("10.00");

        // Convertir 10.00 EUR a GBP (1 EUR = 0.80 GBP)
        // 10.00 * 0.80 = 8.00 GBP
        BigDecimal eurToGbp = currencyExchangeService.convert(new BigDecimal("10.00"), "EUR", "GBP");
        assertThat(eurToGbp).isEqualByComparingTo("8.00");

        // Convertir 55.00 USD a GBP (via EUR)
        // 55.00 USD / 1.10 = 50.00 EUR -> 50.00 EUR * 0.80 = 40.00 GBP
        BigDecimal usdToGbp = currencyExchangeService.convert(new BigDecimal("55.00"), "USD", "GBP");
        assertThat(usdToGbp).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("convert — Handles scale and rounding gracefully")
    void whenConvertingWithComplexRates_thenRoundHalfUp() {
        // GIVEN
        when(restTemplate.getForObject(any(String.class), eq(CurrencyExchangeService.ExchangeRateResponse.class)))
                .thenReturn(mockResponse);
        currencyExchangeService.updateExchangeRates();

        // 10.00 USD a EUR con tasa 1.10. 10 / 1.10 = 9.0909...
        // Debería redondear a 9.09
        BigDecimal result = currencyExchangeService.convert(new BigDecimal("10.00"), "USD", "EUR");
        assertThat(result).isEqualByComparingTo("9.09");
    }
}
