package com.example.notificationservice.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 알림 응답 DTO (폴링용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    /**
     * 알림 목록
     */
    private List<Map<String, Object>> notifications;

    /**
     * 요청한 사용자 ID
     */
    private String userId;

    /**
     * 응답 생성 시간
     */
    private String timestamp;

    /**
     * 더 많은 알림이 있는지 여부
     */
    @Builder.Default
    private Boolean hasMore = false;

    /**
     * 반환된 알림 개수
     */
    private Integer count;

    /**
     * 읽지 않은 알림 개수
     */
    private Integer unreadCount;

    /**
     * 다음 페이지 토큰 (페이징용)
     */
    private String nextPageToken;

    /**
     * 마지막 알림의 타임스탬프 (since 파라미터용)
     */
    private String lastNotificationTime;

    /**
     * 응답 메타데이터
     */
    private ResponseMeta meta;

    /**
     * 응답 메타데이터 내부 클래스
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMeta {

        /**
         * 요청 처리 시간 (밀리초)
         */
        private Long processingTimeMs;

        /**
         * 데이터 소스 (redis, cache, db 등)
         */
        @Builder.Default
        private String dataSource = "redis";

        /**
         * API 버전
         */
        @Builder.Default
        private String apiVersion = "1.0";

        /**
         * 캐시 히트 여부
         */
        @Builder.Default
        private Boolean cacheHit = false;

        /**
         * 서버 시간
         */
        private Long serverTime;

        /**
         * 요청 ID (디버깅용)
         */
        private String requestId;
    }

    /**
     * 읽지 않은 알림 개수 계산
     */
    public void calculateUnreadCount() {
        if (notifications == null) {
            this.unreadCount = 0;
            return;
        }

        this.unreadCount = (int) notifications.stream()
                .filter(notification -> {
                    Object read = notification.get("read");
                    return read == null || !Boolean.parseBoolean(read.toString());
                })
                .count();
    }

    /**
     * 마지막 알림 시간 설정
     */
    public void setLastNotificationTime() {
        if (notifications == null || notifications.isEmpty()) {
            this.lastNotificationTime = null;
            return;
        }

        // 가장 최근 알림의 타임스탬프 사용
        Map<String, Object> lastNotification = notifications.get(0);
        this.lastNotificationTime = (String) lastNotification.get("timestamp");
    }

    /**
     * 응답 검증
     */
    public boolean isValid() {
        return userId != null && !userId.trim().isEmpty()
                && timestamp != null && !timestamp.trim().isEmpty()
                && count != null && count >= 0;
    }

    /**
     * 빈 응답 생성
     */
    public static NotificationResponse empty(String userId, String timestamp) {
        return NotificationResponse.builder()
                .notifications(List.of())
                .userId(userId)
                .timestamp(timestamp)
                .hasMore(false)
                .count(0)
                .unreadCount(0)
                .build();
    }

    /**
     * 요약 정보 (로깅용)
     */
    public String toSummary() {
        return String.format("NotificationResponse{userId='%s', count=%d, unread=%d, hasMore=%s}",
                userId, count, unreadCount, hasMore);
    }
}