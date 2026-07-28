package com.buy01.orderservice.exception;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validation(){return error(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Request validation failed");}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,Object>> bad(IllegalArgumentException e){return error(HttpStatus.BAD_REQUEST,"BAD_REQUEST",e.getMessage());}
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<Map<String,Object>> conflict(IllegalStateException e){return error(HttpStatus.CONFLICT,"CONFLICT",e.getMessage());}
    @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Map<String,Object>> missing(NoSuchElementException e){return error(HttpStatus.NOT_FOUND,"NOT_FOUND",e.getMessage());}
    @ExceptionHandler(SecurityException.class) ResponseEntity<Map<String,Object>> forbidden(SecurityException e){return error(HttpStatus.FORBIDDEN,"FORBIDDEN",e.getMessage());}
    private ResponseEntity<Map<String,Object>> error(HttpStatus s,String c,String m){return ResponseEntity.status(s).body(Map.of("code",c,"message",m,"details",Map.of(),"timestamp",Instant.now()));}
}
