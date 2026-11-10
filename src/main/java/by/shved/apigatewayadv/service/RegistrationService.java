package by.shved.apigatewayadv.service;

import by.shved.apigatewayadv.client.AuthenticationServiceClient;
import by.shved.apigatewayadv.client.UserServiceClient;
import by.shved.apigatewayadv.dto.registration.RegistrationRequest;
import by.shved.apigatewayadv.dto.registration.RegistrationResponse;
import by.shved.apigatewayadv.dto.user.UserCredentialsRequest;
import by.shved.apigatewayadv.dto.user.UserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final UserServiceClient userServiceClient;
    private final AuthenticationServiceClient authenticationServiceClient;

    public Mono<RegistrationResponse> register(RegistrationRequest request) {
        UserRequest userRequest = new UserRequest(
                request.name(),
                request.surname(),
                request.birthDate(),
                request.email()
        );
        return userServiceClient.createUser(userRequest)
                .flatMap(user -> {
                    UserCredentialsRequest credentialsRequest = new UserCredentialsRequest(
                            user.id(),
                            request.login(),
                            request.password()
                    );
                    return authenticationServiceClient.saveCredentials(credentialsRequest)
                            .thenReturn(new RegistrationResponse(
                                    user.id(),
                                    user.name(),
                                    user.surname(),
                                    user.birthDate(),
                                    user.email(),
                                    user.active()
                            ))
                            .onErrorResume(error -> rollbackRegistration(user.id(), error));
                });
    }

    private Mono<RegistrationResponse> rollbackRegistration(Long userId, Throwable originalError) {
        return userServiceClient.deleteUser(userId)
                .onErrorResume(rollbackError -> {
                    originalError.addSuppressed(rollbackError);
                    return Mono.empty();
                })
                .then(Mono.error(originalError));
    }
}
