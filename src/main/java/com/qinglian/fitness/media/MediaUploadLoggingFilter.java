package com.qinglian.fitness.media;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MediaUploadLoggingFilter extends OncePerRequestFilter {

    static final String UPLOAD_REQUEST_ID_MDC_KEY = "mediaUploadRequestId";
    private static final String UPLOAD_REQUEST_ID_HEADER = "X-Upload-Request-Id";
    private static final Set<String> UPLOAD_PATHS = Set.of(
        "/api/admin/media/upload",
        "/api/media/upload"
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaUploadLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !UPLOAD_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        MDC.put(UPLOAD_REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(UPLOAD_REQUEST_ID_HEADER, requestId);
        LOGGER.info("Media upload request started: requestId={}, method={}, uri={}, contentLength={}, contentType={}",
            requestId, request.getMethod(), request.getRequestURI(), request.getContentLengthLong(), request.getContentType());
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            LOGGER.error("Media upload request escaped request handling: requestId={}, method={}, uri={}, type={}, cause={}",
                requestId, request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName(),
                rootCauseMessage(exception), exception);
            throw exception;
        } finally {
            LOGGER.info("Media upload request completed: requestId={}, method={}, uri={}, status={}, elapsedMs={}, responseCommitted={}",
                requestId, request.getMethod(), request.getRequestURI(), response.getStatus(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), response.isCommitted());
            MDC.remove(UPLOAD_REQUEST_ID_MDC_KEY);
        }
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
