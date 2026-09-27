package com.bot.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> illegal(IllegalArgumentException exception) { return Map.of("error", exception.getMessage() == null ? "invalid request" : exception.getMessage()); }
}
