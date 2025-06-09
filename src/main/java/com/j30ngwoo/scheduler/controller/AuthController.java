package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.domain.User;
import com.j30ngwoo.scheduler.repository.UserRepository;
import com.j30ngwoo.scheduler.service.JwtProvider;
import com.j30ngwoo.scheduler.service.KakaoOAuthService;
import com.j30ngwoo.scheduler.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
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
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @GetMapping("/kakao/login")
    public ResponseEntity<Void> redirectToKakao() {
        URI kakaoLoginUri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("kauth.kakao.com")
                .path("/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "profile")
                .build(true)
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(kakaoLoginUri)
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<?> kakaoLogin(@RequestParam String code) {
        String accessTokenFromKakao = kakaoOAuthService.getAccessToken(code);
        KakaoOAuthService.KakaoUser kakaoUser = kakaoOAuthService.getUserInfo(accessTokenFromKakao);

        User user = userRepository.findByKakaoId(kakaoUser.kakaoId())
                .orElseGet(() -> userRepository.save(
                        new User(null, kakaoUser.kakaoId(), kakaoUser.nickname())
                ));

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = refreshTokenService.generateRefreshToken(user.getId());

        // HttpOnly 쿠키로 리프레시 토큰 저장
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true) // HTTPS 환경에서만 설정
                .path("/")
                .maxAge(Duration.ofDays(14))
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(Map.of("accessToken", accessToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return refreshTokenService.validateAndGetUserId(refreshToken)
                .map(userId -> {
                    String newAccessToken = jwtProvider.createAccessToken(userId);
                    return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
