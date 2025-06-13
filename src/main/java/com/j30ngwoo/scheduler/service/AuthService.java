package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.common.exception.AppException;
import com.j30ngwoo.scheduler.common.exception.ErrorCode;
import com.j30ngwoo.scheduler.domain.RefreshToken;
import com.j30ngwoo.scheduler.repository.RefreshTokenRepository;
import com.j30ngwoo.scheduler.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.secret}")
    private String secretKeyRaw;

    @Value("${jwt.access-expiration}")
    private long accessTokenExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretKeyRaw.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public Long parseSubject(String token) {
        return Long.valueOf(Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject());
    }

    public String createRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusDays(30);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(expiry)
                .user(userRepository.getReferenceById(userId))
                .build();

        return refreshTokenRepository.save(newRefreshToken)
                .getToken();
    }

    public String refreshAccessToken(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        return createAccessToken(token.getUser().getId());
    }

    public void deleteRefreshToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }
}
