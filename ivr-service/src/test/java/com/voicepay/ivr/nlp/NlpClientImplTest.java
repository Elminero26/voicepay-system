package com.voicepay.ivr.nlp;

import com.voicepay.ivr.nlp.exception.FallbackIntentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NlpClientImpl Unit Tests")
class NlpClientImplTest {

    private NlpProperties nlpProperties;
    private NlpClientImpl nlpClient;

    @BeforeEach
    void setUp() {
        nlpProperties = new NlpProperties();
        nlpProperties.setMinConfidence(0.7);
        nlpClient = new NlpClientImpl(nlpProperties);
    }

    @Test
    @DisplayName("analyzeText — Given payment phrases, should return PAY_DEBT")
    void givenPaymentText_whenAnalyzed_thenReturnPayDebt() {
        NlpResult result1 = nlpClient.analyzeText("Quiero pagar mi deuda");
        assertThat(result1.getIntent()).isEqualTo("PAY_DEBT");
        assertThat(result1.getConfidence()).isGreaterThanOrEqualTo(0.7);

        NlpResult result2 = nlpClient.analyzeText("Pagar factura");
        assertThat(result2.getIntent()).isEqualTo("PAY_DEBT");

        NlpResult result3 = nlpClient.analyzeText("si");
        assertThat(result3.getIntent()).isEqualTo("PAY_DEBT");

        NlpResult result4 = nlpClient.analyzeText("1");
        assertThat(result4.getIntent()).isEqualTo("PAY_DEBT");
    }

    @Test
    @DisplayName("analyzeText — Given agent phrases, should return TALK_TO_AGENT")
    void givenAgentText_whenAnalyzed_thenReturnTalkToAgent() {
        NlpResult result1 = nlpClient.analyzeText("quiero hablar con un agente");
        assertThat(result1.getIntent()).isEqualTo("TALK_TO_AGENT");
        assertThat(result1.getConfidence()).isGreaterThanOrEqualTo(0.7);

        NlpResult result2 = nlpClient.analyzeText("soporte");
        assertThat(result2.getIntent()).isEqualTo("TALK_TO_AGENT");

        NlpResult result3 = nlpClient.analyzeText("ayuda por favor");
        assertThat(result3.getIntent()).isEqualTo("TALK_TO_AGENT");

        NlpResult result4 = nlpClient.analyzeText("2");
        assertThat(result4.getIntent()).isEqualTo("TALK_TO_AGENT");
    }

    @Test
    @DisplayName("analyzeText — Given cancel phrases, should return CANCEL")
    void givenCancelText_whenAnalyzed_thenReturnCancel() {
        NlpResult result1 = nlpClient.analyzeText("quiero cancelar la operacion");
        assertThat(result1.getIntent()).isEqualTo("CANCEL");
        assertThat(result1.getConfidence()).isGreaterThanOrEqualTo(0.7);

        NlpResult result2 = nlpClient.analyzeText("anular");
        assertThat(result2.getIntent()).isEqualTo("CANCEL");
    }

    @Test
    @DisplayName("analyzeText — Given unrecognized text, should throw FallbackIntentException")
    void givenUnrecognizedText_whenAnalyzed_thenThrowFallbackIntentException() {
        assertThatThrownBy(() -> nlpClient.analyzeText("quiero jugar con mi perro"))
                .isInstanceOf(FallbackIntentException.class)
                .hasMessageContaining("Intent not recognized (FALLBACK)");
    }

    @Test
    @DisplayName("analyzeText — Given empty or null text, should throw FallbackIntentException")
    void givenEmptyOrNullText_whenAnalyzed_thenThrowFallbackIntentException() {
        assertThatThrownBy(() -> nlpClient.analyzeText(null))
                .isInstanceOf(FallbackIntentException.class);

        assertThatThrownBy(() -> nlpClient.analyzeText("   "))
                .isInstanceOf(FallbackIntentException.class);
    }

    @Test
    @DisplayName("analyzeText — When confidence is below minimum threshold, should throw FallbackIntentException")
    void givenLowConfidenceIntent_whenAnalyzed_thenThrowFallbackIntentException() {
        // Configure high threshold to trigger confidence fallback
        nlpProperties.setMinConfidence(0.99);

        assertThatThrownBy(() -> nlpClient.analyzeText("quiero pagar mi deuda"))
                .isInstanceOf(FallbackIntentException.class)
                .hasMessageContaining("confidence below minimum threshold");
    }
}
