package com.sms.apigateway.web;

import com.sms.apigateway.client.UserServiceClient;
import com.sms.apigateway.dto.*;
import com.sms.apigateway.event.UserEvent;
import com.sms.apigateway.producer.AlertForgotPasswordEventProducer;
import com.sms.apigateway.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Issues a JWT for a valid username/password, checked against user-service
 * via AuthService. No configured gateway route predicate matches "/auth/**"
 * on the routes that proxy to plan-catalog-service/subscription-service, and
 * JwtAuthenticationGlobalFilter explicitly exempts "/auth" — so this
 * endpoint is reachable without a token, as it must be to issue the first one.
 *
 * AuthService.login() calls out to user-service over blocking RestTemplate
 * I/O. This is a WebFlux/Netty app with a small, fixed pool of event-loop
 * threads shared by every route this gateway serves — running that blocking
 * call directly on one of them would let a handful of slow/stuck calls to
 * user-service stall unrelated requests across the whole gateway. Offloading
 * it to boundedElastic (a pool sized for exactly this kind of blocking work)
 * keeps the event loop free; migrating UserServiceClient from RestTemplate to
 * WebClient would remove the need for this entirely.
 */
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserServiceClient userServiceClient;
    private final AlertForgotPasswordEventProducer alertForgotPasswordEventProducer;

    public AuthController(AuthService authService, UserServiceClient userServiceClient, AlertForgotPasswordEventProducer alertForgotPasswordEventProducer) {
        this.alertForgotPasswordEventProducer = alertForgotPasswordEventProducer;
        this.authService = authService;
        this.userServiceClient = userServiceClient;
    }

    /*@PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return Mono.fromCallable(() -> authService.login(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }*/
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        return Mono.fromRunnable(() -> authService.logout(authHeader))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {

            String identifier = request.getIdentifier();
            boolean isEmail = identifier.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
            UserResponseDTO user;
            if (isEmail) {
                // Lookup by email
                user = userServiceClient.getByEmail(identifier);
            } else {
                // Lookup by username
                user = userServiceClient.getUserByUsername(identifier);
            }
            // Verify user exists in User Service
            //UserDto users = userServiceClient.findUser(request.getOrgId(), request.getUsername());

            // Build reset link (frontend route)
            String resetLink = "http://localhost:/8090/api/va/users/reset-password/"
                    + user.getUsername();

            // TODO: send resetLink via email/SMS
            UserEvent userEvent =UserEvent.of(
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getOrganizationId()
            );
            alertForgotPasswordEventProducer.alertForgotPassword(userEvent);


            return ResponseEntity.ok(Map.of(
                    "message", "Password reset link sent,Check your email "+user.getEmail(),
                    "resetLink", resetLink
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Failed to process forgot password, Enter regitered email/username"));
        }
    }
}
