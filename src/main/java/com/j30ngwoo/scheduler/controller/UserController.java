package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.common.response.ApiResponse;
import com.j30ngwoo.scheduler.config.resolver.CurrentUser;
import com.j30ngwoo.scheduler.domain.User;
import com.j30ngwoo.scheduler.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/me")
    public ApiResponse<UserDto> getMyInfo(@CurrentUser User currentUser) {
        return ApiResponse.success(UserDto.from(currentUser));
    }
}
