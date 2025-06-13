package com.j30ngwoo.scheduler.dto;

import org.springframework.http.ResponseCookie;

public record KakaoLoginResponse(String accessToken, ResponseCookie refreshTokenCookie) {}