package com.voicepay.ivr.nlp;

import com.voicepay.ivr.nlp.exception.FallbackIntentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NlpClientImpl implements NlpClient {

    private final NlpProperties nlpProperties;

    @Override
    public NlpResult analyzeText(String text) {
        log.info("Analyzing text with NLP: \"{}\"", text);
        if (text == null || text.trim().isEmpty()) {
            throw new FallbackIntentException("Empty or null text provided");
        }

        String lowerText = text.toLowerCase().trim();
        String intent = "FALLBACK";
        double confidence = 0.0;

        // Clasificación semántica simple de intenciones en español e inglés
        if (lowerText.contains("pagar") || lowerText.contains("deuda") || lowerText.contains("factura") ||
                lowerText.contains("abonar") || lowerText.contains("confirmar") || lowerText.contains("pay") || 
                lowerText.contains("debt") || lowerText.contains("tarjeta") || lowerText.contains("si") || 
                lowerText.contains("sí") || lowerText.contains("uno") || lowerText.equals("1")) {
            intent = "PAY_DEBT";
            confidence = 0.95;
        } else if (lowerText.contains("agente") || lowerText.contains("soporte") || lowerText.contains("hablar") ||
                lowerText.contains("operador") || lowerText.contains("humano") || lowerText.contains("ayuda") || 
                lowerText.contains("agent") || lowerText.contains("support") || lowerText.contains("dos") || 
                lowerText.equals("2")) {
            intent = "TALK_TO_AGENT";
            confidence = 0.95;
        } else if (lowerText.contains("cancelar") || lowerText.contains("anular") || lowerText.contains("salir") || 
                lowerText.contains("cancel") || lowerText.contains("no")) {
            intent = "CANCEL";
            confidence = 0.90;
        }

        // Manejo del umbral de confianza mínimo y de intenciones no reconocidas (FALLBACK)
        if ("FALLBACK".equals(intent)) {
            log.warn("NLP classification failed. Intent resolved as FALLBACK for text: \"{}\"", text);
            throw new FallbackIntentException("Intent not recognized (FALLBACK) for text: " + text);
        }

        if (confidence < nlpProperties.getMinConfidence()) {
            log.warn("NLP intent confidence ({}) below minimum threshold ({}) for text: \"{}\"", 
                    confidence, nlpProperties.getMinConfidence(), text);
            throw new FallbackIntentException("NLP intent confidence below minimum threshold for text: " + text);
        }

        log.info("NLP Result - Intent: {}, Confidence: {}", intent, confidence);
        return new NlpResult(intent, confidence);
    }
}
