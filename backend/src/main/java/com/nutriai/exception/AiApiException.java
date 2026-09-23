package com.nutriai.exception;

public class AiApiException extends RuntimeException {
    private final String provider;
    private final int code;

    public AiApiException(String message) {
        super(message);
        this.provider = null;
        this.code = 400;
    }

    public AiApiException(int code, String message) {
        super(message);
        this.provider = null;
        this.code = code;
    }

    public AiApiException(String provider, String message) {
        super(message);
        this.provider = provider;
        this.code = 502;
    }

    public AiApiException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.code = 502;
    }

    public String getProvider() {
        return provider;
    }

    public int getCode() {
        return code;
    }
}