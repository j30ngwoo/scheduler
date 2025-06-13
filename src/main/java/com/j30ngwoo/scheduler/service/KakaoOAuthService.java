package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.common.exception.AppException;
import com.j30ngwoo.scheduler.common.exception.ErrorCode;
import com.j30ngwoo.scheduler.domain.User;
import com.j30ngwoo.scheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoOAuthService {
    private final RestClient restClient;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${auth.kakao.client-id}")
    private String clientId;

    @Value("${auth.kakao.client-secret}")
    private String clientSecret;

    @Value("${auth.kakao.redirect-uri}")
    private String redirectUri;

    public KakaoLoginResponse handleKakaoLoginCallback(String code) {
        String accessTokenFromKakao = getKakaoAccessToken(code);
        KakaoUser kakaoUser = getKakaoUserInfo(accessTokenFromKakao);

        User user = userRepository.findByKakaoId(kakaoUser.kakaoId())
                .orElseGet(() -> userRepository.save(
                        new User(null, kakaoUser.kakaoId(), kakaoUser.nickname())
                ));

        String accessToken = authService.createAccessToken(user.getId());
        String refreshToken = authService.createRefreshToken(user.getId());

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Strict")
                .build();

        return new KakaoLoginResponse(accessToken, refreshCookie);
    }

    public String getKakaoAccessToken(String code) {
        String url = "https://kauth.kakao.com/oauth/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("code", code);
        params.add("redirect_uri", redirectUri);
        params.add("client_secret", clientSecret);

        Map<String, Object> body = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(Map.class);

        if (body == null || !body.containsKey("access_token")) {
            throw new AppException(ErrorCode.OAUTH_COMMUNICATION_FAILED);
        }

        return (String) body.get("access_token");
    }

    public KakaoUser getKakaoUserInfo(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";

        Map<String, Object> body = restClient.get()
                .uri(url)
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                    headers.setBearerAuth(accessToken);
                })
                .retrieve()
                .body(Map.class);

        if (body == null || !body.containsKey("id")) {
            throw new AppException(ErrorCode.OAUTH_COMMUNICATION_FAILED);
        }

        Long id = ((Number) body.get("id")).longValue();
        String nickname = ((Map<String, Object>) body.get("properties")).get("nickname").toString();

        return new KakaoUser(id, nickname);
    }

    public record KakaoLoginResponse(String accessToken, ResponseCookie refreshTokenCookie) {}
    public record KakaoUser(Long kakaoId, String nickname) {}
}
