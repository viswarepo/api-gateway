package com.sms.apigateway.web;

import com.sms.apigateway.dto.AuthResponse;
import com.sms.apigateway.dto.LoginRequest;
import com.sms.apigateway.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    public AuthController(AuthService authService) {
        this.authService = authService;
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


}
