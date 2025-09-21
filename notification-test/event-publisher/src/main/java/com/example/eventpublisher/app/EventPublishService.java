package com.example.eventpublisher.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublishService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 채널명
    private static final String NOTIFICATION_CHANNEL = "notifications";

    // 알림 저장 키 패턴
    private static final String USER_NOTIFICATIONS_KEY = "user:%s:notifications";
    private static final String NOTIFICATION_DETAIL_KEY = "notification:%s:%s";

    // 기본 TTL (10분)
    private static final long DEFAULT_TTL_SECONDS = 600;

    /**
     * 이벤트 발행 - 메인 메소드
     */
    public boolean publishEvent(String eventId, String targetUserId, String eventType, String message, String publisherId) {
        try {
            // 이벤트 객체 생성
            Map<String, Object> event = createEventObject(eventId, targetUserId, eventType, message, publisherId);

            // JSON 직렬화
            String eventJson = objectMapper.writeValueAsString(event);

            // Redis에 이벤트 발행 (SSE용)
            redisTemplate.convertAndSend(NOTIFICATION_CHANNEL, eventJson);
            log.info("📡 이벤트 발행 완료: eventId={}, target={}", eventId, targetUserId);

            // 폴링용 저장
            saveEventForPolling(eventId, targetUserId, eventJson);

            return true;

        } catch (Exception e) {
            log.error("이벤트 발행 실패: eventId={}, target={}", eventId, targetUserId, e);
            return false;
        }
    }

    /**
     * 개인 메시지 발행
     */
    public boolean publishPersonalMessage(String targetUserId, String message, String publisherId) {
        String eventId = generateEventId();
        return publishEvent(eventId, targetUserId, "personal-message", message, publisherId);
    }

    /**
     * 전체 브로드캐스트 발행
     */
    public boolean publishBroadcast(String message, String publisherId) {
        String eventId = generateEventId();
        return publishEvent(eventId, "all", "broadcast", message, publisherId);
    }

    /**
     * 긴급 알림 발행 (높은 우선순위)
     */
    public boolean publishUrgentNotification(String targetUserId, String title, String message, String publisherId) {
        try {
            String eventId = generateEventId();

            Map<String, Object> event = createEventObject(eventId, targetUserId, "urgent-notification", message, publisherId);
            event.put("priority", "HIGH");
            event.put("title", title);
            event.put("urgent", true);

            String eventJson = objectMapper.writeValueAsString(event);

            // 긴급 알림은 별도 채널로도 발송
            redisTemplate.convertAndSend(NOTIFICATION_CHANNEL, eventJson);
            redisTemplate.convertAndSend("urgent-notifications", eventJson);

            saveEventForPolling(eventId, targetUserId, eventJson);

            log.warn("🚨 긴급 알림 발행: eventId={}, target={}, title={}", eventId, targetUserId, title);
            return true;

        } catch (Exception e) {
            log.error("긴급 알림 발행 실패: target={}", targetUserId, e);
            return false;
        }
    }

    /**
     * 시스템 알림 발행 (관리자용)
     */
    public boolean publishSystemNotification(String message, String publisherId) {
        try {
            String eventId = generateEventId();

            Map<String, Object> event = createEventObject(eventId, "all", "system-notification", message, publisherId);
            event.put("category", "system");
            event.put("priority", "HIGH");
            event.put("system", true);

            String eventJson = objectMapper.writeValueAsString(event);

            redisTemplate.convertAndSend(NOTIFICATION_CHANNEL, eventJson);

            // 시스템 알림은 모든 사용자에게 저장 (실제로는 활성 사용자 목록 필요)
            // 여기서는 간단히 브로드캐스트로 처리
            saveEventForPolling(eventId, "all", eventJson);

            log.info("⚙️ 시스템 알림 발행: eventId={}, message={}", eventId, message);
            return true;

        } catch (Exception e) {
            log.error("시스템 알림 발행 실패", e);
            return false;
        }
    }

    /**
     * 대량 개인 메시지 발행
     */
    public int publishBulkPersonalMessages(String[] targetUserIds, String message, String publisherId) {
        int successCount = 0;

        for (String userId : targetUserIds) {
            try {
                if (publishPersonalMessage(userId, message, publisherId)) {
                    successCount++;
                }

                // 과부하 방지를 위한 짧은 딜레이
                Thread.sleep(50);

            } catch (Exception e) {
                log.error("대량 발송 중 오류: userId={}", userId, e);
            }
        }

        log.info("📨 대량 개인 메시지 발행 완료: 성공={}/{}, publisher={}",
                successCount, targetUserIds.length, publisherId);

        return successCount;
    }

    /**
     * 이벤트 통계 조회 (관리자용)
     */
    public Map<String, Object> getEventStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Redis 연결 상태 체크
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            stats.put("redisConnection", "PONG".equals(pingResult) ? "OK" : "ERROR");

            // 간단한 통계 정보
            stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.put("service", "event-publisher");
            stats.put("status", "running");

            // 실제 운영에서는 더 상세한 통계 정보 제공
            // - 발행된 이벤트 수
            // - 활성 구독자 수
            // - 처리 시간 등

        } catch (Exception e) {
            log.error("이벤트 통계 조회 실패", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    // === 내부 헬퍼 메소드들 ===

    /**
     * 이벤트 객체 생성
     */
    private Map<String, Object> createEventObject(String eventId, String targetUserId, String eventType, String message, String publisherId) {
        Map<String, Object> event = new HashMap<>();

        // 기본 정보
        event.put("id", eventId);
        event.put("type", eventType);
        event.put("targetUserId", targetUserId);
        event.put("message", message);
        event.put("publisherId", publisherId);

        // 시간 정보
        LocalDateTime now = LocalDateTime.now();
        event.put("timestamp", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        event.put("eventTime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 메타데이터
        event.put("priority", "NORMAL");
        event.put("category", "general");
        event.put("broadcast", "all".equals(targetUserId));

        // 사용자 정보 (테스트용 - 실제로는 사용자 서비스에서 조회)
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("userId", targetUserId);
        userInfo.put("displayName", getUserDisplayName(targetUserId));
        event.put("userInfo", userInfo);

        // 발행자 정보
        Map<String, String> publisherInfo = new HashMap<>();
        publisherInfo.put("userId", publisherId);
        publisherInfo.put("displayName", getUserDisplayName(publisherId));
        event.put("publisherInfo", publisherInfo);

        return event;
    }

    /**
     * 폴링용 이벤트 저장
     */
    private void saveEventForPolling(String eventId, String targetUserId, String eventJson) {
        try {
            if ("all".equals(targetUserId)) {
                // 브로드캐스트는 공통 저장소에 저장
                redisTemplate.opsForList().leftPush("broadcast:notifications", eventJson);
                redisTemplate.expire("broadcast:notifications", DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
            } else {
                // 개인 알림은 개인 저장소에 저장
                String userKey = String.format(USER_NOTIFICATIONS_KEY, targetUserId);
                String detailKey = String.format(NOTIFICATION_DETAIL_KEY, targetUserId, eventId);

                // 사용자 알림 목록에 추가 (최신 10개만 유지)
                redisTemplate.opsForList().leftPush(userKey, eventJson);
                redisTemplate.opsForList().trim(userKey, 0, 9);
                redisTemplate.expire(userKey, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);

                // 상세 정보 저장
                redisTemplate.opsForValue().set(detailKey, eventJson, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
            }

            log.debug("💾 폴링용 이벤트 저장 완료: eventId={}, target={}", eventId, targetUserId);

        } catch (Exception e) {
            log.error("폴링용 이벤트 저장 실패: eventId={}, target={}", eventId, targetUserId, e);
        }
    }

    /**
     * 이벤트 ID 생성
     */
    private String generateEventId() {
        return "evt_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    /**
     * 사용자 표시명 조회 (테스트용 - 실제로는 사용자 서비스 연동)
     */
    private String getUserDisplayName(String userId) {
        // 테스트용 하드코딩
        switch (userId) {
            case "alice": return "앨리스";
            case "bob": return "밥";
            case "admin": return "관리자";
            case "all": return "전체사용자";
            default: return userId + "님";
        }
    }
}