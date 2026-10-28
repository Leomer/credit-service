package com.bank.credit.exception;

/** Thrown when a downstream service is unavailable, too slow or its circuit is open. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
