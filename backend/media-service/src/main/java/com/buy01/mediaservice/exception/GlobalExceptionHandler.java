package com.buy01.mediaservice.exception;

import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(MediaNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ReferencedProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReferencedProductNotFound(ReferencedProductNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(UnsupportedMediaException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMedia(UnsupportedMediaException exception) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage(), List.of());
    }

    @ExceptionHandler({
            InvalidMediaException.class,
            MaxUploadSizeExceededException.class,
            MultipartException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiErrorResponse> handleJwt(JwtException exception) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid or expired token", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException exception) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", List.of());
    }

    @ExceptionHandler(StorageOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleStorage(StorageOperationException exception) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Storage operation failed", List.of());
    }

    @ExceptionHandler(RemoteServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleRemoteService(RemoteServiceException exception) {
        return build(HttpStatus.BAD_GATEWAY, exception.getMessage(), List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, List<String> details) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details
        );
        return ResponseEntity.status(status).body(body);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
