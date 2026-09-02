package com.sms.apigateway.web;

import com.sms.apigateway.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

/**
 * Targets of the CircuitBreaker filter's fallbackUri (see application.yml).
 * Reached when the corresponding downstream service is down or the circuit
 * is open, so callers get a fast, well-formed 503 instead of a hanging
 * connection or a raw connection-refused error. Mapped to every HTTP method
 * since a "forward:" fallback preserves the original request's method.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(value = "/catalog", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ErrorResponse> catalogUnavailable(ServerWebExchange exchange) {
        return unavailable(exchange, "Plan & Catalog Service is currently unavailable. Please try again shortly.");
    }

    @RequestMapping(value = "/subscription", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ErrorResponse> subscriptionUnavailable(ServerWebExchange exchange) {
        return unavailable(exchange, "Subscription Service is currently unavailable. Please try again shortly.");
    }

    private ResponseEntity<ErrorResponse> unavailable(ServerWebExchange exchange, String message) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
