package com.voicepay.ivr.nlp.exception;

// Base exception class for NLP operations
public class NlpException extends RuntimeException {
    public NlpException(String message) {
        super(message);
    }
    public NlpException(String message, Throwable cause) {
        super(message, cause);
    }
}
