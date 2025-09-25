package com.sesac.chatservice.hanlder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sesac.chatservice.model.ChatMessage;
import com.sesac.chatservice.model.MessageType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // 채팅방별로 세션을 관리
    private final Map<String, Set<WebSocketSession>> chatRooms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WebSocket 연결됨: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);

            // 메시지 타입에 따라 처리
            switch (chatMessage.getType()) {
                case JOIN:
                    joinChatRoom(session, chatMessage.getRoomId(), chatMessage.getSender());
                    break;
                case CHAT:
                    sendMessage(chatMessage);
                    break;
                case LEAVE:
                    leaveChatRoom(session, chatMessage.getRoomId());
                    break;
            }
        } catch (Exception e) {
            System.err.println("메시지 처리 오류: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 세션 종료시 모든 채팅방에서 제거
        chatRooms.values().forEach(sessions -> sessions.remove(session));
        System.out.println("WebSocket 연결 종료: " + session.getId());
    }

    private void joinChatRoom(WebSocketSession session, String roomId, String userName) throws Exception {
        chatRooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("userName", userName);

        // 입장 알림 메시지
        ChatMessage joinMessage = ChatMessage.builder()
                .type(MessageType.JOIN)
                .roomId(roomId)
                .sender(userName)
                .message(userName + "님이 입장했습니다.")
                .timestamp(LocalDateTime.now())
                .build();

        broadcastToRoom(roomId, joinMessage);
    }

    private void sendMessage(ChatMessage message) throws Exception {
        message.setTimestamp(LocalDateTime.now());
        broadcastToRoom(message.getRoomId(), message);
    }

    private void leaveChatRoom(WebSocketSession session, String roomId) throws Exception {
        Set<WebSocketSession> sessions = chatRooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            String userName = (String) session.getAttributes().get("userName");

            // 퇴장 알림 메시지
            ChatMessage leaveMessage = ChatMessage.builder()
                    .type(MessageType.LEAVE)
                    .roomId(roomId)
                    .sender(userName)
                    .message(userName + "님이 퇴장했습니다.")
                    .timestamp(LocalDateTime.now())
                    .build();

            broadcastToRoom(roomId, leaveMessage);
        }
    }

    private void broadcastToRoom(String roomId, ChatMessage message) throws Exception {
        Set<WebSocketSession> sessions = chatRooms.get(roomId);
        if (sessions != null) {
            String messageJson = objectMapper.writeValueAsString(message);

            sessions.removeIf(session -> !session.isOpen());

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(messageJson));
                }
            }
        }
    }
}
