package com.rentflow.user_service.mapper;

import com.rentflow.user_service.dto.user.response.UserResponseDto;
import com.rentflow.user_service.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponseDto toResponseDto(User user);

}
