package by.shved.apigatewayadv.dto.registration;

import java.time.LocalDate;

public record RegistrationResponse(
        Long id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        boolean active
) {
}