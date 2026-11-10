package by.shved.apigatewayadv.service;

import by.shved.apigatewayadv.client.AuthenticationServiceClient;
import by.shved.apigatewayadv.client.UserServiceClient;
import by.shved.apigatewayadv.dto.registration.RegistrationRequest;
import by.shved.apigatewayadv.dto.registration.RegistrationResponse;
import by.shved.apigatewayadv.dto.user.UserCredentialsRequest;
import by.shved.apigatewayadv.dto.user.UserRequest;
import by.shved.apigatewayadv.dto.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private AuthenticationServiceClient authenticationServiceClient;
    @InjectMocks
    private RegistrationService registrationService;

    private RegistrationRequest registrationRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        registrationRequest = new RegistrationRequest(
                "Ivan",
                "Ivanov",
                LocalDate.of(2000, 1, 1),
                "ivan@example.com",
                "ivan123",
                "password123"
        );
        userResponse = new UserResponse(
                1L,
                "Ivan",
                "Ivanov",
                LocalDate.of(2000, 1, 1),
                "ivan@example.com",
                true
        );
    }

    @Test
    void register_shouldReturnRegistrationResponse_whenRegistrationIsSuccessful() {
        when(userServiceClient.createUser(any(UserRequest.class)))
                .thenReturn(Mono.just(userResponse));
        when(authenticationServiceClient.saveCredentials(any(UserCredentialsRequest.class)))
                .thenReturn(Mono.empty());

        Mono<RegistrationResponse> result = registrationService.register(registrationRequest);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(userResponse.id(), response.id());
                    assertEquals(userResponse.name(), response.name());
                    assertEquals(userResponse.surname(), response.surname());
                    assertEquals(userResponse.birthDate(), response.birthDate());
                    assertEquals(userResponse.email(), response.email());
                    assertEquals(userResponse.active(), response.active());
                })
                .verifyComplete();
        verify(userServiceClient, times(1)).createUser(any(UserRequest.class));
        verify(authenticationServiceClient, times(1)).saveCredentials(any(UserCredentialsRequest.class));
        verify(userServiceClient, never()).deleteUser(anyLong());
    }

    @Test
    void register_shouldPropagateError_whenUserCreationFails() {
        RuntimeException expectedException = new RuntimeException("User Service is unavailable");
        when(userServiceClient.createUser(any(UserRequest.class)))
                .thenReturn(Mono.error(expectedException));

        Mono<RegistrationResponse> result = registrationService.register(registrationRequest);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error == expectedException)
                .verify();
        verify(userServiceClient, times(1)).createUser(any(UserRequest.class));
        verify(authenticationServiceClient, never()).saveCredentials(any(UserCredentialsRequest.class));
        verify(userServiceClient, never()).deleteUser(anyLong());
    }

    @Test
    void register_shouldDeleteCreatedUser_whenCredentialsSavingFails() {
        RuntimeException expectedException = new RuntimeException("Credentials saving failed");
        when(userServiceClient.createUser(any(UserRequest.class)))
                .thenReturn(Mono.just(userResponse));
        when(authenticationServiceClient.saveCredentials(any(UserCredentialsRequest.class)))
                .thenReturn(Mono.error(expectedException));
        when(userServiceClient.deleteUser(userResponse.id()))
                .thenReturn(Mono.empty());

        Mono<RegistrationResponse> result = registrationService.register(registrationRequest);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error == expectedException)
                .verify();
        verify(userServiceClient, times(1)).createUser(any(UserRequest.class));
        verify(authenticationServiceClient, times(1)).saveCredentials(any(UserCredentialsRequest.class));
        verify(userServiceClient, times(1)).deleteUser(userResponse.id());
    }

    @Test
    void register_shouldPropagateOriginalError_whenRollbackSucceeds() {
        RuntimeException credentialsException = new RuntimeException("Credentials saving failed");
        when(userServiceClient.createUser(any(UserRequest.class)))
                .thenReturn(Mono.just(userResponse));
        when(authenticationServiceClient.saveCredentials(any(UserCredentialsRequest.class)))
                .thenReturn(Mono.error(credentialsException));
        when(userServiceClient.deleteUser(userResponse.id()))
                .thenReturn(Mono.empty());

        Mono<RegistrationResponse> result = registrationService.register(registrationRequest);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error == credentialsException)
                .verify();
        assertEquals(0, credentialsException.getSuppressed().length);
        verify(userServiceClient).deleteUser(userResponse.id());
    }

    @Test
    void register_shouldAddRollbackErrorAsSuppressed_whenRollbackFails() {
        RuntimeException credentialsException = new RuntimeException("Credentials saving failed");
        RuntimeException rollbackException = new RuntimeException("User deletion failed");
        when(userServiceClient.createUser(any(UserRequest.class)))
                .thenReturn(Mono.just(userResponse));
        when(authenticationServiceClient.saveCredentials(any(UserCredentialsRequest.class)))
                .thenReturn(Mono.error(credentialsException));
        when(userServiceClient.deleteUser(userResponse.id()))
                .thenReturn(Mono.error(rollbackException));

        Mono<RegistrationResponse> result = registrationService.register(registrationRequest);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error == credentialsException)
                .verify();
        assertEquals(1, credentialsException.getSuppressed().length);
        assertSame(rollbackException, credentialsException.getSuppressed()[0]);
        verify(userServiceClient).deleteUser(userResponse.id());
    }
}