package com.qinglian.fitness.sensor;

import com.qinglian.fitness.common.ApiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=SensorController.class)
public class SensorErrors {
    private static final Logger log = LoggerFactory.getLogger(SensorErrors.class);
    private Map<String,Object> body(String code,String message) {
        String requestId = UUID.randomUUID().toString();
        log.warn("SENSOR_REQUEST_REJECTED requestId={} code={}",requestId,code);
        return Map.of("ok",false,"code",code,"message",message,"requestId",requestId);
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> business(ApiException error) {
        return ResponseEntity.status(error.status()).body(body(error.code(),error.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentNotValidException.class})
    public ResponseEntity<?> invalid(Exception error) {
        // Do not log or echo raw payloads, which may include pairing credentials.
        return ResponseEntity.badRequest().body(body("INVALID_PAYLOAD","请求格式或必填字段不正确"));
    }
}
