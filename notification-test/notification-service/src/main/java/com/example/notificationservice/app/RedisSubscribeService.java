package com.example.notificationservice.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSubscribeService implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 처리 통계
    private final AtomicLong totalProcessedMessages = new AtomicLong(0);
    private final AtomicLong successfulMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);

    // 구독할 채널들
    private static final String NOTIFICATION_CHANNEL = "notifications";
    private static final String URGENT_NOTIFICATION_CHANNEL = "urgent-notifications";
    private static final String SYSTEM_NOTIFICATION_CHANNEL = "system-notifications";

    @Value("${app.redis.subscribe.enabled:true}")
    private boolean subscribeEnabled;

    /**
     * 스프링 컨텍스트 초기화 완료 후 Redis 채널 구독 시작
     */
    @EventListener(ContextRefreshedEvent.class)
    public void startSubscription() {
        if (!subscribeEnabled) {
            log.info("Redis 구독이 비활성화되어 있습니다");
            return;
        }

        try {
            // 일반 알림 채널 구독
            redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(NOTIFICATION_CHANNEL));
            log.info("📻 Redis 구독 시작: {}", NOTIFICATION_CHANNEL);

            // 긴급 알림 채널 구독
            redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(URGENT_NOTIFICATION_CHANNEL));
            log.info("📻 Redis 구독 시작: {}", URGENT_NOTIFICATION_CHANNEL);

            // 시스템 알림 채널 구독 (선택사항)
            redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(SYSTEM_NOTIFICATION_CHANNEL));
            log.info("📻 Redis 구독 시작: {}", SYSTEM_NOTIFICATION_CHANNEL);

            log.info("🎯 Redis 메시지 구독 초기화 완료");

        } catch (Exception e) {
            log.error("Redis 구독 초기화 실패", e);
        }
    }

    /**
     * 스프링 컨텍스트 종료 시 구독 해제
     */
    @EventListener(ContextClosedEvent.class)
    public void stopSubscription() {
        try {
            redisMessageListenerContainer.stop();
            log.info("📻 Redis 구독 종료 완료");

            // 최종 통계 로그
            log.info("📊 Redis 메시지 처리 통계 - 총 처리: {}, 성공: {}, 실패: {}",
                    totalProcessedMessages.get(), successfulMessages.get(), failedMessages.get());

        } catch (Exception e) {
            log.error("Redis 구독 종료 중 오류", e);
        }
    }

    /**
     * Redis 메시지 수신 처리 (MessageListener 인터페이스 구현)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        totalProcessedMessages.incrementAndGet();

        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            log.debug("📨 Redis 메시지 수신: channel={}, bodyLength={}", channel, body.length());

            // JSON 파싱
            Map<String, Object> notification = objectMapper.readValue(body, Map.class);

            // 채널별 처리
            switch (channel) {
                case NOTIFICATION_CHANNEL:
                    handleGeneralNotification(notification);
                    break;
                case URGENT_NOTIFICATION_CHANNEL:
                    handleUrgentNotification(notification);
                    break;
                case SYSTEM_NOTIFICATION_CHANNEL:
                    handleSystemNotification(notification);
                    break;
                default:
                    log.warn("알 수 없는 채널에서 메시지 수신: {}", channel);
            }

            successfulMessages.incrementAndGet();

        } catch (Exception e) {
            failedMessages.incrementAndGet();
            log.error("Redis 메시지 처리 실패", e);
        }
    }

    /**
     * 일반 알림 처리
     */
    private void handleGeneralNotification(Map<String, Object> notification) {
        try {
            String targetUserId = (String) notification.get("targetUserId");
            String eventType = (String) notification.get("type");
            String eventId = (String) notification.get("id");

            log.debug("📋 일반 알림 처리: eventId={}, target={}, type={}", eventId, targetUserId, eventType);

            // 브로드캐스트 vs 개인 메시지 처리
            if ("all".equals(targetUserId)) {
                handleBroadcastNotification(notification);
            } else {
                handlePersonalNotification(notification);
            }

        } catch (Exception e) {
            log.error("일반 알림 처리 실패", e);
        }
    }

    /**
     * 긴급 알림 처리 (높은 우선순위)
     */
    private void handleUrgentNotification(Map<String, Object> notification) {
        try {
            String targetUserId = (String) notification.get("targetUserId");
            String eventId = (String) notification.get("id");

            log.warn("🚨 긴급 알림 처리: eventId={}, target={}", eventId, targetUserId);

            // 긴급 알림 표시 추가
            notification.put("urgent", true);
            notification.put("priority", "HIGH");

            // 즉시 처리
            if ("all".equals(targetUserId)) {
                int sentCount = notificationService.broadcastToAll(notification);
                log.warn("🚨 긴급 브로드캐스트 완료: eventId={}, 전송={}", eventId, sentCount);
            } else {
                boolean sent = notificationService.sendToUser(targetUserId, notification);
                log.warn("🚨 긴급 개인 알림 완료: eventId={}, target={}, 전송={}", eventId, targetUserId, sent);
            }

        } catch (Exception e) {
            log.error("긴급 알림 처리 실패", e);
        }
    }

    /**
     * 시스템 알림 처리 (관리자 메시지 등)
     */
    private void handleSystemNotification(Map<String, Object> notification) {
        try {
            String eventId = (String) notification.get("id");

            log.info("⚙️ 시스템 알림 처리: eventId={}", eventId);

            // 시스템 알림 표시 추가
            notification.put("system", true);
            notification.put("category", "system");

            // 모든 연결된 사용자에게 전송
            int sentCount = notificationService.broadcastToAll(notification);
            log.info("⚙️ 시스템 알림 브로드캐스트 완료: eventId={}, 전송={}", eventId, sentCount);

        } catch (Exception e) {
            log.error("시스템 알림 처리 실패", e);
        }
    }

    /**
     * 브로드캐스트 알림 처리
     */
    private void handleBroadcastNotification(Map<String, Object> notification) {
        try {
            String eventId = (String) notification.get("id");

            log.info("📢 브로드캐스트 알림 처리: eventId={}", eventId);

            // 모든 연결된 사용자에게 SSE 전송
            int sentCount = notificationService.broadcastToAll(notification);

            log.info("📢 브로드캐스트 완료: eventId={}, SSE 전송={}", eventId, sentCount);

        } catch (Exception e) {
            log.error("브로드캐스트 알림 처리 실패", e);
        }
    }

    /**
     * 개인 알림 처리
     */
    private void handlePersonalNotification(Map<String, Object> notification) {
        try {
            String targetUserId = (String) notification.get("targetUserId");
            String eventId = (String) notification.get("id");
            String eventType = (String) notification.get("type");

            log.debug("👤 개인 알림 처리: eventId={}, target={}, type={}", eventId, targetUserId, eventType);

            // 대상 사용자에게만 SSE 전송
            boolean sent = notificationService.sendToUser(targetUserId, notification);

            if (sent) {
                log.debug("👤 개인 알림 전송 성공: eventId={}, target={}", eventId, targetUserId);
            } else {
                log.debug("👤 개인 알림 전송 실패: 사용자 미연결 - eventId={}, target={}", eventId, targetUserId);
            }

        } catch (Exception e) {
            log.error("개인 알림 처리 실패", e);
        }
    }

    /**
     * 메시지 처리 통계 조회
     */
    public Map<String, Object> getProcessingStatistics() {
        return Map.of(
                "totalProcessed", totalProcessedMessages.get(),
                "successful", successfulMessages.get(),
                "failed", failedMessages.get(),
                "successRate", calculateSuccessRate(),
                "subscribeEnabled", subscribeEnabled,
                "timestamp", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    /**
     * Redis 연결 상태 체크
     */
    public boolean isRedisConnected() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            log.error("Redis 연결 상태 체크 실패", e);
            return false;
        }
    }

    /**
     * 구독 상태 재시작 (장애 복구용)
     */
    public void restartSubscription() {
        try {
            log.info("🔄 Redis 구독 재시작 시도");

            stopSubscription();
            Thread.sleep(1000); // 1초 대기
            startSubscription();

            log.info("✅ Redis 구독 재시작 완료");

        } catch (Exception e) {
            log.error("Redis 구독 재시작 실패", e);
        }
    }

    // === 내부 헬퍼 메소드들 ===

    /**
     * 성공률 계산
     */
    private double calculateSuccessRate() {
        long total = totalProcessedMessages.get();
        if (total == 0) {
            return 100.0;
        }

        long successful = successfulMessages.get();
        return (double) successful / total * 100.0;
    }
}