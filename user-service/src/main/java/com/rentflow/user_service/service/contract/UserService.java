package com.rentflow.user_service.service.contract;

import com.rentflow.user_service.dto.user.request.UpdatePasswordRequest;
import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.response.UserResponseDto;
import jakarta.validation.Valid;

import java.util.UUID;

public interface UserService {

    UserResponseDto registerUser(@Valid UserRegisterRequestDto request);

    UserResponseDto getCurrentUser();

    void updatePassword(@Valid UpdatePasswordRequest request);

    void deleteCurrentUser();

    void hardDeleteUser(UUID userId);
}
