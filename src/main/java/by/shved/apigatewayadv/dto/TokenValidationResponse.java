package by.shved.apigatewayadv.dto;

public record TokenValidationResponse(
        boolean valid,
        Long userId,
        String role
) {
}
