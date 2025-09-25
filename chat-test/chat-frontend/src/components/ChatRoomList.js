import React, { useState, useEffect } from 'react';

const ChatRoomList = ({ onJoinRoom, userName }) => {
    const [rooms, setRooms] = useState([]);
    const [newRoomName, setNewRoomName] = useState('');

    useEffect(() => {
        fetchRooms();
    }, []);

    const fetchRooms = async () => {
        try {
            const response = await fetch('http://localhost:8080/api/chat/rooms');
            const data = await response.json();
            setRooms(data);
        } catch (error) {
            console.error('채팅방 목록 조회 실패:', error);
        }
    };

    const createRoom = async (e) => {
        e.preventDefault();
        if (!newRoomName.trim()) return;

        try {
            const response = await fetch('http://localhost:8080/api/chat/rooms', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ name: newRoomName }),
            });

            if (response.ok) {
                setNewRoomName('');
                fetchRooms();
            }
        } catch (error) {
            console.error('채팅방 생성 실패:', error);
        }
    };

    return (
        <div className="chat-room-list">
            <h2>채팅방 목록</h2>

            <form onSubmit={createRoom} className="create-room-form">
                <input
                    type="text"
                    value={newRoomName}
                    onChange={(e) => setNewRoomName(e.target.value)}
                    placeholder="새 채팅방 이름"
                />
                <button type="submit">방 만들기</button>
            </form>

            <div className="room-list">
                {rooms.map(room => (
                    <div key={room.roomId} className="room-item">
                        <h3>{room.roomName}</h3>
                        <p>참여자: {room.participantCount}명</p>
                        <button onClick={() => onJoinRoom(room)}>
                            입장하기
                        </button>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default ChatRoomList;