# ⚡ NexusChat — Server

The backend for NexusChat. Built with **Spring Boot**, **Redis Pub/Sub**, **MongoDB**, and **WebSocket (STOMP)**. Handles real-time messaging, presence tracking, and JWT authentication.

> **Frontend repo:** [nexuschat-client](https://github.com/your-username/nexuschat-client)

---

## 🏗️ Architecture

```
Client (WebSocket / REST)
         │
         ▼
  Spring Boot Server
  ┌──────────────────────────────┐
  │  REST Controllers            │  /api/auth, /api/channels
  │  WebSocket Controllers       │  /app/chat.send, /app/presence.*
  │                              │
  │  ChatService                 │  Save → MongoDB, Publish → Redis
  │  PresenceService             │  Redis TTL keys + heartbeat
  │  JwtService                  │  Access + Refresh tokens
  │                              │
  │  RedisMessageSubscriber      │  Redis → WebSocket broadcast
  └──────┬───────────────────────┘
         │
    ┌────┴─────┐
    ▼          ▼
 MongoDB     Redis
(messages)  (presence + pub/sub)
```

### Message Flow

```
Client → /app/chat.send/{channelId}
       → ChatService.processAndBroadcast()
           ├── Save to MongoDB
           └── Publish to Redis: chat:{channelId}
               → RedisMessageSubscriber.onMessage()
               → Broadcast to /topic/channel/{channelId}
               → All subscribed WebSocket clients
```

### Presence Flow

```
Connect   → /app/presence.join  → SET presence:{userId} EX 30 (Redis)
Heartbeat → /app/presence.heartbeat (every 20s) → EXPIRE reset
Disconnect → WebSocketEventListener → DEL presence:{userId}
Each event → broadcast to /topic/presence
```

---

## 🛠️ Tech Stack

| | |
|---|---|
| Framework | Spring Boot 3 |
| Real-time | WebSocket + STOMP (SockJS) |
| Message Broker | Redis Pub/Sub |
| Database | MongoDB |
| Auth | JWT via jjwt |
| Build | Maven |

---

## ⚙️ Prerequisites

- Java 17+
- Maven 3.8+
- MongoDB on `localhost:27017`
- Redis on `localhost:6379`

---

## 🚀 Getting Started

### 1. Clone

```bash
git clone https://github.com/your-username/nexuschat-server.git
cd nexuschat-server
```

### 2. Configure `src/main/resources/application.properties`

```properties
# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/nexuschat

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Server
server.port=8080
```

### 3. Run

```bash
./mvnw spring-boot:run
```

### 4. Verify

```bash
curl http://localhost:8080/api/health
# {"status":"UP","app":"NexusChat Server"}
```

---

## 🐳 Docker (MongoDB + Redis)

```yaml
# docker-compose.yml
version: '3.8'
services:
  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

```bash
docker-compose up -d
```

---

## 📡 REST API

All protected endpoints require:
```
Authorization: Bearer <accessToken>
```

### Auth — Public

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Login and receive tokens |

**Request body (register):**
```json
{ "username": "alice", "email": "alice@example.com", "password": "secret" }
```

**Request body (login):**
```json
{ "email": "alice@example.com", "password": "secret" }
```

**Response (both):**
```json
{
  "accessToken":  "eyJ...",
  "refreshToken": "eyJ...",
  "userId":       "64abc123",
  "username":     "alice"
}
```

### Chat — Protected

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/channels/{channelId}/messages` | Load message history |

Query params:
- `limit` — number of messages (default: `50`)
- `before` — message ID for cursor-based pagination

### Presence — Protected

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/channels/{channelId}/presence` | Online users in a channel |
| `GET` | `/api/presence/{userId}` | Check if a user is online |

---

## 🔌 WebSocket API

**Endpoint:** `ws://localhost:8080/ws`

Connect with STOMP headers:
```
userId:   <userId>
username: <username>
```

### Subscribe

| Destination | Description |
|-------------|-------------|
| `/topic/channel/{channelId}` | Incoming messages for a channel |
| `/topic/presence` | Presence change events |

### Publish

| Destination | Payload | Description |
|-------------|---------|-------------|
| `/app/chat.send/{channelId}` | `ChatMessage` | Send a message |
| `/app/chat.typing/{channelId}` | `ChatMessage` | Typing indicator (ephemeral) |
| `/app/presence.join` | `{ userId, username, channelId }` | Mark online + join channel |
| `/app/presence.leave` | `{ userId, username, channelId }` | Mark offline + leave channel |
| `/app/presence.heartbeat` | `{ userId }` | Refresh presence TTL (every 20s) |

### ChatMessage Schema

```json
{
  "id":             "64abc...",
  "channelId":      "general",
  "senderId":       "user123",
  "senderUsername": "alice",
  "content":        "Hello!",
  "type":           "TEXT",
  "createdAt":      "2024-01-15T10:30:00Z",
  "eventType":      "MESSAGE"
}
```

`eventType`: `MESSAGE` | `TYPING` | `JOIN` | `LEAVE`

---

## 📁 Project Structure

```
src/main/java/com/nexuschat/server/
├── config/
│   ├── RedisConfig.java               # RedisTemplate + Pub/Sub listener
│   ├── SecurityConfig.java            # JWT filter chain + CORS
│   ├── WebSocketConfig.java           # STOMP broker + session attributes
│   └── WebSocketEventListener.java    # Connect/disconnect events
├── controller/
│   ├── AuthController.java            # /api/auth/register, /login
│   ├── ChatController.java            # WS chat.send, REST message history
│   └── PresenceController.java        # WS presence.*, REST presence queries
├── dto/
│   └── ChatMessage.java               # WebSocket message DTO
├── model/
│   ├── Message.java                   # MongoDB message document
│   └── User.java                      # MongoDB user document
├── repository/
│   ├── MessageRepository.java
│   └── UserRepository.java
└── service/
    ├── ChatService.java               # Save + Redis publish
    ├── JwtService.java                # Token generation + validation
    ├── PresenceService.java           # Redis TTL presence management
    └── RedisMessageSubscriber.java    # Redis → WebSocket bridge
```

---

## 🔐 Security Notes

> Before deploying to production:

- Move the JWT secret out of `JwtService.java` into an environment variable or secrets manager
- Restrict CORS `allowedOriginPatterns` in `SecurityConfig.java` to your frontend domain
- Enable Redis authentication if your Redis instance is exposed

---

## 📄 License

MIT
