# Gomoku — Multiplayer Socket Game

A real-time multiplayer Gomoku implementation built with raw TCP sockets and Java object serialization. Players connect to a central server, join a lobby, invite opponents, and play on a 10×10 grid where the first to align 4 stones wins.

## Architecture

```
┌─────────────────┐       TCP / Java ObjectStreams       ┌─────────────────────┐
│  gomoku-client   │ ◄──────────────────────────────────► │   gomoku-server      │
│  (CLI or JavaFX) │        NetPacket { type, payload }   │   (threaded)         │
└────────┬────────┘                                       │                      │
         │  depends on                                    │  ServerNetworkAdapter │
┌────────▼────────┐                                       │  ClientHandler (1/conn)│
│ gomoku-protocol  │ ◄──── shared ────────────────────────│  PacketDispatcher     │
│ (messaging +     │                                      │  LobbyController      │
│  payload types)  │                                      │  MatchController      │
└─────────────────┘                                       │  Game (10×10 board)   │
                                                          │  H2 Database          │
                                                          └─────────────────────┘
```

Three Maven modules under `com.athtech.gomoku`:

| Module | Purpose |
|---|---|
| **gomoku-protocol** | Shared `NetPacket` record, `PacketType` enum, and ~30 typed payload records (request/response pairs) used by both client and server |
| **gomoku-server** | TCP server — accepts connections, dispatches packets, manages lobby/match state, persists player data to an embedded H2 database |
| **gomoku-client** | Three sub-modules: `gomoku-client-net` (shared networking), `gomoku-client-cli` (terminal client), `gomoku-client-gui` (JavaFX client) |

## Features

- **Lobby system** — see who's online, who's in a game; diff-based broadcasting (1 s tick) to minimize traffic
- **Game invites** — invite a player, accept or decline; pending invites survive across views
- **Real-time gameplay** — 10×10 board, alternating turns (X / O), 4-in-a-row to win (horizontal, vertical, diagonal)
- **Rematch** — end-of-game and mid-game rematch voting between both players
- **Session reconnection** — relog codes allow seamless reconnection without re-authentication; in-progress matches resume automatically
- **Lobby chat** — global chat with rate limiting (5 messages / 10 seconds) and message length cap (250 chars)
- **Player accounts** — signup / login with hashed passwords; win/loss/draw stats tracked in H2
- **Inactivity timeout** — idle logged-in users are disconnected after 3 minutes
- **Duplicate session handling** — logging in from a second client forces the previous session closed

## Tech Stack

- Java 21, JPMS modules
- TCP sockets with `ObjectInputStream` / `ObjectOutputStream` (Java serialization)
- H2 embedded database (file-based, `./data/gomoku`)
- JavaFX (GUI client)
- Docker (Eclipse Temurin 21 JRE)

## Prerequisites

- JDK 21+
- Maven 3.9+
- JavaFX SDK (for the GUI client)

## Build

```bash
mvn clean package -pl gomoku-protocol,gomoku-server,gomoku-client
```

## Run

**Server:**

```bash
java -cp gomoku-server/target/gomoku-server-1.0-SNAPSHOT.jar:gomoku-protocol/target/gomoku-protocol-1.0-SNAPSHOT.jar \
     com.athtech.gomoku.server.ServerLauncher
```

Default port: `999`. Override with the `PORT` environment variable.

**Server (Docker):**

```bash
docker compose -f gomoku-server/docker-compose.yml up --build
```

The compose file maps port 999 and mounts `./data` for database persistence.

**CLI Client:**

```bash
java -cp gomoku-client/gomoku-client-cli/target/*:gomoku-protocol/target/* \
     com.athtech.gomoku.client.cli.<MainClass>
```

**GUI Client:**

```bash
java --module-path <javafx-sdk>/lib --add-modules javafx.controls,javafx.fxml \
     -cp gomoku-client/gomoku-client-gui/target/*:gomoku-protocol/target/* \
     com.athtech.gomoku.client.gui.<MainClass>
```

## Protocol

Communication uses Java object serialization over raw TCP. Every message is a `NetPacket` record:

```java
record NetPacket(PacketType type, String sender, Serializable payload) implements Serializable
```

Packet types include: `LOGIN_REQUEST/RESPONSE`, `SIGNUP_REQUEST/RESPONSE`, `MOVE_REQUEST`, `GAME_STATE_RESPONSE`, `GAME_END_RESPONSE`, `INVITE_REQUEST/RESPONSE`, `LOBBY_PLAYERS_RESPONSE`, `LOBBY_CHAT_MESSAGE_REQUEST/RESPONSE`, `REMATCH_REQUEST/RESPONSE`, `RESYNC_REQUEST/RESPONSE`, `HANDSHAKE_REQUEST/RESPONSE`, among others.

## Project Structure

```
├── pom.xml                          (parent POM, Java 21)
├── gomoku-protocol/
│   └── src/main/java/.../protocol/
│       ├── messaging/               NetPacket, PacketType, MatchEndReason
│       └── payload/                 ~30 request/response records
├── gomoku-server/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── src/main/java/.../server/
│       ├── ServerLauncher.java      Entry point
│       ├── net/                     ServerNetworkAdapter, ClientHandler,
│       │                            PacketDispatcher, LobbyController
│       ├── game/                    Game (board logic, win detection)
│       ├── match/                   Match, MatchController, RematchVote
│       └── persistence/             H2-backed player storage
└── gomoku-client/
    ├── gomoku-client-cli/           Terminal client
    ├── gomoku-client-gui/           JavaFX client (CSS-styled)
    └── gomoku-client-net/           Shared client networking
```

## Contributors

| GitHub | |
|---|---|
| [Balasis](https://github.com/Balasis) | Developer |
| [vertimnu-s](https://github.com/vertimnu-s) | Developer |

[YadaYadaAT](https://github.com/YadaYadaAT) is the shared organization account for the team.

## License

Academic project — Athens Tech College (BSc Computer Science via York St John University).
