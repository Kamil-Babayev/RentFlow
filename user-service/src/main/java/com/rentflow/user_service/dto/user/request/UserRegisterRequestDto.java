package com.rentflow.user_service.dto.user.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.rentflow.user_service.dto.personal_info.request.PersonalInfoRequestDto;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserRegisterRequestDto(

       PersonalInfoRequestDto personalInfo,

       @NotBlank(message = "Password is required")
       String password

) {}
