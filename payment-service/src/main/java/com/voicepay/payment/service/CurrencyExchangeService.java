package com.voicepay.payment.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExchangeService {

    private final RestTemplate restTemplate;

    private static final String FX_API_URL = "https://open.er-api.com/v6/latest/EUR";

    // Caché en memoria para tipos de cambio (base: EUR)
    private final Map<String, BigDecimal> exchangeRatesCache = new ConcurrentHashMap<>();
    private LocalDateTime lastUpdated;

    // Valores por defecto (Fallback) en caso de que falle la API
    private static final Map<String, BigDecimal> FALLBACK_RATES;
    static {
        Map<String, BigDecimal> fallback = new HashMap<>();
        fallback.put("EUR", new BigDecimal("1.0000"));
        fallback.put("USD", new BigDecimal("1.0850"));
        fallback.put("GBP", new BigDecimal("0.8520"));
        fallback.put("MXN", new BigDecimal("18.2500"));
        fallback.put("JPY", new BigDecimal("170.5000"));
        fallback.put("CAD", new BigDecimal("1.4800"));
        fallback.put("AUD", new BigDecimal("1.6300"));
        fallback.put("CHF", new BigDecimal("0.9900"));
        FALLBACK_RATES = Collections.unmodifiableMap(fallback);
    }

    @PostConstruct
    public void init() {
        log.info("Inicializando tipos de cambio...");
        updateExchangeRates();
    }

    /**
     * Tarea programada para actualizar las divisas automáticamente cada hora (3600000 ms)
     */
    @Scheduled(fixedRate = 3600000)
    public void scheduledUpdate() {
        log.info("Actualización programada de tipos de cambio en ejecución...");
        updateExchangeRates();
    }

    /**
     * Obtiene los tipos de cambio en tiempo real desde la API FX
     * Si la API falla, carga los valores por defecto
     */
    public synchronized void updateExchangeRates() {
        try {
            log.info("Obteniendo tipos de cambio actualizados desde: {}", FX_API_URL);
            ExchangeRateResponse response = restTemplate.getForObject(FX_API_URL, ExchangeRateResponse.class);

            if (response != null && "success".equalsIgnoreCase(response.getResult()) && response.getRates() != null) {
                exchangeRatesCache.clear();
                // Asegurarse de que el monto se procesa con precisión monetaria adecuada
                response.getRates().forEach((currency, rate) -> {
                    exchangeRatesCache.put(currency.toUpperCase(), rate.setScale(4, RoundingMode.HALF_UP));
                });
                lastUpdated = LocalDateTime.now();
                log.info("Tipos de cambio actualizados con éxito. Total divisas: {}", exchangeRatesCache.size());
            } else {
                throw new RuntimeException("Respuesta inválida de la API de tipos de cambio");
            }
        } catch (Exception e) {
            log.error("Error al actualizar tipos de cambio desde la API. Utilizando valores de contingencia (fallback). Detalle: {}", e.getMessage());
            if (exchangeRatesCache.isEmpty()) {
                FALLBACK_RATES.forEach((currency, rate) -> {
                    exchangeRatesCache.put(currency.toUpperCase(), rate.setScale(4, RoundingMode.HALF_UP));
                });
                lastUpdated = LocalDateTime.now();
            }
        }
    }

    /**
     * Obtiene el mapa actual de tipos de cambio
     */
    public Map<String, BigDecimal> getExchangeRates() {
        if (exchangeRatesCache.isEmpty()) {
            init();
        }
        return Collections.unmodifiableMap(exchangeRatesCache);
    }

    /**
     * Obtiene la fecha y hora de la última actualización exitosa o de contingencia
     */
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Obtiene la tasa de cambio de una divisa respecto al EUR
     */
    public BigDecimal getRate(String currency) {
        String key = currency.toUpperCase();
        if (exchangeRatesCache.isEmpty()) {
            init();
        }
        BigDecimal rate = exchangeRatesCache.get(key);
        if (rate == null) {
            log.warn("Divisa no soportada en la caché: {}. Utilizando valor temporal de 1.0", key);
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return rate;
    }

    /**
     * Convierte un importe de una divisa origen a una divisa destino gestionando la precisión monetaria
     * 
     * @param amount Importe original
     * @param fromCurrency Divisa de origen (ej. USD)
     * @param toCurrency Divisa de destino (ej. EUR)
     * @return Importe convertido con escala de 2 decimales y redondeo HALF_UP
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();

        if (from.equals(to)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rateFrom = getRate(from);
        BigDecimal rateTo = getRate(to);

        // Convertir importe a EUR primero (divisa base)
        // Ejemplo: Si son 10.85 USD y 1 EUR = 1.085 USD -> 10.85 / 1.085 = 10.00 EUR
        BigDecimal amountInEur = amount.divide(rateFrom, 6, RoundingMode.HALF_UP);

        // Convertir de EUR a divisa destino
        // Ejemplo: 10.00 EUR * 0.852 GBP = 8.52 GBP
        BigDecimal convertedAmount = amountInEur.multiply(rateTo);

        // Retornar con precisión estándar de cobro de 2 decimales
        return convertedAmount.setScale(2, RoundingMode.HALF_UP);
    }

    @Data
    public static class ExchangeRateResponse {
        private String result;
        @JsonProperty("base_code")
        private String baseCode;
        private Map<String, BigDecimal> rates;
    }
}
