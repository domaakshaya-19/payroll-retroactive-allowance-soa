package com.example.payroll;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class InputErrors {
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
        jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) { return error(HttpStatus.BAD_REQUEST,"Invalid request"); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> domain(ResponseStatusException error) {
        return error(error.getStatusCode(),error.getReason()==null?"Request rejected":error.getReason());
    }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.dao.OptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> conflict(Exception ignored) {return error(HttpStatus.CONFLICT,"Conflicting or duplicate operation");}
    private ResponseEntity<ProblemDetail> error(HttpStatusCode status,String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status,detail));
    }
}
