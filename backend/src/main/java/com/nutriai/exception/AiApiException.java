package com.nutriai.exception;

public class AiApiException extends RuntimeException {
    private final String provider;

    public AiApiException(String provider, String message) {
        super(message);
        this.provider = provider;
    }

    public AiApiException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}