package com.gov.app.exception;

public class UpstreamException extends RuntimeException {

    private final int statusCode;

    public UpstreamException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
