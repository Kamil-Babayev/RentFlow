package com.rentflow.user_service.dto.personal_info.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PersonalInfoRequestDto(

        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotNull(message = "Birth date is required")
        LocalDate birthDate,

        @NotBlank(message = "Phone is required")
        @Pattern(
                regexp = "^\\+?[1-9]\\d{6,14}$",
                message = "Invalid phone number"
        )
        String phoneNumber
){}
