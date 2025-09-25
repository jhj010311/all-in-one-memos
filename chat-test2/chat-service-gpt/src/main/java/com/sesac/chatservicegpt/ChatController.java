package com.sesac.chatservicegpt;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;

    @MessageMapping("/chat.sendMessage")   // 클라이언트 → /app/chat.sendMessage
    public void sendMessage(ChatMessageDto chatMessageDto) {
        // DB 저장
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(chatMessageDto.getRoomId())
                .sender(chatMessageDto.getSender())
                .message(chatMessageDto.getMessage())
                .timestamp(LocalDateTime.now())
                .build());

        // 구독자에게 브로드캐스트
        messagingTemplate.convertAndSend("/topic/" + chatMessageDto.getRoomId(), saved);
    }
}