# Spring Scheduler 완전 가이드 - 채팅 서비스 활용편

## 1. Spring Scheduler 개요

### 1-1. 기본 개념
Spring Scheduler는 **시간 기반 작업**을 자동으로 실행하는 Spring의 스케줄링 프레임워워크입니다.

**핵심 특징:**
- **선언적 스케줄링**: 어노테이션으로 간단하게 설정
- **다양한 실행 방식**: 고정 시간, Cron 표현식, 고정 딜레이
- **비동기 실행**: 메인 스레드를 블로킹하지 않음
- **Spring 통합**: IoC 컨테이너와 완전 통합

### 1-2. 기본 설정
```java
@Configuration
@EnableScheduling  // 스케줄링 기능 활성화
public class SchedulingConfig {
    
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);  // 스레드 풀 크기
        scheduler.setThreadNamePrefix("chat-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
```

## 2. 자주 사용되는 기능들

### 2-1. @Scheduled 어노테이션 옵션들

#### **1) fixedRate - 고정 간격 실행**
```java
@Scheduled(fixedRate = 5000)  // 5초마다 실행
public void cleanupInactiveConnections() {
    // WebSocket 비활성 연결 정리
    webSocketSessionManager.removeInactiveConnections();
    log.info("비활성 연결 정리 완료");
}
```

#### **2) fixedDelay - 완료 후 대기**
```java
@Scheduled(fixedDelay = 10000)  // 이전 실행 완료 후 10초 대기
public void processMessageQueue() {
    // 메시지 큐 처리 (시간이 오래 걸릴 수 있는 작업)
    messageQueueService.processAll();
    log.info("메시지 큐 처리 완료");
}
```

#### **3) cron - Cron 표현식**
```java
@Scheduled(cron = "0 0 2 * * ?")  // 매일 새벽 2시
public void archiveOldMessages() {
    // 오래된 메시지 아카이빙
    chatService.archiveMessagesOlderThan(Duration.ofDays(365));
    log.info("메시지 아카이빙 완료");
}

@Scheduled(cron = "0 */10 * * * ?")  // 10분마다
public void syncChatRoomStatus() {
    // 채팅방 상태 동기화
    chatRoomService.syncAllRoomStatus();
}
```

### 2-2. Cron 표현식 패턴
```
초(0-59) 분(0-59) 시(0-23) 일(1-31) 월(1-12) 요일(0-7) [년도]

예시:
"0 0 12 * * ?"        → 매일 정오
"0 15 10 * * ?"       → 매일 오전 10:15
"0 0/5 14 * * ?"      → 매일 오후 2:00~2:55, 5분마다
"0 0 9-17 * * MON-FRI" → 평일 오전 9시~오후 5시, 매시 정각
"0 30 23 * * SAT"     → 매주 토요일 오후 11:30
```

### 2-3. 조건부 실행
```java
@Component
public class ConditionalScheduler {
    
    @Value("${app.cleanup.enabled:true}")
    private boolean cleanupEnabled;
    
    @Scheduled(fixedRate = 30000)
    public void conditionalCleanup() {
        if (cleanupEnabled) {
            performCleanup();
        }
    }
    
    @Scheduled(cron = "0 0 1 * * ?")
    @ConditionalOnProperty(value = "app.archive.enabled", havingValue = "true")
    public void archiveMessages() {
        // 설정이 enabled일 때만 실행
    }
}
```

## 3. 채팅 서비스에서의 활용 포인트

### 3-1. 채팅방 생명주기 관리

#### **모집 시간 만료 처리**
```java
@Service
public class ChatRoomLifecycleScheduler {
    
    @Scheduled(fixedRate = 10000)  // 10초마다 체크
    public void checkExpiredChatRooms() {
        List<ChatRoom> expiredRooms = chatRoomService.findExpiredRooms();
        
        for (ChatRoom room : expiredRooms) {
            processChatRoomExpiration(room);
        }
    }
    
    private void processChatRoomExpiration(ChatRoom room) {
        int currentParticipants = room.getParticipants().size();
        int minParticipants = room.getMinParticipants();
        
        if (currentParticipants >= minParticipants) {
            // 최소 인원 충족 → 모집 완료
            room.setStatus(ChatRoomStatus.CONFIRMED);
            notificationService.sendGroupPurchaseConfirmed(room);
        } else {
            // 최소 인원 미달 → 연장 또는 취소 처리
            handleInsufficientParticipants(room);
        }
        
        chatRoomService.updateRoomStatus(room);
    }
    
    private void handleInsufficientParticipants(ChatRoom room) {
        if (room.canExtend()) {
            // 시간 연장 가능한 경우
            room.extendDeadline(Duration.ofHours(24));
            room.setStatus(ChatRoomStatus.EXTENDED);
            notificationService.sendDeadlineExtended(room);
        } else {
            // 취소 처리
            room.setStatus(ChatRoomStatus.CANCELLED);
            chatRoomService.refundAllParticipants(room);
            notificationService.sendGroupPurchaseCancelled(room);
        }
    }
}
```

#### **모집 완료 후 후속 처리**
```java
@Scheduled(fixedRate = 30000)  // 30초마다
public void processConfirmedRooms() {
    List<ChatRoom> confirmedRooms = chatRoomService.findRecentlyConfirmedRooms();
    
    for (ChatRoom room : confirmedRooms) {
        // 1. 미참여자 채팅방에서 제거
        removeNonParticipants(room);
        
        // 2. 배송 정보 수집 시작
        startDeliveryInfoCollection(room);
        
        // 3. 정산 준비
        paymentService.prepareGroupPayment(room);
        
        room.setStatus(ChatRoomStatus.DELIVERY_PREP);
        chatRoomService.save(room);
    }
}
```

### 3-2. 메시지 및 연결 관리

#### **비활성 WebSocket 연결 정리**
```java
@Scheduled(fixedRate = 60000)  // 1분마다
public void cleanupInactiveWebSocketSessions() {
    Map<String, WebSocketSession> activeSessions = webSocketManager.getAllSessions();
    
    for (Map.Entry<String, WebSocketSession> entry : activeSessions.entrySet()) {
        WebSocketSession session = entry.getValue();
        
        if (isSessionInactive(session)) {
            try {
                session.close(CloseStatus.GOING_AWAY);
                webSocketManager.removeSession(entry.getKey());
                log.info("비활성 세션 제거: {}", entry.getKey());
            } catch (IOException e) {
                log.error("세션 종료 중 오류", e);
            }
        }
    }
}

private boolean isSessionInactive(WebSocketSession session) {
    // 마지막 활동 시간이 5분 이상 지났는지 확인
    Long lastActivity = (Long) session.getAttributes().get("lastActivity");
    return lastActivity != null && 
           System.currentTimeMillis() - lastActivity > 300000; // 5분
}
```

#### **메시지 배치 처리**
```java
@Scheduled(fixedDelay = 5000)  // 이전 처리 완료 후 5초 대기
public void processPendingMessages() {
    List<PendingMessage> pendingMessages = messageService.getPendingMessages(100);
    
    if (!pendingMessages.isEmpty()) {
        messageService.processBatch(pendingMessages);
        log.info("배치 메시지 처리 완료: {} 개", pendingMessages.size());
    }
}
```

### 3-3. 알림 및 리마인더

#### **구매 확정 리마인더**
```java
@Scheduled(cron = "0 0 10,15,20 * * ?")  // 오전 10시, 오후 3시, 8시
public void sendPurchaseConfirmationReminder() {
    List<ChatRoom> roomsWaitingConfirmation = chatRoomService
        .findRoomsWaitingPurchaseConfirmation();
    
    for (ChatRoom room : roomsWaitingConfirmation) {
        List<User> unconfirmedUsers = room.getUnconfirmedUsers();
        
        if (!unconfirmedUsers.isEmpty()) {
            notificationService.sendPurchaseConfirmationReminder(
                room, unconfirmedUsers);
        }
        
        // 일정 시간 후 자동 확정 처리
        if (room.isAutoConfirmationTime()) {
            paymentService.autoConfirmPurchases(room);
        }
    }
}
```

#### **배송 상태 업데이트**
```java
@Scheduled(fixedRate = 1800000)  // 30분마다
public void updateDeliveryStatus() {
    List<ChatRoom> roomsInDelivery = chatRoomService
        .findRoomsInDeliveryStatus();
    
    for (ChatRoom room : roomsInDelivery) {
        // 배송업체 API를 통해 배송 상태 조회
        DeliveryStatus status = deliveryService.checkDeliveryStatus(room);
        
        if (status.hasUpdates()) {
            // 채팅방에 배송 상태 업데이트 알림
            ChatMessage systemMessage = ChatMessage.builder()
                .type(MessageType.SYSTEM)
                .content(status.getUpdateMessage())
                .chatRoom(room)
                .build();
                
            messageService.broadcastSystemMessage(systemMessage);
        }
    }
}
```

### 3-4. 데이터 정리 및 최적화

#### **오래된 메시지 아카이빙**
```java
@Scheduled(cron = "0 0 3 * * SUN")  // 매주 일요일 새벽 3시
public void archiveOldMessages() {
    LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);
    
    // 6개월 이상 된 메시지를 아카이브 테이블로 이동
    int archivedCount = messageService.archiveMessagesOlderThan(cutoffDate);
    
    log.info("메시지 아카이빙 완료: {} 개 메시지", archivedCount);
    
    // 아카이브 후 통계 업데이트
    statisticsService.updateArchivedMessageStats();
}
```

#### **채팅방 통계 업데이트**
```java
@Scheduled(cron = "0 30 23 * * ?")  // 매일 밤 11:30
public void updateDailyStatistics() {
    LocalDate today = LocalDate.now();
    
    // 일일 통계 계산
    DailyStatistics stats = DailyStatistics.builder()
        .date(today)
        .totalMessages(messageService.countMessagesForDate(today))
        .activeUsers(userService.countActiveUsersForDate(today))
        .newChatRooms(chatRoomService.countNewRoomsForDate(today))
        .completedPurchases(purchaseService.countCompletedForDate(today))
        .build();
    
    statisticsService.saveDailyStats(stats);
    log.info("일일 통계 업데이트 완료: {}", stats);
}
```

### 3-5. 시스템 모니터링

#### **시스템 상태 체크**
```java
@Scheduled(fixedRate = 120000)  // 2분마다
public void performHealthCheck() {
    SystemHealth health = SystemHealth.builder()
        .timestamp(LocalDateTime.now())
        .activeWebSocketConnections(webSocketManager.getActiveConnectionCount())
        .memoryUsage(getMemoryUsage())
        .dbConnectionPoolStatus(getDbPoolStatus())
        .build();
    
    // 임계치 초과 시 알림
    if (health.requiresAttention()) {
        alertService.sendSystemAlert(health);
    }
    
    monitoringService.recordHealthCheck(health);
}
```

## 4. 스케줄러 최적화 및 주의사항

### 4-1. 성능 최적화
```java
@Configuration
public class SchedulerOptimizationConfig {
    
    @Bean("chatTaskExecutor")
    public TaskExecutor chatTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}

@Service
public class OptimizedScheduler {
    
    @Async("chatTaskExecutor")
    @Scheduled(fixedRate = 10000)
    public void asyncTask() {
        // 시간이 오래 걸리는 작업은 비동기로 처리
        performHeavyOperation();
    }
}
```

### 4-2. 에러 처리
```java
@Scheduled(fixedRate = 30000)
public void robustScheduledTask() {
    try {
        performCriticalTask();
    } catch (Exception e) {
        log.error("스케줄된 작업 실행 중 오류 발생", e);
        
        // 에러 알림
        alertService.sendSchedulerError("criticalTask", e);
        
        // 필요시 재시도 로직
        retryService.scheduleRetry("criticalTask");
    }
}
```

### 4-3. 동적 스케줄링
```java
@Service
public class DynamicSchedulingService {
    
    @Autowired
    private TaskScheduler taskScheduler;
    
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    public void scheduleChatRoomExpiration(ChatRoom chatRoom) {
        String taskId = "expire-room-" + chatRoom.getId();
        
        // 기존 스케줄 취소
        ScheduledFuture<?> existingTask = scheduledTasks.get(taskId);
        if (existingTask != null) {
            existingTask.cancel(false);
        }
        
        // 새 스케줄 등록
        ScheduledFuture<?> task = taskScheduler.schedule(
            () -> chatRoomService.expireRoom(chatRoom.getId()),
            chatRoom.getExpiresAt()
        );
        
        scheduledTasks.put(taskId, task);
    }
}
```

## 5. 모니터링 및 디버깅

### 5-1. 스케줄러 실행 로그
```java
@Component
@Slf4j
public class SchedulerMonitor {
    
    @EventListener
    public void handleScheduledEvent(ScheduledTaskEvent event) {
        log.info("스케줄된 작업 실행: {} - 소요시간: {}ms", 
            event.getTaskName(), event.getExecutionTime());
    }
}
```

### 5-2. 메트릭 수집
```java
@Scheduled(fixedRate = 60000)
@Timed(name = "cleanup.inactive.connections", description = "비활성 연결 정리 시간")
public void monitoredCleanup() {
    Metrics.counter("cleanup.inactive.connections.count").increment();
    
    cleanupInactiveConnections();
    
    Metrics.gauge("websocket.active.connections", 
        webSocketManager.getActiveConnectionCount());
}
```

## 6. 정리

Spring Scheduler를 채팅 서비스에 활용할 수 있는 핵심 영역:

### **필수 기능:**
- ✅ 채팅방 모집 시간 만료 처리
- ✅ 비활성 WebSocket 연결 정리
- ✅ 구매 확정 리마인더 발송

### **최적화 기능:**
- 📊 메시지 아카이빙
- 📈 통계 데이터 집계
- 🔍 시스템 상태 모니터링

### **주의사항:**
- 🚨 예외 처리 필수
- ⚡ 긴 작업은 비동기 처리
- 📝 충분한 로깅과 모니터링

공동구매 채팅 서비스에서는 특히 **시간 기반 상태 변경**이 핵심이므로 Spring Scheduler가 매우 유용할 것입니다!