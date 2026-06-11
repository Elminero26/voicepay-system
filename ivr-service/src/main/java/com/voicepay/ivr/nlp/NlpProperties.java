package com.voicepay.ivr.nlp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.nlp")
public class NlpProperties {
    private String provider = "mock";
    private double minConfidence = 0.7;
}
