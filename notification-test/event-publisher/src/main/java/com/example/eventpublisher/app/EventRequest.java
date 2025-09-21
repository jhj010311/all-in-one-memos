package com.example.eventpublisher.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 이벤트 발행 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequest {

    /**
     * 대상 사용자 ID
     * "all" = 전체 브로드캐스트
     * 특정 userId = 개인 메시지
     */
    private String targetUserId;

    /**
     * 이벤트 타입
     */
    @Builder.Default
    private String eventType = "notification";

    /**
     * 메시지 내용
     */
    private String message;

    /**
     * 메시지 제목 (선택사항)
     */
    private String title;

    /**
     * 우선순위 (HIGH, NORMAL, LOW)
     */
    @Builder.Default
    private String priority = "NORMAL";

    /**
     * 카테고리 (알림 분류용)
     */
    private String category;

    /**
     * 추가 데이터 (JSON 형태의 확장 정보)
     */
    private Map<String, Object> additionalData;

    /**
     * 즉시 발송 여부
     */
    @Builder.Default
    private Boolean immediate = true;

    /**
     * 푸시 알림 포함 여부 (향후 확장용)
     */
    @Builder.Default
    private Boolean includePush = false;

    /**
     * 이메일 알림 포함 여부 (향후 확장용)
     */
    @Builder.Default
    private Boolean includeEmail = false;

    /**
     * 알림 만료 시간 (초) - 설정하지 않으면 만료되지 않음
     */
    private Long ttlSeconds;

    /**
     * 전체 브로드캐스트 여부 체크
     */
    public boolean isBroadcast() {
        return "all".equalsIgnoreCase(targetUserId);
    }

    /**
     * 개인 메시지 여부 체크
     */
    public boolean isPersonalMessage() {
        return !isBroadcast();
    }

    /**
     * 높은 우선순위 여부 체크
     */
    public boolean isHighPriority() {
        return "HIGH".equalsIgnoreCase(priority);
    }

    /**
     * 요청 검증
     */
    public boolean isValid() {
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            return false;
        }
        if (eventType == null || eventType.trim().isEmpty()) {
            return false;
        }
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * 디버깅용 간단 정보
     */
    public String toSimpleString() {
        return String.format("EventRequest{target='%s', type='%s', message='%s'}",
                targetUserId, eventType,
                message.length() > 50 ? message.substring(0, 50) + "..." : message);
    }
}