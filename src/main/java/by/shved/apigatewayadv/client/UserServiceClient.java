package by.shved.apigatewayadv.client;

import by.shved.apigatewayadv.dto.user.UserRequest;
import by.shved.apigatewayadv.dto.user.UserResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class UserServiceClient {
    private final WebClient userWebClient;
    private final String internalSecret;

    public UserServiceClient(@Qualifier("userWebClient") WebClient userWebClient,
                             @Value("${app.internal.secret}") String internalSecret) {
        this.userWebClient = userWebClient;
        this.internalSecret = internalSecret;
    }

    public Mono<UserResponse> createUser(UserRequest request) {
        return userWebClient.post()
                .uri("/api/v1/users")
                .header("X-Internal-Secret", internalSecret)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class);
    }

    public Mono<Void> deleteUser(Long userId) {
        return userWebClient.delete()
                .uri("/api/v1/users/{id}", userId)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
