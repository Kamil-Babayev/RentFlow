package com.rentflow.user_service.service.impl;

import com.rentflow.user_service.dto.user.request.UpdatePasswordRequest;
import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.response.UserResponseDto;
import com.rentflow.user_service.service.contract.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Override
    public UserResponseDto registerUser(UserRegisterRequestDto request) {
        return null;
    }

    @Override
    public UserResponseDto getCurrentUser() {
        return null;
    }

    @Override
    public void updatePassword(UpdatePasswordRequest request) {

    }

    @Override
    public void deleteCurrentUser() {

    }

    @Override
    public void hardDeleteUser(UUID userId) {

    }

}
