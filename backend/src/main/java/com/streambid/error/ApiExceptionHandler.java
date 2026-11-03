package com.streambid.error;

import com.streambid.platform.ChannelNotFoundException;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.PlatformRateLimitException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ChannelNotFoundException.class)
    public ProblemDetail channelNotFound(ChannelNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Channel not found");
    }

    @ExceptionHandler(PlatformUnavailableException.class)
    public ProblemDetail platformUnavailable(PlatformUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Streaming platform unavailable");
    }

    @ExceptionHandler(PlatformRateLimitException.class)
    public ResponseEntity<ProblemDetail> rateLimit(PlatformRateLimitException exception) {
        long seconds = Math.max(1, Duration.between(Instant.now(), exception.retryAt()).getSeconds() + 1);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Streaming platform rate limited");
        problem.setProperty("retryAt", exception.retryAt());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", Long.toString(seconds)).body(problem);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request data");
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField()).distinct().sorted().toList();
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid JSON request");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail requestError(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }
}
