package com.example.eventpublisher.app;

import com.example.eventpublisher.files.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private final JwtUtil jwtUtil;

    /**
     * 로그인 처리 - JWT 토큰 발급
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        try {
            log.info("로그인 요청: userId={}", request.getUserId());

            // 실제 운영에서는 사용자 인증 로직 필요
            // 여기서는 테스트용으로 모든 사용자 허용
            String userId = request.getUserId();

            if (userId == null || userId.trim().isEmpty()) {
                log.warn("로그인 실패: 사용자 ID가 비어있음");
                return ResponseEntity.badRequest().build();
            }

            // JWT 토큰 생성
            String token = jwtUtil.generateToken(userId);

            log.info("로그인 성공: userId={}, tokenLength={}", userId, token.length());

            TokenResponse response = TokenResponse.builder()
                    .token(token)
                    .userId(userId)
                    .expiresIn(3600L) // 1시간
                    .tokenType("Bearer")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("로그인 처리 중 오류 발생", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 토큰 검증 엔드포인트 (디버깅용)
     */
    @PostMapping("/verify")
    public ResponseEntity<String> verifyToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            if (jwtUtil.validateToken(token)) {
                String userId = jwtUtil.extractUserId(token);
                log.info("토큰 검증 성공: userId={}", userId);
                return ResponseEntity.ok("토큰 유효, 사용자: " + userId);
            } else {
                log.warn("토큰 검증 실패: 유효하지 않은 토큰");
                return ResponseEntity.status(401).body("유효하지 않은 토큰");
            }

        } catch (Exception e) {
            log.error("토큰 검증 중 오류 발생", e);
            return ResponseEntity.status(401).body("토큰 검증 실패");
        }
    }

    /**
     * 로그아웃 (클라이언트에서 토큰 삭제하면 됨)
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String userId = jwtUtil.extractUserId(token);

            log.info("로그아웃: userId={}", userId);

            // JWT는 stateless이므로 서버에서 할 일은 없음
            // 클라이언트에서 토큰을 삭제하면 됨
            return ResponseEntity.ok("로그아웃 완료");

        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류 발생", e);
            return ResponseEntity.ok("로그아웃 완료"); // 어차피 로그아웃이므로 성공 처리
        }
    }
}