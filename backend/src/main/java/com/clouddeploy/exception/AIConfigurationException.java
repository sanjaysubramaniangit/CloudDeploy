package com.clouddeploy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AIConfigurationException extends RuntimeException {
    public AIConfigurationException(String message) {
        super(message);
    }
}
