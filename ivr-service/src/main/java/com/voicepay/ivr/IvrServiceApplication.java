package com.voicepay.ivr;

import com.voicepay.ivr.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class IvrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IvrServiceApplication.class, args);
    }
}
