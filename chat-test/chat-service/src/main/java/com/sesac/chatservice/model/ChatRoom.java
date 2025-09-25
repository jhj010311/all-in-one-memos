package com.sesac.chatservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRoom {
    private String roomId;
    private String roomName;
    private int participantCount;
}
