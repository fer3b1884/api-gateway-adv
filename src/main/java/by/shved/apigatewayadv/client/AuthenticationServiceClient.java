package by.shved.apigatewayadv.client;

import by.shved.apigatewayadv.dto.TokenValidationResponse;
import by.shved.apigatewayadv.dto.user.UserCredentialsRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationServiceClient {
    private final WebClient authenticationWebClient;

    public AuthenticationServiceClient(@Qualifier("authenticationWebClient") WebClient authenticationWebClient) {
        this.authenticationWebClient = authenticationWebClient;
    }

    public Mono<TokenValidationResponse> validateToken(String authorizationHeader) {
        return authenticationWebClient.post()
                .uri("/api/v1/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .onStatus(
                        status -> status.value() == 401,
                        response -> Mono.empty()
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response -> response.createException()
                                .flatMap(Mono::error)
                )
                .bodyToMono(TokenValidationResponse.class)
                .defaultIfEmpty(new TokenValidationResponse(false, null, null));
    }

    public Mono<Void> saveCredentials(UserCredentialsRequest request) {
        return authenticationWebClient.post()
                .uri("/api/v1/auth/credentials")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
