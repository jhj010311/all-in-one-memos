package com.example.notificationservice.files;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 기본 헬스체크 - 가장 단순한 형태
    @GetMapping("/health")
    public String health() {
        return "Event Publisher Service is UP and running!";
    }

    // Redis 헬스체크 - 이미 작동함
    @GetMapping("/health/redis")
    public ResponseEntity<String> redisHealth() {
        try {
            redisTemplate.opsForValue().set("health-check", "ok");
            String value = redisTemplate.opsForValue().get("health-check");
            return ResponseEntity.ok("Redis OK: " + value);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Redis Error: " + e.getMessage());
        }
    }

    // 상세 헬스체크
    @GetMapping("/health/detail")
    public String healthDetail() {
        try {
            // Redis 연결 테스트
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            boolean redisOk = "PONG".equals(pingResult);

            return String.format(
                    "Service: Event Publisher\n" +
                            "Status: UP\n" +
                            "Redis: %s\n" +
                            "Time: %s\n" +
                            "Port: 3001",
                    redisOk ? "CONNECTED" : "ERROR",
                    java.time.LocalDateTime.now()
            );

        } catch (Exception e) {
            return "Health check failed: " + e.getMessage();
        }
    }

    // 디버깅용 - 모든 엔드포인트 확인
    @GetMapping("/debug")
    public String debug() {
        return "Available endpoints:\n" +
                "- GET /health (기본 상태)\n" +
                "- GET /health/redis (Redis 연결)\n" +
                "- GET /status (상세 상태)\n" +
                "- GET /debug (이 페이지)\n" +
                "- POST /auth/login (로그인)\n";
    }
}