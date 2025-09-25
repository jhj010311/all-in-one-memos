import React, { useState, useEffect } from 'react';
import ChatRoomList from './components/ChatRoomList';
import ChatRoom from './components/ChatRoom';
import './App.css';

function App() {
  const [currentRoom, setCurrentRoom] = useState(null);
  const [userName, setUserName] = useState('');
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  const handleLogin = (name) => {
    setUserName(name);
    setIsLoggedIn(true);
  };

  const handleJoinRoom = (room) => {
    setCurrentRoom(room);
  };

  const handleLeaveRoom = () => {
    setCurrentRoom(null);
  };

  if (!isLoggedIn) {
    return (
        <div className="login-container">
          <h2>채팅 서비스</h2>
          <form onSubmit={(e) => {
            e.preventDefault();
            const name = e.target.username.value.trim();
            if (name) handleLogin(name);
          }}>
            <input
                name="username"
                type="text"
                placeholder="사용자 이름을 입력하세요"
                required
            />
            <button type="submit">입장</button>
          </form>
        </div>
    );
  }

  return (
      <div className="app">
        <header>
          <h1>공동구매 채팅</h1>
          <span>안녕하세요, {userName}님!</span>
        </header>
        {currentRoom ? (
            <ChatRoom
                room={currentRoom}
                userName={userName}
                onLeaveRoom={handleLeaveRoom}
            />
        ) : (
            <ChatRoomList
                onJoinRoom={handleJoinRoom}
                userName={userName}
            />
        )}
      </div>
  );
}

export default App;