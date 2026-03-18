package com.rentflow.user_service.dto.user.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdatePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword,

        @NotBlank(message = "Confirmation is required")
        String confirmPassword

) {
        public UpdatePasswordRequest {
                if (!newPassword.equals(confirmPassword)) {
                        throw new IllegalArgumentException("Passwords do not match");
                }
        }

}
