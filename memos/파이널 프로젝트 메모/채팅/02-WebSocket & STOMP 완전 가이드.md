# WebSocket & STOMP 완전 가이드

## 1. HTTP → WebSocket 업그레이드 과정

### 1-1. 기본 개념
- **HTTP**: 요청-응답 기반의 단방향 통신
- **WebSocket**: 전이중(full-duplex) 양방향 실시간 통신
- **업그레이드**: HTTP 연결을 WebSocket 연결로 전환하는 과정

### 1-2. 업그레이드 핸드셰이크 과정

```
클라이언트 → 서버: HTTP 업그레이드 요청
┌─────────────────────────────────────────┐
│ GET /chat HTTP/1.1                      │
│ Host: localhost:8080                    │
│ Upgrade: websocket                      │
│ Connection: Upgrade                     │
│ Sec-WebSocket-Key: x3JJHMbDL1EzLkh9... │
│ Sec-WebSocket-Version: 13               │
└─────────────────────────────────────────┘

서버 → 클라이언트: HTTP 101 Switching Protocols
┌─────────────────────────────────────────┐
│ HTTP/1.1 101 Switching Protocols        │
│ Upgrade: websocket                      │
│ Connection: Upgrade                     │
│ Sec-WebSocket-Accept: HSmrc0sMlYUk...   │
└─────────────────────────────────────────┘

이제 WebSocket 연결 완료! 🎉
```

### 1-3. 핵심 헤더 설명
- **Upgrade: websocket**: HTTP를 WebSocket으로 업그레이드 요청
- **Connection: Upgrade**: 연결을 업그레이드하겠다는 의도
- **Sec-WebSocket-Key**: 클라이언트가 생성한 랜덤 키
- **Sec-WebSocket-Accept**: 서버가 키를 기반으로 생성한 응답

## 2. STOMP 프로토콜 이해

### 2-1. STOMP란?
- **STOMP**: Simple Text Oriented Messaging Protocol
- **목적**: WebSocket 위에서 메시징 패턴을 표준화
- **장점**: pub/sub 모델, 구독/발행 패턴 지원

### 2-2. 왜 STOMP를 사용하나?
```javascript
// 순수 WebSocket (복잡함)
websocket.send(JSON.stringify({
  type: 'CHAT_MESSAGE',
  roomId: 'room1',
  content: '안녕하세요',
  sender: 'user1'
}));

// STOMP 사용 (간단함)
stompClient.send('/app/chat/room1', {}, '안녕하세요');
```

### 2-3. STOMP 프레임 구조
```
COMMAND
header1:value1
header2:value2

Body^@
```

## 3. STOMP 핸드셰이크 과정

### 3-1. 연결 과정
```
1단계: WebSocket 연결
클라이언트 ← → 서버: WebSocket 핸드셰이크

2단계: STOMP 연결
클라이언트 → 서버: CONNECT 프레임
┌─────────────────┐
│ CONNECT         │
│ accept-version: │
│ host:localhost  │
│                 │
│ ^@              │
└─────────────────┘

서버 → 클라이언트: CONNECTED 프레임
┌─────────────────┐
│ CONNECTED       │
│ version:1.2     │
│ session:sess123 │
│                 │
│ ^@              │
└─────────────────┘

3단계: 구독/발행 가능 상태
```

### 3-2. 주요 STOMP 명령어
- **CONNECT**: STOMP 연결 요청
- **CONNECTED**: STOMP 연결 완료
- **SUBSCRIBE**: 특정 destination 구독
- **UNSUBSCRIBE**: 구독 취소
- **SEND**: 메시지 전송
- **MESSAGE**: 메시지 수신
- **DISCONNECT**: 연결 종료

## 4. Spring Boot STOMP 구성

### 4-1. 기본 설정
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트로 메시지를 보낼 때 사용할 prefix
        config.enableSimpleBroker("/topic", "/queue");
        
        // 클라이언트가 서버로 메시지를 보낼 때 사용할 prefix  
        config.setApplicationDestinationPrefixes("/app");
        
        // 특정 사용자에게만 보낼 때 사용할 prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 연결 엔드포인트
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // SockJS 폴백 지원
    }
}
```

### 4-2. 컨트롤러 구현
```java
@Controller
public class ChatController {

    @MessageMapping("/chat/{roomId}")  // /app/chat/{roomId}로 받음
    @SendTo("/topic/chat/{roomId}")    // 구독자들에게 전송
    public ChatMessage handleMessage(
            @DestinationVariable String roomId,
            ChatMessage message) {
        
        // 메시지 처리 로직
        message.setTimestamp(LocalDateTime.now());
        
        // 데이터베이스 저장
        chatService.saveMessage(roomId, message);
        
        return message; // 구독자들에게 브로드캐스트
    }

    @MessageMapping("/chat/{roomId}/join")
    @SendTo("/topic/chat/{roomId}/participants")
    public ParticipantInfo handleJoin(
            @DestinationVariable String roomId,
            @Header("simpSessionId") String sessionId) {
        
        return chatService.addParticipant(roomId, sessionId);
    }
}
```

## 5. React 클라이언트 구현

### 5-1. STOMP 클라이언트 설정
```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const useStompClient = (roomId) => {
  const [stompClient, setStompClient] = useState(null);
  const [messages, setMessages] = useState([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    // STOMP 클라이언트 생성
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      debug: (str) => console.log(str),
      reconnectDelay: 5000,
      
      onConnect: () => {
        console.log('STOMP Connected!');
        setConnected(true);
        
        // 채팅 메시지 구독
        client.subscribe(`/topic/chat/${roomId}`, (message) => {
          const chatMessage = JSON.parse(message.body);
          setMessages(prev => [...prev, chatMessage]);
        });
        
        // 참여자 변경 구독
        client.subscribe(`/topic/chat/${roomId}/participants`, (message) => {
          const participantInfo = JSON.parse(message.body);
          // 참여자 목록 업데이트
        });
      },
      
      onDisconnect: () => {
        console.log('STOMP Disconnected');
        setConnected(false);
      },
      
      onStompError: (frame) => {
        console.error('STOMP Error:', frame);
      }
    });

    client.activate();
    setStompClient(client);

    return () => {
      if (client.active) {
        client.deactivate();
      }
    };
  }, [roomId]);

  const sendMessage = (content) => {
    if (stompClient && connected) {
      stompClient.publish({
        destination: `/app/chat/${roomId}`,
        body: JSON.stringify({
          content,
          sender: getCurrentUser(),
          type: 'CHAT'
        })
      });
    }
  };

  return { messages, sendMessage, connected };
};
```

### 5-2. 채팅 컴포넌트 사용
```javascript
const ChatRoom = ({ roomId }) => {
  const { messages, sendMessage, connected } = useStompClient(roomId);
  const [inputMessage, setInputMessage] = useState('');

  const handleSend = () => {
    if (inputMessage.trim() && connected) {
      sendMessage(inputMessage);
      setInputMessage('');
    }
  };

  return (
    <div className="chat-room">
      <div className="connection-status">
        {connected ? '🟢 연결됨' : '🔴 연결 중...'}
      </div>
      
      <div className="messages">
        {messages.map((msg, index) => (
          <div key={index} className="message">
            <strong>{msg.sender}:</strong> {msg.content}
          </div>
        ))}
      </div>
      
      <div className="message-input">
        <input
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          onKeyPress={(e) => e.key === 'Enter' && handleSend()}
          placeholder="메시지를 입력하세요..."
        />
        <button onClick={handleSend} disabled={!connected}>
          전송
        </button>
      </div>
    </div>
  );
};
```

## 6. 채팅방 생성 API 연동

### 6-1. 거래 서비스에서 채팅방 생성 요청
```java
// 거래 서비스의 상품 생성 로직
@Service
public class ProductService {
    
    @Autowired
    private ChatServiceClient chatServiceClient;
    
    public Product createProduct(ProductRequest request) {
        // 상품 생성
        Product product = productRepository.save(new Product(request));
        
        // 채팅방 생성 요청 (비동기)
        CompletableFuture.runAsync(() -> {
            CreateChatRoomRequest chatRoomRequest = CreateChatRoomRequest.builder()
                .productId(product.getId())
                .maxParticipants(request.getMaxParticipants())
                .expiresAt(request.getExpiresAt())
                .build();
                
            chatServiceClient.createChatRoom(chatRoomRequest);
        });
        
        return product;
    }
}
```

### 6-2. 채팅 서비스의 채팅방 생성 API
```java
@RestController
@RequestMapping("/api/chat")
public class ChatRoomController {
    
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @RequestBody CreateChatRoomRequest request) {
        
        ChatRoom chatRoom = chatRoomService.createChatRoom(request);
        
        return ResponseEntity.ok(ChatRoomResponse.from(chatRoom));
    }
    
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(
            @PathVariable Long roomId) {
        
        ChatRoom chatRoom = chatRoomService.findById(roomId);
        
        return ResponseEntity.ok(ChatRoomResponse.from(chatRoom));
    }
}
```

## 7. 정리

### 연결 흐름 요약
1. **HTTP 업그레이드**: 클라이언트가 HTTP를 WebSocket으로 업그레이드 요청
2. **WebSocket 연결**: 서버가 101 응답으로 WebSocket 연결 완료
3. **STOMP 핸드셰이크**: WebSocket 위에서 STOMP 프로토콜 연결
4. **구독/발행**: 채팅방 구독 후 실시간 메시지 송수신

### 장점
- **표준화된 메시징**: STOMP로 구독/발행 패턴 쉽게 구현
- **확장성**: 브로커를 통한 멀티 서버 환경 지원
- **유연성**: 다양한 destination 패턴 지원