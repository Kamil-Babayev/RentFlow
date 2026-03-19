package com.rentflow.user_service.dto.personal_info.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PersonalInfoResponseDto(

        UUID id,

        String email,

        String firstName,

        String lastName,

        LocalDate birthDate,

        String phoneNumber,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {}
