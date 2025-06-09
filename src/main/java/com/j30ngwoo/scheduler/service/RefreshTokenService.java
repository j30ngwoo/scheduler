package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.domain.RefreshToken;
import com.j30ngwoo.scheduler.repository.RefreshTokenRepository;
import com.j30ngwoo.scheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public String generateRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusDays(14);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(expiry)
                .user(userRepository.getReferenceById(userId))
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    public Optional<Long> validateAndGetUserId(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(rt -> rt.getUser().getId());
    }

    public void deleteToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }
}
