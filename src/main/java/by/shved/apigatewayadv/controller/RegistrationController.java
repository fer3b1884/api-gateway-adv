package by.shved.apigatewayadv.controller;

import by.shved.apigatewayadv.dto.registration.RegistrationRequest;
import by.shved.apigatewayadv.dto.registration.RegistrationResponse;
import by.shved.apigatewayadv.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/")
@RequiredArgsConstructor
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping("/register")
    public Mono<ResponseEntity<RegistrationResponse>> register(@Valid @RequestBody RegistrationRequest request) {
        return registrationService.register(request)
                .map(response -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response));
    }
}
