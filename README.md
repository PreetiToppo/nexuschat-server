# ⚡ NexusChat — Server

The backend for NexusChat. Built with **Spring Boot**, **Redis Pub/Sub**, **MongoDB**, and **WebSocket (STOMP)**. Handles real-time messaging, presence tracking, JWT authentication, and **AI-powered reply suggestions via Groq**.

> **Frontend repo:** [nexuschat-client](https://github.com/your-username/nexuschat-client)

---

## 🏗️ Architecture

```
Client (WebSocket / REST / SSE)
              │
              ▼
     Spring Boot Server
  ┌────────────────────────────────────┐
  │  REST Controllers                  │  /api/auth, /api/channels
  │  WebSocket Controllers             │  /app/chat.send, /app/presence.*
  │  SSE Controller                    │  /api/ai/suggest/{channelId}
  │                                    │
  │  ChatService                       │  Save → MongoDB, Publish → Redis
  │  PresenceService                   │  Redis TTL keys + heartbeat
  │  JwtService                        │  Access + Refresh tokens
  │  AiSuggestionService               │  Groq API → SSE stream
  │                                    │
  │  RedisMessageSubscriber            │  Redis → WebSocket broadcast
  └──────┬─────────────────────────────┘
         │
    ┌────┴─────┐
    ▼          ▼
 MongoDB     Redis
(messages)  (presence + pub/sub + suggestion cache)
```

### Message Flow

```
Client → /app/chat.send/{channelId}
       → ChatService.processAndBroadcast()
           ├── Sanitize + validate content
           ├── Save to MongoDB
           └── Publish to Redis: chat:{channelId}
               → RedisMessageSubscriber.onMessage()
               → Broadcast to /topic/channel/{channelId}
               → All subscribed WebSocket clients
```

### Presence Flow

```
Connect   → /app/presence.join      → SET presence:{userId} EX 30 (Redis)
Heartbeat → /app/presence.heartbeat → EXPIRE reset (every 20s)
Disconnect → WebSocketEventListener → DEL presence:{userId}
Each event → broadcast to /topic/presence
```

### AI Suggestion Flow

```
Client → GET /api/ai/suggest/{channelId}  (SSE)
       → AiSuggestionService.streamSuggestions()
           ├── Load last 15 messages from MongoDB
           ├── Check Redis cache (suggest:{channelId}:{lastMsgId})
           │     └── Cache hit → send immediately, no LLM call
           ├── Build prompt from conversation context (max 3000 chars)
           └── Stream to Groq API (llama-3.1-8b-instant)
               ├── SSE event: "token"       → live typing effect on client
               └── SSE event: "suggestions" → final JSON array of 3
                   └── Cache result in Redis for 30s
```

---

## 🛠️ Tech Stack

| | |
|---|---|
| Framework | Spring Boot 3 |
| Real-time | WebSocket + STOMP (SockJS) |
| Message Broker | Redis Pub/Sub |
| Database | MongoDB |
| Auth | JWT (jjwt) |
| AI | Groq API — `llama-3.1-8b-instant` |
| Streaming | Server-Sent Events (SSE) |
| Build | Maven |

---

## ⚙️ Prerequisites

- Java 17+
- Maven 3.8+
- MongoDB on `localhost:27017`
- Redis on `localhost:6379`
- Groq API key — free at [console.groq.com](https://console.groq.com) *(optional — falls back to static suggestions if absent)*

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

# JWT
jwt.secret=your-secret-key-here

# Server
server.port=8080

# Groq AI (optional — fallback suggestions used if blank)
ai.groq.api-key=your-groq-api-key
ai.groq.model=llama-3.1-8b-instant
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

**Register body:**
```json
{ "username": "alice", "email": "alice@example.com", "password": "Secret1" }
```

**Login body:**
```json
{ "email": "alice@example.com", "password": "Secret1" }
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

**Validation rules:**
- Username: 3–20 chars, letters / numbers / underscores only
- Password: min 8 chars, at least one uppercase letter and one number

### Chat — Protected

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/channels/{channelId}/messages` | Load message history |

Query params:
- `limit` — number of messages (default: `50`)
- `before` — message ID for cursor-based pagination

### Presence — Public

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/presence/online` | All online users globally |
| `GET` | `/api/channels/{channelId}/presence` | Online users in a channel |
| `GET` | `/api/presence/{userId}` | Check if a specific user is online |

### AI Suggestions — Public

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/ai/suggest/{channelId}` | Stream AI reply suggestions (SSE) |

**SSE event types:**

| Event | Data | Description |
|-------|------|-------------|
| `token` | `{ "token": "..." }` | Individual token — shows typing effect |
| `suggestions` | `["...", "...", "..."]` | Final JSON array of 3 suggestions |

---

## 🔌 WebSocket API

**Endpoint:** `ws://localhost:8080/ws`

Connect with STOMP headers:
```
Authorization: Bearer <accessToken>
userId:        <userId>
username:      <username>
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
| `/app/chat.typing/{channelId}` | `ChatMessage` | Typing indicator (ephemeral, skips Redis) |
| `/app/presence.join` | `{ userId, username, channelId }` | Mark user online |
| `/app/presence.leave` | `{ userId, username, channelId }` | Mark user offline |
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
│   ├── RedisConfig.java                # RedisTemplate + Pub/Sub listener
│   ├── SecurityConfig.java             # JWT filter chain + CORS
│   ├── WebSocketConfig.java            # STOMP broker + JWT header validation
│   └── WebSocketEventListener.java     # Connect/disconnect lifecycle events
├── controller/
│   ├── AuthController.java             # Register + Login
│   ├── ChatController.java             # WS chat.send, REST message history
│   ├── PresenceController.java         # WS presence.*, REST presence queries
│   └── AiSuggestionController.java     # SSE AI suggestion stream
├── dto/
│   └── ChatMessage.java                # WebSocket message DTO
├── model/
│   ├── Message.java                    # MongoDB message document
│   └── User.java                       # MongoDB user document
├── repository/
│   ├── MessageRepository.java
│   └── UserRepository.java
├── security/
│   └── JwtAuthFilter.java              # JWT filter for HTTP requests
└── service/
    ├── ChatService.java                # Sanitize, save, Redis publish
    ├── JwtService.java                 # Token generation + validation
    ├── PresenceService.java            # Redis TTL presence management
    ├── RateLimiterService.java         # Multi-layer Redis rate limiting
    ├── AiSuggestionService.java        # Groq streaming + Redis cache
    └── RedisMessageSubscriber.java     # Redis → WebSocket bridge
```

---

## 🤖 AI Suggestions

- Uses **Groq API** with `llama-3.1-8b-instant` (free tier, very fast)
- Reads the **last 15 messages** from MongoDB to build conversation context (capped at 3000 chars)
- Returns exactly **3 suggestions** — one casual, one informative, one question — each under 12 words
- Results are **cached in Redis for 30 seconds** keyed by `suggest:{channelId}:{lastMessageId}` — identical channel state never hits the LLM twice
- Tokens are streamed live via SSE for a typing effect, then the complete JSON array is sent as the final event
- Falls back to hardcoded suggestions if the Groq API key is missing or the call fails

---

## 🔐 Security

- **Rate limiting** on login: 20 req/IP, 10 req/email, 5 req/IP+email combo — per 15-minute window, backed by Redis
- **Rate limiting** on register: 10 req/IP per 15 minutes
- **JWT validation** on every WebSocket CONNECT frame — `userId` header must match token subject to prevent spoofing
- **Message sanitization** — HTML tags and entities stripped before saving to MongoDB
- **Message length** capped at 2000 characters

> Before deploying to production:
> - Move `jwt.secret` and `ai.groq.api-key` to environment variables or a secrets manager
> - Restrict CORS `allowedOriginPatterns` to your frontend domain
> - Enable Redis authentication if Redis is network-exposed

---

## 📄 License

MIT
