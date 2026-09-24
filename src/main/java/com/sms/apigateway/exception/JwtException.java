package com.sms.apigateway.exception;

public class JwtException extends RuntimeException{
    public JwtException(String message) {
        super(message);
    }
}
