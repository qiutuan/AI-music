package com.pujian.music;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handle(ApiException error) {
        return ResponseEntity.status(error.getStatus()).body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException error) {
        var field = error.getBindingResult().getFieldError();
        return ResponseEntity.badRequest().body(Map.of("message", field == null ? "请检查输入内容" : field.getDefaultMessage()));
    }
}
