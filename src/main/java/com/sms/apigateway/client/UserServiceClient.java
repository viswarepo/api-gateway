package com.sms.apigateway.client;


import com.sms.apigateway.dto.RegisterRequest;
import com.sms.apigateway.dto.UserDto;
import com.sms.apigateway.dto.UserResponseDTO;
import com.sms.apigateway.exception.InvalidCredentialsException;
import com.sms.apigateway.exception.UserServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Talks to user-service over plain HTTP using RestTemplate. This is the
 * component auth-service uses to fetch user details (including the
 * password hash) for authentication.
 */
@Component
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user-service.url}")
    private String userServiceBaseUrl;

    @Value("${internal.api-key}")
    private String internalApiKey;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public UserDto register(RegisterRequest request) {
        String url = userServiceBaseUrl + "/api/users/register";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RegisterRequest> entity = new HttpEntity<>(request, headers);

            var response = restTemplate.postForEntity(url, entity, UserDto.class);
            return response.getBody();
        } catch (HttpClientErrorException.Conflict e) {
            throw e; // let GlobalExceptionHandler / caller decide; username or email already taken
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("user-service unreachable while registering user", e);
            throw new UserServiceUnavailableException("user-service is currently unavailable", e);
        }
    }

    public UserDto getByUsername(String username) {
        String url = userServiceBaseUrl + "/api/v1/users" + username;
        try {
            return restTemplate.getForObject(url, UserDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new InvalidCredentialsException("User not found: " + username);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("user-service unreachable while fetching user {}", username, e);
            throw new UserServiceUnavailableException("user-service is currently unavailable", e);
        }
    }

    /**
     * Fetches the password hash + roles for the given username from
     * user-service's internal endpoint, attaching the shared-secret header
     * required by user-service's InternalApiKeyFilter.
     */
    public UserResponseDTO getCredentials(String username) {
        String url = userServiceBaseUrl + "/api/v1/users/info?username="+username;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            Map<String, String> params = new HashMap<>();
            params.put("username", username);

            //ResponseEntity<UserResponseDTO> response = restTemplate.getForEntity(url, UserResponseDTO.class,params);
            ResponseEntity<UserResponseDTO> response = restTemplate.exchange(url, HttpMethod.GET, entity, UserResponseDTO.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            // Never reveal to the caller whether the username itself exists.
            log.warn("Login attempt for unknown username: {}", username);
            throw new InvalidCredentialsException("Invalid username or password");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.error("auth-service is not authorized to call user-service internal API - check internal.api-key");
                throw new UserServiceUnavailableException(
                        "Auth service is not authorized to call user-service", e);
            }
            throw e;
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("user-service unreachable while fetching credentials for {}", username, e);
            throw new UserServiceUnavailableException("user-service is currently unavailable", e);
        }
    }
}
