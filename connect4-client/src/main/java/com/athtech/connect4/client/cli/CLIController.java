package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;
import com.athtech.connect4.protocol.messaging.*;
import com.athtech.connect4.protocol.payload.*;

public class CLIController {

    private final CLIView view;
    private final CLIInputHandler input;
    private final ClientNetworkAdapter clientNetwork;

    private String username;
    private boolean loggedIn = false;
    private boolean inGame = false;
    private NetPacket lastInvite = null;

    // Locks used for synchronous waits (login, game updates)
    private final Object loginLock = new Object();
    private final Object gameLock = new Object();
    private final Object inviteLock = new Object();

    // Controls outer loop exit
    private boolean appShouldExit = false;

    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork, CLIInputHandler input) {
        this.view = view;
        this.clientNetwork = clientNetwork;
        this.input = input;
        clientNetwork.setListener(this::handleServerPacket);
    }

    /* ======================================================
       ENTRY POINT (keeps ability to logout -> login again)
       ====================================================== */
    public void run() {
        boolean running = true;

        while (running && !appShouldExit) {
            resetSessionState();

            // Login/signup/exit screen
            loginMenuLoop();

            if (appShouldExit) break;
            if (!loggedIn) {
                // user aborted or login never succeeded; restart outer loop
                continue;
            }

            // Main lobby/game loop; returns whether we should continue running
            running = mainLoop();
        }

        view.show("Client stopped.");
    }

    private void resetSessionState() {
        loggedIn = false;
        inGame = false;
        lastInvite = null;
    }

    /* ======================================================
       LOGIN / SIGNUP UI (non-blocking; uses waitFor with timeout)
       ====================================================== */
    private void loginMenuLoop() {
        while (!loggedIn && !appShouldExit) {
            view.showLoginScreen();
            String pick = input.readChoice();
            switch (pick) {
                case "1" -> attemptLoginFlow();
                case "2" -> attemptSignupFlow();
                case "0" -> {
                    appShouldExit = true;
                    return;
                }
                default -> view.show("Invalid choice.");
            }
        }
    }

    private void attemptLoginFlow() {
        view.prompt("Username: ");
        username = input.readLine();
        if (username.equalsIgnoreCase("exit")) { appShouldExit = true; return; }

        view.prompt("Password: ");
        String password = input.readLine();

        LoginRequest req = new LoginRequest(username, password);
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, req));

        // wait up to 8 seconds for server response; if not received, inform user and allow retry
        boolean notified = waitFor(loginLock, 8000);
        if (!notified) {
            view.show("Login attempt timed out (no server response). Try again or check connection.");
        }
    }

    private void attemptSignupFlow() {
        view.showSignupPrompt();
        view.prompt("Choose username: ");
        String desired = input.readLine();

        view.prompt("Choose password: ");
        String pwd = input.readLine();

        SignupRequest req = new SignupRequest(desired, pwd);
        clientNetwork.sendPacket(new NetPacket(PacketType.SIGNUP_REQUEST, desired, req));

        // wait briefly for signup response (server may auto-login or instruct)
        boolean notified = waitFor(loginLock, 8000);
        if (!notified) {
            view.show("Signup timed out. Try again later.");
        }
        // if signup succeeded, server may set loggedIn=true via LOGIN_RESPONSE or SIGNUP_RESPONSE.
    }

    /* ======================================================
       MAIN LOOP (LOBBY + GAME), returns whether to keep app running
       ====================================================== */
    private boolean mainLoop() {
        while (loggedIn) {
            if (!inGame) {
                view.showLobbyMenu();
                String choice = input.readChoice();
                handleLobbyChoice(choice);
            } else {
                runGameLoop();
            }
        }
        // When loggedIn becomes false, return true to allow re-login.
        return true;
    }

    private void handleLobbyChoice(String choice) {
        switch (choice) {
            case "1" -> sendInvite();
            case "2" -> handleIncomingInviteDecision();
            case "3" -> requestLobbyPlayers(); // refresh list
            case "0" -> logout();
            default -> view.show("Invalid option.");
        }
    }

    /* ======================================================
       LOBBY / INVITES
       ====================================================== */
    private void sendInvite() {
        view.showInvitePrompt();
        String target = input.readLine();
        if (target.isBlank()) { view.show("No target entered."); return; }

        InviteRequest req = new InviteRequest(target);
        clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_REQUEST, username, req));
    }

    private void handleIncomingInviteDecision() {
        synchronized (inviteLock) {
            if (lastInvite == null) {
                view.show("No pending invites.");
                return;
            }
            InviteNotification notif = (InviteNotification) lastInvite.payload();
            view.show("Invite from: " + notif.fromUsername());
            view.showAcceptPrompt();
            boolean accept = input.readLine().equalsIgnoreCase("y");

            InviteDecisionRequest req = new InviteDecisionRequest(notif.fromUsername(), accept);
            clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST, username, req));
            lastInvite = null;
        }
    }

    private void requestLobbyPlayers() {
        clientNetwork.sendPacket(new NetPacket(PacketType.LOBBY_PLAYERS, username, new LobbyPlayersRequest()));
    }

    /* ======================================================
       GAME LOOP (turn-based)
       - Uses timeout to avoid deadlocks. If timeout occurs, user is informed and returned to menu.
       ====================================================== */
    private void runGameLoop() {
        view.showGameStart();

        while (inGame) {
            view.showGamePromptRow();
            int row = input.readInt();

            view.showGamePromptCol();
            int col = input.readInt();

            Move move = new Move(row, col);
            clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, username, move));

            // Wait up to 60 seconds for the next GameState (or other event). If timeout, inform user and continue.
            boolean notified = waitFor(gameLock, 60_000);
            if (!notified) {
                view.show("No update from server after your move (timeout). You can continue; server may be slow or disconnected.");
                // re-check inGame flag — server might have ended the game in the meantime
                if (!inGame) break;
            }
        }
    }

    /* ======================================================
       REMATCH flow (triggered when game ends)
       - When server sends GAME_END, controller will prompt for rematch (below in onGameEnd).
       ====================================================== */

    /* ======================================================
       NETWORK / PACKET HANDLING
       ====================================================== */
    private void handleServerPacket(NetPacket packet) {
        switch (packet.type()) {
            case LOGIN_RESPONSE -> onLoginResponse(packet);
            case SIGNUP_RESPONSE -> onSignupResponse(packet);
            case LOBBY_PLAYERS -> onLobbyPlayers(packet);
            case INVITE_NOTIFICATION -> onInviteNotification(packet);
            case INVITE_RESPONSE -> onInviteResponse(packet);
            case INVITE_DECISION_RESPONSE -> onInviteDecisionResponse(packet);
            case GAME_STATE -> onGameState(packet);
            case GAME_END -> onGameEnd(packet);
            case REMATCH_RESPONSE -> onRematchResponse(packet);
            case REMATCH_REQUEST -> onRematchRequest(packet);
            case ERROR_MESSAGE -> onError(packet);
            case INFO -> onInfoNotification(packet);
            default -> view.show("Unhandled packet: " + packet.type());
        }
    }

    private void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        view.show(resp.message());
        notifyAllLock(loginLock);
    }

    private void onSignupResponse(NetPacket packet) {
        SignupResponse resp = (SignupResponse) packet.payload();
        view.show(resp.message());
        // Server may auto-login on signup; if so it should send LOGIN_RESPONSE next.
        notifyAllLock(loginLock);
    }

    private void onLobbyPlayers(NetPacket packet) {
        LobbyPlayers lobby = (LobbyPlayers) packet.payload();
        view.show("Lobby: " + String.join(", ", lobby.players()));
    }

    private void onInviteNotification(NetPacket packet) {
        synchronized (inviteLock) {
            lastInvite = packet;
        }
        InviteNotification notif = (InviteNotification) packet.payload();
        view.show("Invite from: " + notif.fromUsername());
    }

    private void onInviteResponse(NetPacket packet) {
        InviteResponse resp = (InviteResponse) packet.payload();
        view.show(resp.delivered() ? "Invite delivered." : "Invite failed: " + resp.reason());
    }

    private void onInviteDecisionResponse(NetPacket packet) {
        InviteDecisionResponse resp = (InviteDecisionResponse) packet.payload();
        if (resp.accepted()) {
            inGame = true;
            view.show("Invitation accepted. Match starting...");
            notifyAllLock(gameLock);
        } else {
            view.show("Invitation declined.");
        }
    }

    private void onGameState(NetPacket packet) {
        GameState gs = (GameState) packet.payload();
        // TODO: render board -> for now print a short summary
        view.show("Game updated. Turn: " + gs.currentPlayer() + (gs.gameOver() ? " (game over)" : ""));
        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameEnd(NetPacket packet) {
        GameEnd end = (GameEnd) packet.payload();
        // show final message
        view.show("Game ended: " + end.reason());

        // ask for rematch
        view.showRematchPrompt();
        boolean wantRematch = input.readLine().equalsIgnoreCase("y");
        if (wantRematch) {
            // send rematch request to server (server decides how to route it)
            RematchRequest req = new RematchRequest(/*opponentUsername=*/ end.opponent());
            clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_REQUEST, username, req));
            view.show("Rematch request sent.");
        }

        // ensure we exit game loop
        inGame = false;
        notifyAllLock(gameLock);
    }

    private void onRematchResponse(NetPacket packet) {
        RematchResponse resp = (RematchResponse) packet.payload();
        view.show("Rematch response: " + (resp.accepted() ? "accepted" : "declined") + ". " + resp.message());
    }

    private void onRematchRequest(NetPacket packet) {
        // Server forwarded a rematch offer from opponent
        RematchRequest offer = (RematchRequest) packet.payload();
        view.show("Rematch offer from: " + offer.requester());
        view.prompt("Accept rematch? (y/n): ");
        boolean accept = input.readLine().equalsIgnoreCase("y");
        RematchResponse resp = new RematchResponse(accept, accept ? "OK" : "No thanks");
        clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_RESPONSE, username, resp));
    }

    private void onError(NetPacket packet) {
        ErrorMessage err = (ErrorMessage) packet.payload();
        view.show("ERROR: " + err.message());
        // if error is fatal (e.g., disconnected) notify locks
        notifyAllLock(loginLock);
        notifyAllLock(gameLock);
    }

    private void onInfoNotification(NetPacket packet){
        view.show(" ");
        view.show("INFO: " + packet.payload());
    }

    /* ======================================================
       HELPERS: logout, waitFor with timeout, notifyAll
       ====================================================== */

    private void logout() {
        loggedIn = false;
        try {
            clientNetwork.disconnect();
        } catch (Exception ignored) {}
    }

    /**
     * Wait for lock to be notified (or timeout). Returns true if notified, false if timed out.
     */
    private boolean waitFor(Object lock, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (lock) {
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return false;
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                // awakened — caller code will check the relevant condition (e.g., loggedIn/inGame)
                return true;
            }
        }
    }

    /** Convenience: short waitFor with default timeout (used by login/game where needed). */
    private boolean waitFor(Object lock) {
        // default: 8 seconds for login, 60s for game in their callers
        return waitFor(lock, 8000);
    }

    /** Notify all waiting threads on lock. */
    private void notifyAllLock(Object lock) {
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
