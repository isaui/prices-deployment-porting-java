package com.prices.api.handlers;

import com.prices.api.dto.requests.UpdateUserRequest;
import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.dto.responses.ErrorResponse;
import com.prices.api.dto.responses.UserListResponse;
import com.prices.api.models.User;
import com.prices.api.services.UserService;
import com.prices.api.utils.MapperUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.prices.api.constants.Constants.ROLE_ADMIN;

@Singleton
@RequiredArgsConstructor
public class UserHandler {

    private final UserService userService;

    public HttpResponse<?> getAll(String role) {
        if (!ROLE_ADMIN.equals(role)) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Admin access required"));
        }
        try {
            List<User> users = userService.getAll();
            return HttpResponse.ok(ApiResponse.success("Users retrieved successfully", MapperUtils.toUserListResponse(users)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to get users"));
        }
    }

    public HttpResponse<?> getById(Long id, Long currentUserId, String role) {
        if (!ROLE_ADMIN.equals(role) && !id.equals(currentUserId)) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
        }
        try {
            User user = userService.getById(id);
            if (user == null) {
                return HttpResponse.notFound(ErrorResponse.error("User not found"));
            }
            return HttpResponse.ok(ApiResponse.success("User retrieved successfully", MapperUtils.toUserResponse(user)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error(e.getMessage()));
        }
    }

    public HttpResponse<?> update(Long id, UpdateUserRequest req, Long currentUserId, String role) {
        if (!ROLE_ADMIN.equals(role) && !id.equals(currentUserId)) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
        }
        try {
            User user = userService.update(id, req);
            return HttpResponse.ok(ApiResponse.success("User updated successfully", MapperUtils.toUserResponse(user)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to update user"));
        }
    }

    public HttpResponse<?> delete(Long id, Long currentUserId, String role) {
        if (!ROLE_ADMIN.equals(role) && !id.equals(currentUserId)) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
        }
        try {
            userService.delete(id);
            return HttpResponse.ok(ApiResponse.success("User deleted successfully", null));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to delete user"));
        }
    }
}
