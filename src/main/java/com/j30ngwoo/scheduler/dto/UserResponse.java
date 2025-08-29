package com.j30ngwoo.scheduler.dto;

import com.j30ngwoo.scheduler.domain.User;

public record UserResponse(
        Long id,
        String name
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName());
    }
}
