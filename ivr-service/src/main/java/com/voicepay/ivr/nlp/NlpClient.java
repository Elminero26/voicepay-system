package com.voicepay.ivr.nlp;

// Client interface for Natural Language Processing
import com.voicepay.ivr.nlp.exception.NlpException;

public interface NlpClient {
    /**
     * Analiza el texto de la transcripción y retorna el resultado NLP.
     * @param text texto a analizar
     * @return NlpResult con el ID de intención y el nivel de confianza
     * @throws NlpException en caso de error o si la intención no es reconocida (FALLBACK)
     */
    NlpResult analyzeText(String text) throws NlpException;
}
