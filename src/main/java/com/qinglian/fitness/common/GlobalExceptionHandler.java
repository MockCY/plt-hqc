package com.qinglian.fitness.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.status().is5xxServerError()) {
            log.error("Request failed: method={}, uri={}, status={}, code={}, cause={}",
                request.getMethod(), request.getRequestURI(), exception.status().value(), exception.code(),
                rootCauseMessage(exception), exception);
        } else {
            log.warn("Request rejected: method={}, uri={}, status={}, code={}, cause={}",
                request.getMethod(), request.getRequestURI(), exception.status().value(), exception.code(),
                rootCauseMessage(exception));
        }
        return ResponseEntity.status(exception.status())
            .body(ApiError.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        log.warn("Upload rejected because request is too large: method={}, uri={}, cause={}",
            request.getMethod(), request.getRequestURI(), rootCauseMessage(exception));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiError.of("MEDIA_TOO_LARGE", "文件超过服务器允许的大小，请压缩后重新上传"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .orElse(null);
        String message = fieldError == null
            ? "请求参数不正确"
            : fieldError.getField() + "：" + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("NOT_FOUND", "请求的资源不存在"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", "请求参数格式不正确，请检查字段类型和数值"));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(
        AsyncRequestNotUsableException exception,
        HttpServletRequest request
    ) {
        log.info("Client disconnected before response completed: method={}, uri={}, cause={}",
            request.getMethod(), request.getRequestURI(), rootCauseMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request error: method={}, uri={}, type={}, cause={}",
            request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName(),
            rootCauseMessage(exception), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of("INTERNAL_ERROR", "服务器暂时无法处理请求"));
    }

    private String rootCauseMessage(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        return message == null || message.isBlank()
            ? rootCause.getClass().getSimpleName()
            : message;
    }
}
