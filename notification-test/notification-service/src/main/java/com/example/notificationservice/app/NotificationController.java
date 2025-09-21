package com.example.notificationservice.app;

import com.example.notificationservice.files.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    // SSE 연결 관리 (실제 운영에서는 외부 저장소 사용 권장)
    private final Map<String, Map<String, SseEmitter>> userConnections = new ConcurrentHashMap<>();

    /**
     * SSE 연결 엔드포인트 - URL 파라미터로 토큰 전달
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSSE(@RequestParam String token) {
        try {
            // JWT 토큰 검증
            if (!jwtUtil.validateToken(token)) {
                log.warn("SSE 연결 실패: 유효하지 않은 토큰");
                throw new RuntimeException("유효하지 않은 토큰");
            }

            String userId = jwtUtil.extractUserId(token);
            String connectionId = generateConnectionId();

            log.info("🔗 SSE 연결 요청: userId={}, connectionId={}", userId, connectionId);

            // SseEmitter 생성 (30분 타임아웃)
            SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

            // 사용자별 연결 저장
            userConnections.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(connectionId, emitter);

            // 연결 완료 메시지 전송
            sendConnectionSuccessMessage(emitter, userId, connectionId);

            // 연결 종료 처리
            emitter.onCompletion(() -> {
                removeConnection(userId, connectionId);
                log.info("🔌 SSE 연결 완료: userId={}, connectionId={}", userId, connectionId);
            });

            emitter.onTimeout(() -> {
                removeConnection(userId, connectionId);
                log.info("⏰ SSE 연결 타임아웃: userId={}, connectionId={}", userId, connectionId);
            });

            emitter.onError((throwable) -> {
                removeConnection(userId, connectionId);
                log.error("❌ SSE 연결 오류: userId={}, connectionId={}", userId, connectionId, throwable);
            });

            // 알림 서비스에 연결 등록
            notificationService.registerSSEConnection(userId, connectionId, emitter);

            log.info("✅ SSE 연결 성공: userId={}, 총 연결 수={}",
                    userId, getTotalConnectionCount());

            return emitter;

        } catch (Exception e) {
            log.error("SSE 연결 처리 중 오류 발생", e);
            throw new RuntimeException("SSE 연결 실패: " + e.getMessage());
        }
    }

    /**
     * 폴링 엔드포인트 - Authorization 헤더로 토큰 전달
     */
    @GetMapping("/poll")
    public ResponseEntity<NotificationResponse> pollNotifications(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            // JWT 토큰 검증
            String token = authHeader.replace("Bearer ", "");
            if (!jwtUtil.validateToken(token)) {
                log.warn("폴링 요청 실패: 유효하지 않은 토큰");
                return ResponseEntity.status(401).build();
            }

            String userId = jwtUtil.extractUserId(token);

            log.debug("📮 폴링 요청: userId={}, since={}, limit={}", userId, since, limit);

            // 알림 조회
            List<Map<String, Object>> notifications = notificationService.getNotificationsForUser(userId, since, limit);

            // 응답 생성
            NotificationResponse response = NotificationResponse.builder()
                    .notifications(notifications)
                    .userId(userId)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .hasMore(notifications.size() >= limit)
                    .count(notifications.size())
                    .build();

            log.debug("📮 폴링 응답: userId={}, count={}", userId, notifications.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("폴링 처리 중 오류 발생", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 알림 상세 조회
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<Map<String, Object>> getNotificationDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String notificationId) {

        try {
            String token = authHeader.replace("Bearer ", "");
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(401).build();
            }

            String userId = jwtUtil.extractUserId(token);

            Map<String, Object> notification = notificationService.getNotificationDetail(userId, notificationId);

            if (notification == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(notification);

        } catch (Exception e) {
            log.error("알림 상세 조회 중 오류 발생: notificationId={}", notificationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 알림 읽음 처리
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<String> markAsRead(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String notificationId) {

        try {
            String token = authHeader.replace("Bearer ", "");
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(401).build();
            }

            String userId = jwtUtil.extractUserId(token);

            boolean success = notificationService.markAsRead(userId, notificationId);

            if (success) {
                log.info("✅ 알림 읽음 처리: userId={}, notificationId={}", userId, notificationId);
                return ResponseEntity.ok("읽음 처리 완료");
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("알림 읽음 처리 중 오류 발생: notificationId={}", notificationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * SSE 연결 상태 조회 (관리자용)
     */
    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            Map<String, Integer> userConnectionCounts = new HashMap<>();
            userConnections.forEach((userId, connections) -> {
                userConnectionCounts.put(userId, connections.size());
            });

            status.put("totalConnections", getTotalConnectionCount());
            status.put("connectedUsers", userConnections.size());
            status.put("userConnectionCounts", userConnectionCounts);
            status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            status.put("service", "notification-service");

        } catch (Exception e) {
            log.error("연결 상태 조회 중 오류 발생", e);
            status.put("error", e.getMessage());
        }

        return ResponseEntity.ok(status);
    }

    /**
     * 특정 사용자에게 테스트 메시지 전송 (관리자용)
     */
    @PostMapping("/test-send")
    public ResponseEntity<String> sendTestMessage(
            @RequestParam String targetUserId,
            @RequestParam String message) {

        try {
            boolean sent = notificationService.sendTestMessageToUser(targetUserId, message);

            if (sent) {
                return ResponseEntity.ok("테스트 메시지 전송 완료");
            } else {
                return ResponseEntity.ok("사용자가 연결되어 있지 않음");
            }

        } catch (Exception e) {
            log.error("테스트 메시지 전송 중 오류 발생", e);
            return ResponseEntity.internalServerError().body("전송 실패: " + e.getMessage());
        }
    }

    // === 내부 헬퍼 메소드들 ===

    /**
     * 연결 성공 메시지 전송
     */
    private void sendConnectionSuccessMessage(SseEmitter emitter, String userId, String connectionId) {
        try {
            Map<String, Object> welcomeMessage = new HashMap<>();
            welcomeMessage.put("type", "connection-success");
            welcomeMessage.put("message", "SSE 연결이 성공적으로 설정되었습니다");
            welcomeMessage.put("userId", userId);
            welcomeMessage.put("connectionId", connectionId);
            welcomeMessage.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            welcomeMessage.put("serverTime", System.currentTimeMillis());

            emitter.send(SseEmitter.event()
                    .id(connectionId + "_welcome")
                    .name("welcome")
                    .data(welcomeMessage));

        } catch (Exception e) {
            log.error("연결 성공 메시지 전송 실패: userId={}", userId, e);
        }
    }

    /**
     * 연결 제거
     */
    private void removeConnection(String userId, String connectionId) {
        Map<String, SseEmitter> userEmitters = userConnections.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(connectionId);
            if (userEmitters.isEmpty()) {
                userConnections.remove(userId);
            }
        }

        // 알림 서비스에서도 제거
        notificationService.unregisterSSEConnection(userId, connectionId);
    }

    /**
     * 연결 ID 생성
     */
    private String generateConnectionId() {
        return "conn_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    /**
     * 전체 연결 수 조회
     */
    private int getTotalConnectionCount() {
        return userConnections.values().stream()
                .mapToInt(Map::size)
                .sum();
    }



    @GetMapping("/test-sse")
    public SseEmitter testSSE() {
        SseEmitter emitter = new SseEmitter();

        // 즉시 테스트 메시지 전송
        try {
            emitter.send("테스트 메시지");
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}