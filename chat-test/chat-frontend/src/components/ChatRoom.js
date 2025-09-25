import React, { useState, useEffect, useRef } from 'react';

const ChatRoom = ({ room, userName, onLeaveRoom }) => {
    const [messages, setMessages] = useState([]);
    const [newMessage, setNewMessage] = useState('');
    const [socket, setSocket] = useState(null);
    const messagesEndRef = useRef(null);

    useEffect(() => {
        // WebSocket 연결
        const ws = new WebSocket('ws://localhost:8080/chat');

        ws.onopen = () => {
            console.log('WebSocket 연결됨');
            // 채팅방 입장
            const joinMessage = {
                type: 'JOIN',
                roomId: room.roomId,
                sender: userName,
                message: ''
            };
            ws.send(JSON.stringify(joinMessage));
        };

        ws.onmessage = (event) => {
            const message = JSON.parse(event.data);
            setMessages(prev => [...prev, message]);
        };

        ws.onclose = () => {
            console.log('WebSocket 연결 종료됨');
        };

        ws.onerror = (error) => {
            console.error('WebSocket 오류:', error);
        };

        setSocket(ws);

        return () => {
            if (ws.readyState === WebSocket.OPEN) {
                // 채팅방 나가기
                const leaveMessage = {
                    type: 'LEAVE',
                    roomId: room.roomId,
                    sender: userName,
                    message: ''
                };
                ws.send(JSON.stringify(leaveMessage));
            }
            ws.close();
        };
    }, [room.roomId, userName]);

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    const sendMessage = (e) => {
        e.preventDefault();
        if (!newMessage.trim() || !socket) return;

        const message = {
            type: 'CHAT',
            roomId: room.roomId,
            sender: userName,
            message: newMessage
        };

        socket.send(JSON.stringify(message));
        setNewMessage('');
    };

    const formatTime = (timestamp) => {
        return new Date(timestamp).toLocaleTimeString();
    };

    return (
        <div className="chat-room">
            <div className="chat-header">
                <h2>{room.roomName}</h2>
                <button onClick={onLeaveRoom} className="leave-btn">
                    나가기
                </button>
            </div>

            <div className="messages-container">
                {messages.map((msg, index) => (
                    <div key={index} className={`message ${msg.type.toLowerCase()}`}>
                        {msg.type === 'CHAT' ? (
                            <>
                                <div className="message-header">
                                    <span className="sender">{msg.sender}</span>
                                    <span className="timestamp">{formatTime(msg.timestamp)}</span>
                                </div>
                                <div className="message-content">{msg.message}</div>
                            </>
                        ) : (
                            <div className="system-message">
                                {msg.message}
                            </div>
                        )}
                    </div>
                ))}
                <div ref={messagesEndRef} />
            </div>

            <form onSubmit={sendMessage} className="message-input">
                <input
                    type="text"
                    value={newMessage}
                    onChange={(e) => setNewMessage(e.target.value)}
                    placeholder="메시지를 입력하세요..."
                    autoFocus
                />
                <button type="submit">전송</button>
            </form>
        </div>
    );
};

export default ChatRoom;