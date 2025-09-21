package com.example.notificationservice.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // SSE 연결 관리
    private final Map<String, Map<String, SseEmitter>> sseConnections = new ConcurrentHashMap<>();

    // Redis 키 패턴
    private static final String USER_NOTIFICATIONS_KEY = "user:%s:notifications";
    private static final String NOTIFICATION_DETAIL_KEY = "notification:%s:%s";
    private static final String BROADCAST_NOTIFICATIONS_KEY = "broadcast:notifications";
    private static final String USER_READ_NOTIFICATIONS_KEY = "user:%s:read";

    /**
     * SSE 연결 등록
     */
    public void registerSSEConnection(String userId, String connectionId, SseEmitter emitter) {
        sseConnections.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(connectionId, emitter);

        log.info("📡 SSE 연결 등록: userId={}, connectionId={}, 총 연결 수={}",
                userId, connectionId, getTotalConnectionCount());
    }

    /**
     * SSE 연결 해제
     */
    public void unregisterSSEConnection(String userId, String connectionId) {
        Map<String, SseEmitter> userEmitters = sseConnections.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(connectionId);
            if (userEmitters.isEmpty()) {
                sseConnections.remove(userId);
            }
        }

        log.info("🔌 SSE 연결 해제: userId={}, connectionId={}, 총 연결 수={}",
                userId, connectionId, getTotalConnectionCount());
    }

    /**
     * 특정 사용자에게 SSE 메시지 전송
     */
    public boolean sendToUser(String userId, Map<String, Object> notification) {
        Map<String, SseEmitter> userEmitters = sseConnections.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.debug("📭 SSE 전송 실패: 사용자 연결 없음 - userId={}", userId);
            return false;
        }

        List<String> failedConnections = new ArrayList<>();
        int sentCount = 0;

        for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
            String connectionId = entry.getKey();
            SseEmitter emitter = entry.getValue();

            try {
                emitter.send(notification);

                sentCount++;
                log.debug("📨 SSE 전송 성공: userId={}, connectionId={}, eventId={}",
                        userId, connectionId, notification.get("id"));

            } catch (Exception e) {
                log.warn("📨 SSE 전송 실패: userId={}, connectionId={}", userId, connectionId, e);
                failedConnections.add(connectionId);
            }
        }

        // 실패한 연결들 정리
        failedConnections.forEach(connectionId -> {
            userEmitters.remove(connectionId);
            log.info("🗑️ 실패한 SSE 연결 제거: userId={}, connectionId={}", userId, connectionId);
        });

        if (userEmitters.isEmpty()) {
            sseConnections.remove(userId);
        }

        log.info("📡 SSE 전송 결과: userId={}, 성공={}, 실패={}", userId, sentCount, failedConnections.size());
        return sentCount > 0;
    }

    /**
     * 모든 연결된 사용자에게 브로드캐스트
     */
    public int broadcastToAll(Map<String, Object> notification) {
        int totalSent = 0;

        for (String userId : new HashSet<>(sseConnections.keySet())) {
            if (sendToUser(userId, notification)) {
                totalSent++;
            }
        }

        log.info("📢 브로드캐스트 완료: 전송 대상={}, 성공={}", sseConnections.size(), totalSent);
        return totalSent;
    }

    /**
     * 사용자별 알림 목록 조회 (폴링용)
     */
    public List<Map<String, Object>> getNotificationsForUser(String userId, String since, int limit) {
        try {
            List<Map<String, Object>> notifications = new ArrayList<>();

            // 개인 알림 조회
            String userKey = String.format(USER_NOTIFICATIONS_KEY, userId);
            List<String> personalNotifications = redisTemplate.opsForList().range(userKey, 0, limit - 1);

            if (personalNotifications != null) {
                for (String notificationJson : personalNotifications) {
                    try {
                        Map<String, Object> notification = objectMapper.readValue(notificationJson, Map.class);
                        notifications.add(notification);
                    } catch (Exception e) {
                        log.warn("개인 알림 파싱 실패: userId={}", userId, e);
                    }
                }
            }

            // 브로드캐스트 알림 조회 (개인 알림이 부족한 경우)
            if (notifications.size() < limit) {
                int remaining = limit - notifications.size();
                List<String> broadcastNotifications = redisTemplate.opsForList().range(BROADCAST_NOTIFICATIONS_KEY, 0, remaining - 1);

                if (broadcastNotifications != null) {
                    for (String notificationJson : broadcastNotifications) {
                        try {
                            Map<String, Object> notification = objectMapper.readValue(notificationJson, Map.class);
                            notifications.add(notification);
                        } catch (Exception e) {
                            log.warn("브로드캐스트 알림 파싱 실패", e);
                        }
                    }
                }
            }

            // since 파라미터 필터링
            if (since != null && !since.isEmpty()) {
                LocalDateTime sinceTime = LocalDateTime.parse(since, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                notifications = notifications.stream()
                        .filter(notification -> {
                            try {
                                String timestampStr = (String) notification.get("timestamp");
                                LocalDateTime notificationTime = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                return notificationTime.isAfter(sinceTime);
                            } catch (Exception e) {
                                return true; // 파싱 실패 시 포함
                            }
                        })
                        .collect(Collectors.toList());
            }

            // 읽음 상태 정보 추가
            addReadStatusToNotifications(userId, notifications);

            // 시간순 정렬 (최신순)
            notifications.sort((a, b) -> {
                try {
                    String timeA = (String) a.get("timestamp");
                    String timeB = (String) b.get("timestamp");
                    return timeB.compareTo(timeA);
                } catch (Exception e) {
                    return 0;
                }
            });

            log.debug("📮 알림 조회 완료: userId={}, count={}, since={}", userId, notifications.size(), since);
            return notifications.subList(0, Math.min(notifications.size(), limit));

        } catch (Exception e) {
            log.error("알림 조회 실패: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 특정 알림 상세 조회
     */
    public Map<String, Object> getNotificationDetail(String userId, String notificationId) {
        try {
            String detailKey = String.format(NOTIFICATION_DETAIL_KEY, userId, notificationId);
            String notificationJson = redisTemplate.opsForValue().get(detailKey);

            if (notificationJson != null) {
                Map<String, Object> notification = objectMapper.readValue(notificationJson, Map.class);

                // 읽음 상태 추가
                boolean isRead = isNotificationRead(userId, notificationId);
                notification.put("read", isRead);

                return notification;
            }

            log.debug("알림 상세 조회 실패: 알림을 찾을 수 없음 - userId={}, notificationId={}", userId, notificationId);
            return null;

        } catch (Exception e) {
            log.error("알림 상세 조회 실패: userId={}, notificationId={}", userId, notificationId, e);
            return null;
        }
    }

    /**
     * 알림 읽음 처리
     */
    public boolean markAsRead(String userId, String notificationId) {
        try {
            String readKey = String.format(USER_READ_NOTIFICATIONS_KEY, userId);
            redisTemplate.opsForSet().add(readKey, notificationId);

            // 읽음 상태 TTL 설정 (30일)
            redisTemplate.expire(readKey, 30 * 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);

            log.debug("✅ 알림 읽음 처리: userId={}, notificationId={}", userId, notificationId);
            return true;

        } catch (Exception e) {
            log.error("알림 읽음 처리 실패: userId={}, notificationId={}", userId, notificationId, e);
            return false;
        }
    }

    /**
     * 테스트용 메시지 전송
     */
    public boolean sendTestMessageToUser(String targetUserId, String message) {
        try {
            Map<String, Object> testNotification = new HashMap<>();
            testNotification.put("id", "test_" + System.currentTimeMillis());
            testNotification.put("type", "test-message");
            testNotification.put("targetUserId", targetUserId);
            testNotification.put("message", message);
            testNotification.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            testNotification.put("test", true);

            return sendToUser(targetUserId, testNotification);

        } catch (Exception e) {
            log.error("테스트 메시지 전송 실패: targetUserId={}", targetUserId, e);
            return false;
        }
    }

    /**
     * 연결 통계 조회
     */
    public Map<String, Object> getConnectionStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            stats.put("totalConnections", getTotalConnectionCount());
            stats.put("connectedUsers", sseConnections.size());
            stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            Map<String, Integer> userConnectionCounts = new HashMap<>();
            sseConnections.forEach((userId, connections) -> {
                userConnectionCounts.put(userId, connections.size());
            });
            stats.put("userConnections", userConnectionCounts);

        } catch (Exception e) {
            log.error("연결 통계 조회 실패", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    // === 내부 헬퍼 메소드들 ===

    /**
     * 알림 읽음 상태 확인
     */
    private boolean isNotificationRead(String userId, String notificationId) {
        try {
            String readKey = String.format(USER_READ_NOTIFICATIONS_KEY, userId);
            return redisTemplate.opsForSet().isMember(readKey, notificationId);
        } catch (Exception e) {
            log.warn("읽음 상태 확인 실패: userId={}, notificationId={}", userId, notificationId, e);
            return false;
        }
    }

    /**
     * 알림 목록에 읽음 상태 정보 추가
     */
    private void addReadStatusToNotifications(String userId, List<Map<String, Object>> notifications) {
        try {
            String readKey = String.format(USER_READ_NOTIFICATIONS_KEY, userId);
            Set<String> readNotifications = redisTemplate.opsForSet().members(readKey);

            if (readNotifications == null) {
                readNotifications = new HashSet<>();
            }

            for (Map<String, Object> notification : notifications) {
                String notificationId = (String) notification.get("id");
                boolean isRead = readNotifications.contains(notificationId);
                notification.put("read", isRead);
            }

        } catch (Exception e) {
            log.warn("읽음 상태 추가 실패: userId={}", userId, e);
        }
    }

    /**
     * 전체 연결 수 조회
     */
    private int getTotalConnectionCount() {
        return sseConnections.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
}