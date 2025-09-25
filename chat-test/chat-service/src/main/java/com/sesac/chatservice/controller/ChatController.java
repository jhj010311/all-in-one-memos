package com.sesac.chatservice.controller;

import com.sesac.chatservice.model.ChatRoom;
import com.sesac.chatservice.model.CreateChatRoomRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    // 채팅방 목록 조회
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getChatRooms() {
        // 실제로는 DB에서 조회
        List<ChatRoom> rooms = Arrays.asList(
                new ChatRoom("room1", "사과 공동구매", 5),
                new ChatRoom("room2", "딸기 공동구매", 3)
        );
        return ResponseEntity.ok(rooms);
    }

    // 채팅방 생성
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoom> createChatRoom(@RequestBody CreateChatRoomRequest request) {
        ChatRoom room = new ChatRoom(
                "room" + System.currentTimeMillis(),
                request.getName(),
                0
        );
        return ResponseEntity.ok(room);
    }
}
