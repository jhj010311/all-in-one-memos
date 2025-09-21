package com.example.eventpublisher.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 로그인 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 사용자 이름 (선택사항, 테스트용)
     */
    private String userName;

    /**
     * 디바이스 정보 (선택사항)
     */
    private String deviceInfo;

    /**
     * 클라이언트 타입 (web, mobile, etc.)
     */
    @Builder.Default
    private String clientType = "web";

    // 실제 운영에서는 password 필드도 있어야 함
    // private String password;
}