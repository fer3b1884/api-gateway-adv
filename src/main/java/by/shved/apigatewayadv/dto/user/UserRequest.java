package by.shved.apigatewayadv.dto.user;

import java.time.LocalDate;

public record UserRequest(
        String name,
        String surname,
        LocalDate birthDate,
        String email
) {
}
