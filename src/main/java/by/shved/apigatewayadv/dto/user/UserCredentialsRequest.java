package by.shved.apigatewayadv.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UserCredentialsRequest(
        @NotNull(message = "User ID must not be null")
        @Positive(message = "User ID must be positive")
        Long userId,

        @NotBlank(message = "Login must not be blank")
        @Size(max = 100, message = "Login must not exceed 100 characters")
        String login,

        @NotBlank(message = "Password must not be blank")
        @Size(max = 100, message = "Password must not exceed 100 characters")
        String password
) {
}

