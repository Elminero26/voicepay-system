package com.voicepay.userservice.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Converter
public class DeterministicEncryptionConverter implements AttributeConverter<String, String> {

    private static EncryptionUtil encryptionUtil;

    @Autowired
    public void setEncryptionUtil(EncryptionUtil encryptionUtil) {
        DeterministicEncryptionConverter.encryptionUtil = encryptionUtil;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionUtil != null ? encryptionUtil.encryptDeterministic(attribute) : attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionUtil != null ? encryptionUtil.decrypt(dbData) : dbData;
    }
}
