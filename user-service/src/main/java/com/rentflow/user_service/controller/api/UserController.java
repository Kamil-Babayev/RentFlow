package com.rentflow.user_service.controller.api;

import com.rentflow.user_service.dto.user.request.UpdatePasswordRequest;
import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.response.UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/api/v1/users")
public interface UserController {

    @Operation(
            summary = "Register a new user",
            description = "Endpoint to register a new user",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "User registered successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input data",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))),
            })
    @PostMapping("/register")
    ResponseEntity<UserResponseDto> registerUser(@Valid @RequestBody UserRegisterRequestDto request);

    @Operation(
            summary = "Get current user details",
            description = "Endpoint to retrieve details of the currently authenticated user",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "User details retrieved successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponseDto.class))),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized access",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))),
            })
    @PostMapping("/me")
    ResponseEntity<UserResponseDto> getCurrentUser();

    @Operation(
            summary = "Update current user's password",
            description = "Endpoint to update the password of the currently authenticated user",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Password updated successfully"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input data",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized access",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping("/me/password")
    ResponseEntity<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request);

    @Operation(
            summary = "Delete current user",
            description = "Endpoint to delete the currently authenticated user",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "User deleted successfully"),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized access",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/me/delete")
    ResponseEntity<Void> deleteCurrentUser();

    @Operation(
            summary = "Hard delete a user",
            description = "Endpoint to permanently delete a user by their ID",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "User hard deleted successfully"),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized access",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(
                            responseCode = "404",
                            description = "User not found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)))
            })
    @DeleteMapping("{userId}/hard")
    ResponseEntity<Void> hardDeleteUser(@PathVariable UUID userId);

}
