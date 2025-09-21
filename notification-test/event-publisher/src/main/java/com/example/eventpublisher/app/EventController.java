package com.example.eventpublisher.app;

import com.example.eventpublisher.files.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EventController {

    private final EventPublishService eventPublishService;
    private final JwtUtil jwtUtil;

    /**
     * 이벤트 발행 - 특정 사용자 또는 전체 대상
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishEvent(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody EventRequest request) {

        try {
            // JWT 토큰 검증
            String token = authHeader.replace("Bearer ", "");
            if (!jwtUtil.validateToken(token)) {
                log.warn("이벤트 발행 실패: 유효하지 않은 토큰");
                return ResponseEntity.status(401).body(createErrorResponse("유효하지 않은 토큰"));
            }

            String publisherId = jwtUtil.extractUserId(token);
            log.info("이벤트 발행 요청: publisher={}, target={}, type={}",
                    publisherId, request.getTargetUserId(), request.getEventType());

            // 이벤트 ID 생성
            String eventId = generateEventId();

            // 이벤트 발행
            boolean success = eventPublishService.publishEvent(
                    eventId,
                    request.getTargetUserId(),
                    request.getEventType(),
                    request.getMessage(),
                    publisherId
            );

            if (success) {
                log.info("이벤트 발행 성공: eventId={}", eventId);
                return ResponseEntity.ok(createSuccessResponse(eventId, publisherId));
            } else {
                log.error("이벤트 발행 실패: eventId={}", eventId);
                return ResponseEntity.internalServerError()
                        .body(createErrorResponse("이벤트 발행 실패"));
            }

        } catch (Exception e) {
            log.error("이벤트 발행 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("서버 오류: " + e.getMessage()));
        }
    }

    /**
     * 특정 사용자에게 이벤트 발행 (편의 메소드)
     */
    @PostMapping("/send-to-user")
    public ResponseEntity<Map<String, Object>> sendToUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String targetUserId,
            @RequestParam(defaultValue = "personal-message") String eventType,
            @RequestParam String message) {

        EventRequest request = EventRequest.builder()
                .targetUserId(targetUserId)
                .eventType(eventType)
                .message(message)
                .build();

        return publishEvent(authHeader, request);
    }

    /**
     * 전체 사용자에게 브로드캐스트 (편의 메소드)
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "broadcast") String eventType,
            @RequestParam String message) {

        EventRequest request = EventRequest.builder()
                .targetUserId("all") // 전체 대상
                .eventType(eventType)
                .message(message)
                .build();

        return publishEvent(authHeader, request);
    }

    /**
     * 테스트용 대량 이벤트 발행
     */
    @PostMapping("/bulk-test")
    public ResponseEntity<Map<String, Object>> bulkTest(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "alice") String targetUserId,
            @RequestParam(defaultValue = "5") int count) {

        try {
            String token = authHeader.replace("Bearer ", "");
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(401).body(createErrorResponse("유효하지 않은 토큰"));
            }

            String publisherId = jwtUtil.extractUserId(token);
            log.info("대량 테스트 시작: publisher={}, target={}, count={}", publisherId, targetUserId, count);

            for (int i = 1; i <= count; i++) {
                String eventId = generateEventId();
                String message = String.format("대량 테스트 메시지 %d/%d", i, count);

                eventPublishService.publishEvent(
                        eventId,
                        targetUserId,
                        "bulk-test",
                        message,
                        publisherId
                );

                // 너무 빠른 발송 방지
                Thread.sleep(100);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("published", count);
            response.put("target", targetUserId);
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("대량 테스트 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("대량 테스트 실패: " + e.getMessage()));
        }
    }

    // === 헬퍼 메소드들 ===

    private String generateEventId() {
        return "evt_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    private Map<String, Object> createSuccessResponse(String eventId, String publisherId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("eventId", eventId);
        response.put("publisherId", publisherId);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return response;
    }
}