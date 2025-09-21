package com.example.eventpublisher.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JWT 토큰 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    /**
     * JWT 액세스 토큰
     */
    private String token;

    /**
     * 토큰 타입 (일반적으로 "Bearer")
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * 토큰 만료 시간 (초)
     */
    private Long expiresIn;

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 토큰 발급 시간
     */
    @Builder.Default
    private String issuedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    /**
     * 토큰 만료 예정 시간
     */
    private String expiresAt;

    /**
     * 리프레시 토큰 (향후 확장용)
     */
    private String refreshToken;

    /**
     * 사용자 권한 정보 (향후 확장용)
     */
    private String[] authorities;

    /**
     * 토큰 발급 후 만료 시간 자동 계산
     */
    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
        if (expiresIn != null) {
            this.expiresAt = LocalDateTime.now()
                    .plusSeconds(expiresIn)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    /**
     * Authorization 헤더 형태로 토큰 반환
     */
    public String getAuthorizationHeader() {
        return tokenType + " " + token;
    }

    /**
     * 토큰 유효성 빠른 체크 (클라이언트용)
     */
    public boolean isValid() {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        if (expiresAt == null) {
            return true; // 만료시간이 없으면 유효하다고 가정
        }

        try {
            LocalDateTime expiry = LocalDateTime.parse(expiresAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return LocalDateTime.now().isBefore(expiry);
        } catch (Exception e) {
            return false;
        }
    }
}