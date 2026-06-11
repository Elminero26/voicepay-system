package com.voicepay.ivr.nlp.exception;

public class NlpException extends RuntimeException {
    public NlpException(String message) {
        super(message);
    }
    public NlpException(String message, Throwable cause) {
        super(message, cause);
    }
}
