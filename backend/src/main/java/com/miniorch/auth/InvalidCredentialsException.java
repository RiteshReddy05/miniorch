package com.miniorch.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
