package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.config.resolver.CurrentUser;
import com.j30ngwoo.scheduler.domain.User;
import com.j30ngwoo.scheduler.dto.KakaoLoginResponse;
import com.j30ngwoo.scheduler.service.AuthService;
import com.j30ngwoo.scheduler.service.KakaoOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${auth.kakao.client-id}")
    private String clientId;

    @Value("${auth.kakao.redirect-uri}")
    private String redirectUri;

    private final KakaoOAuthService kakaoOAuthService;
    private final AuthService authService;

    @GetMapping("/kakao/login")
    public ResponseEntity<Void> redirectToKakao() {
        URI kakaoLoginUri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("kauth.kakao.com")
                .path("/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .build(true)
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(kakaoLoginUri)
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoLogin(@RequestParam String code) {
        KakaoLoginResponse response = kakaoOAuthService.handleKakaoLoginCallback(code);
        
        String frontendCallback = "https://scheduler.j30ngwoo.site/login/callback?accessToken=" + response.accessToken();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, response.refreshTokenCookie().toString())
                .location(URI.create(frontendCallback))
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String newAccessToken = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CurrentUser User currentUser,
            @RequestHeader("Authorization") String authorization,
            @CookieValue(value = "refreshToken", required = false) String refreshToken
    ) {
        if (refreshToken != null) {
            authService.deleteRefreshToken(refreshToken);
        }

        if (authorization != null && authorization.startsWith("Bearer ")) {
            String kakaoAccessToken = authorization.substring("Bearer ".length());
            try {
                kakaoOAuthService.kakaoLogout(kakaoAccessToken);
            } catch (Exception e) {
                // 실패해도 무시 (이미 만료일 수도 있음)
            }
        }

        ResponseCookie removeCookie = ResponseCookie.from("refreshToken", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, removeCookie.toString())
                .body(Map.of("message", "로그아웃 완료"));
    }
}
