package com.rentflow.user_service.dto.user.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.rentflow.user_service.dto.personal_info.response.PersonalInfoResponseDto;
import com.rentflow.user_service.entity.enums.UserStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserResponseDto(

        UUID id,

        PersonalInfoResponseDto personalInfo,

        UserStatus status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {}
