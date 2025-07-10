package com.j30ngwoo.scheduler.dto;

import com.j30ngwoo.scheduler.domain.User;

public record UserDto(
        Long id,
        String name
) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName());
    }
}
