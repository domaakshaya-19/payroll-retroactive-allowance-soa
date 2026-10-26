package com.example.payroll;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class InputErrors {
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<java.util.Map<String,String>> invalidInput(Exception ignored) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error","Invalid request"));
    }
}
