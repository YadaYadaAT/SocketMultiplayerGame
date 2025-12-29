package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;
import com.athtech.connect4.protocol.messaging.*;
import com.athtech.connect4.protocol.payload.*;

import java.util.*;

public class CLIController {
    private final CLIView view;
    private final ClientNetworkAdapter clientNetwork;
    private final CLIInputHandler input;

    // Session state
    private volatile boolean loggedIn = false;
    private volatile boolean sessionClosing = false;
    private volatile boolean inGame = false;
    private volatile InviteNotificationResponse lastInvite = null;
    private String username;
    private String relogCode;

    private volatile String pendingRematchRequester = null;
    private volatile String pendingRematchOpponent = null;

    private volatile List<String> lobbyPlayers = new ArrayList<>();
    private volatile PlayerStatsResponse myStats;

    // Locks
    private final Object loginLock = new Object();
    private final Object logoutLock = new Object();
    private final Object gameLock = new Object();
    private final Object inviteLock = new Object();
    private final Object reconnectLock = new Object();
    private final Object rematchLock = new Object();
    private volatile boolean rematchPhase = false; // true when waiting for rematch decision
    private volatile boolean reconnectInProgress = false;
    private final int MAX_RECONNECT_ATTEMPTS = 3;
    private final long RECONNECT_INTERVAL_MS = 12_000;

    private volatile boolean gameStartingPromptConsumsed = false;

    private boolean shouldAppExit = false;

    // --- Testing hook ---
    private volatile boolean simulateServerDown = false;

    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork ) {
        this.view = view;
        this.clientNetwork = clientNetwork;
        input = new CLIInputHandler(this::getIsInGame);
        clientNetwork.setListener(this::handleServerPacket);
        clientNetwork.setConnectionLostListener(this::handleConnectionLost);
    }

    public void run() {
        while (!shouldAppExit) {
            resetSessionState();
            authenticationMenu();
            if (loggedIn) mainLoop();
        }
        view.show("Hope you enjoyed our app. o/");
    }

    private void resetSessionState() {
        loggedIn = false;
        sessionClosing = false;
        inGame = false;
        lastInvite = null;
        username = null;
        relogCode = null;
        pendingRematchRequester = null;
        pendingRematchOpponent = null;
        lobbyPlayers.clear();
        myStats = null;
        gameStartingPromptConsumsed = false;
    }

    private void authenticationMenu() {
        view.showLoginScreen();
        switch (input.readChoice()) {
            case "1" -> attemptLoginFlow();
            case "2" -> attemptSignupFlow();
            case "0" -> {
                shouldAppExit = true;
                try { clientNetwork.disconnect(); } catch (Exception ignored) {}
            }
            default -> view.show("Invalid choice.");
        }
    }

    private void attemptLoginFlow() {
        view.prompt("Username: ");
        username = input.readLine();
        if ("exit".equalsIgnoreCase(username)) { shouldAppExit = true; return; }
        view.prompt("Password: ");
        String password = input.readLine();

        clientNetwork.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, new LoginRequest(username, password)));
        waitFor(loginLock, 8000);
    }

    private void attemptSignupFlow() {
        view.showSignupPrompt();
        view.prompt("Choose username: ");
        String desired = input.readLine();
        view.prompt("Choose password: ");
        String pwd = input.readLine();

        clientNetwork.sendPacket(new NetPacket(PacketType.SIGNUP_REQUEST, desired, new SignupRequest(desired, pwd)));
        waitFor(loginLock, 8000);
    }

    private void mainLoop() {
        while (loggedIn) {
            if (sessionClosing) { waitFor(logoutLock, 1000); continue; }

            if (!inGame && pendingRematchRequester != null) handleIncomingRematchRequest();
            if (!inGame && pendingRematchOpponent != null) handleRematchPrompt();

            gameStartingPromptConsumsed = false;
            if (!inGame && pendingRematchRequester == null && pendingRematchOpponent == null) handleLobby();

            if (inGame) runGameLoop();
        }
    }

    private void runGameLoop() {
        view.showGameStart();
        while (inGame && loggedIn && !sessionClosing) {
            int[] move = input.readMove();
            // game end consume input of loser
            if (!inGame || move == null) {break;}
            int row = move[0];
            int col = move[1];
            clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, username, new MoveRequest(row, col)));
            boolean notified = waitFor(gameLock, 60_000);
            if (!notified || simulateServerDown) {
                view.showCallback("No response from server. Attempting resync...");
                handleConnectionLost();
            }
        }
    }



    private void handleLobby() {
        if (inGame || rematchPhase) {
            view.show("You are currently in a game or rematch phase. Lobby actions are disabled.");
            return;
        }
        view.showLobbyMenu();
        var choice = input.readChoice();
        if (inGame || rematchPhase) {
            gameStartingPromptConsumsed = true;
            return; }

        switch (choice) {
            case "1" -> sendInviteRequest();
            case "2" -> handleReceivedInviteRequest();
            case "3" -> requestLobbyPlayers();
            case "4" -> requestPlayerStats();
            case "0" -> requestLogout();
            default -> view.show("Invalid option.");
        }
    }

    private void handleIncomingRematchRequest() {
        view.show("Rematch offer from: " + pendingRematchRequester);
        view.prompt("Accept rematch? (y/n): ");
        boolean accept = input.readLine().trim().equalsIgnoreCase("y");
        clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_DECISION_REQUEST, username,
                new RematchDecisionRequest(pendingRematchRequester, accept)));
        pendingRematchRequester = null;
    }

    private void handleRematchPrompt() {
        if (!rematchPhase) return;

        view.showRematchPrompt();
        boolean wantRematch = input.readLine().trim().equalsIgnoreCase("y");
        if (wantRematch) {
            clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_REQUEST, username, new RematchRequest(true)));
            view.show("Rematch request sent.");
        }else{
            clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_REQUEST, username, new RematchRequest(false)));
        }
        rematchPhase = false;
        pendingRematchOpponent = null;
    }




    private void sendInviteRequest() {
        if (inGame) return;
        List<String> snapshot = requestLobbyPlayers();
        if (snapshot.isEmpty()) return;

        view.prompt("Choose a player to invite (number), press `q` to go back: ");
        int choice = input.readIntOrQuit();
        if (choice == -1) {
            view.show("Invite cancelled. Returning to lobby menu.");
            return;
        }
        if (choice < 1 || choice > snapshot.size()) {
            view.show("Invalid choice. Returning to lobby menu.");
            return;
        }

        clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_REQUEST, username,
                new InviteRequest(snapshot.get(choice - 1))));
    }

    private void handleReceivedInviteRequest() {
        synchronized (inviteLock) {
            if (inGame || lastInvite == null) return;
            view.show("Invite from: " + lastInvite.fromUsername());
            view.showAcceptPrompt();
            boolean accept = input.readLine().trim().equalsIgnoreCase("y");
            clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST, username,
                    new InviteDecisionRequest(lastInvite.fromUsername(), accept)));
            lastInvite = null;
        }
    }

    public void handleConnectionLost() {
        synchronized (reconnectLock) {
            if (reconnectInProgress || username == null || relogCode == null) return;
            reconnectInProgress = true;
        }

        new Thread(() -> {
            int attempts = 0;
            boolean success = false;

            while (attempts < MAX_RECONNECT_ATTEMPTS && !success) {
                attempts++;
                view.showCallback("Attempting reconnect (" + attempts + "/" + MAX_RECONNECT_ATTEMPTS + ")...");
                clientNetwork.sendPacket(new NetPacket(PacketType.RECONNECT_REQUEST, username, new ReconnectRequest(username, relogCode)));

                boolean notified = waitFor(reconnectLock, 8000);
                synchronized (reconnectLock) { success = !reconnectInProgress; }

                if (!success) {
                    try { Thread.sleep(RECONNECT_INTERVAL_MS); } catch (InterruptedException ignored) {}
                }
            }

            synchronized (reconnectLock) { reconnectInProgress = false; }

            if (!success) {
                view.showCallback("Reconnect failed. Session lost.");
                loggedIn = false;
                inGame = false;
                notifyAllLock(gameLock);
            }
        }, "ReconnectThread").start();
    }

    private List<String> requestLobbyPlayers() {
        if (inGame) return List.of();
        if (lobbyPlayers.isEmpty()) { view.showCallback("No players available."); return List.of(); }
        for (int i = 0; i < lobbyPlayers.size(); i++) view.showCallback((i + 1) + ") " + lobbyPlayers.get(i));
        return new ArrayList<>(lobbyPlayers);
    }

    private void requestPlayerStats() {
        if (inGame || myStats == null) return;
        view.showCallback("Stats → Played: " + myStats.gamesPlayed() +
                ", Wins: " + myStats.wins() +
                ", Losses: " + myStats.losses() +
                ", Draws: " + myStats.draws());
    }

    private void requestLogout() {
        if (inGame || sessionClosing) { view.showCallback("Logout already in progress..."); return; }

        sessionClosing = true;
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGOUT_REQUEST, username, new LogoutRequest()));
        waitFor(logoutLock, 4000);

        resetSessionState();
        try { clientNetwork.disconnect(); } catch (Exception ignored) {}
    }



    private int readGameInt(String prompt) {
        while (true) {
            view.prompt(prompt);
            try { return Integer.parseInt(input.readLine().trim()); }
            catch (NumberFormatException e) { view.showCallback("Invalid number. Try again."); }
        }
    }

    private void handleServerPacket(NetPacket packet) {
        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE) return;

        switch (packet.type()) {
            case LOGIN_RESPONSE -> onLoginResponse(packet);
            case SIGNUP_RESPONSE -> onSignupResponse(packet);
            case LOGOUT_RESPONSE -> onLogoutResponse(packet);
            case LOBBY_PLAYERS_RESPONSE -> onLobbyPlayersResponse(packet);
            case PLAYER_STATS_RESPONSE -> onPlayerStatsResponse(packet);
            case INVITE_RESPONSE -> onInviteResponse(packet);
            case INVITE_NOTIFICATION_RESPONSE -> onInviteNotificationResponse(packet);
            case INVITE_DECISION_RESPONSE -> onInviteDecisionResponse(packet);
            case GAME_START_RESPONSE -> onGameStartResponse(packet);
            case GAME_STATE_RESPONSE -> onGameStateResponse(packet);
            case GAME_END_RESPONSE -> onGameEndResponse(packet);
            case MOVE_REJECTED_RESPONSE -> onMoveRejectedResponse(packet);
            case REMATCH_RESPONSE -> onRematchResponse(packet);
            case REMATCH_NOTIFICATION_RESPONSE -> onRematchNotificationResponse(packet);
            case RECONNECT_RESPONSE -> onReconnectResponse(packet);
            case ERROR_MESSAGE_RESPONSE -> onErrorMessageResponse(packet);
            case INFO_RESPONSE -> onInfoResponse(packet);
            default -> view.showCallback("Unhandled packet: " + packet.type());
        }
    }

    // --- on* callbacks now use showCallback / showCallbackHighlight ---
    private void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        view.showCallback(resp.message());
        if (loggedIn) { relogCode = resp.relogCode(); myStats = resp.stats(); }
        notifyAllLock(loginLock);
    }

    private void onSignupResponse(NetPacket packet) {
        SignupResponse resp = (SignupResponse) packet.payload();
        view.showCallback(resp.message());
        notifyAllLock(loginLock);
    }

    private void onLobbyPlayersResponse(NetPacket packet) {
        var lobP = (String[]) packet.payload();
        lobbyPlayers = new ArrayList<>(Arrays.asList(lobP));
        view.showCallback("Lobby: " + String.join(", ", lobbyPlayers));
    }

    private void onInviteNotificationResponse(NetPacket packet) {
        lastInvite = (InviteNotificationResponse) packet.payload();
        view.showCallback("Invite from: " + lastInvite.fromUsername());
    }

    private void onInviteResponse(NetPacket packet) {
        var resp = (InviteResponse) packet.payload();
        view.showCallback(resp.delivered() ? "Invite delivered." : "Invite failed: " + resp.reason());
    }

    private void onInviteDecisionResponse(NetPacket packet) {
        var resp = (InviteDecisionResponse) packet.payload();
        if (resp.accepted()) {
            view.showCallback("Invitation accepted. Match starting...");
            inGame = true;
        } else view.showCallback("Invitation declined.");
        notifyAllLock(gameLock);
    }

    private void onGameStartResponse(NetPacket packet) {
        view.showGameStarted("Ignore Lobby Menu! Game started!");

        GameStateResponse gs = (GameStateResponse) packet.payload();
        view.showBoard(gs.board());

        if (gs.currentPlayer().equals(username)) {
            view.showYourTurn(
                    "At the start of the game players need to press "
                            + "\u001B[38;5;208m`enter`\u001B[0m"
                            + " once to enter into the game mode!"
                            + "\nIt's also your turn so afterwards enter your move:\n row,column:"
            );
        } else {
            view.showWaitTurn(
                    "Press "
                            + "\u001B[38;5;208m`enter`\u001B[0m"
                            + " once to enter into the gaming mode"
                            + "\nAnd then wait since it's your opponent's turn."
            );
        }

        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameStateResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        view.showBoard(gs.board());

        if (gs.currentPlayer().equals(username)) {
            if (!gameStartingPromptConsumsed) {
                view.showYourTurn(
                        "It's your turn but you haven't yet pressed "
                                + "\u001B[38;5;208m`enter`\u001B[0m"
                                + " to enter into game mode!"
                                + "\nOnce you activate it you may proceed with your move afterwards:\n row,column :"
                );
            } else {
                view.showYourTurn("It's your turn! Enter your move: row,column");
            }
        } else {
            view.showWaitTurn("Opponent's turn. Please wait for your turn.");
        }

        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameEndResponse(NetPacket packet) {
        GameEndResponse end = (GameEndResponse) packet.payload();
        inGame = false;
        if (end.finalBoard() != null) {
            view.showBoard(end.finalBoard());
        }
        boolean iWon = username.equals(end.winner());
        boolean draw = "Draw".equals(end.reason());
        String message = "Draw".equals(end.reason())
                ? "Game ended in a draw against " + end.opponent()
                : (username.equals(end.winner())
                ? "You won against " + end.opponent()
                : "You lost against " + end.opponent());

        view.showCallback(message);


        notifyAllLock(gameLock);

        if (!iWon && !draw) {
            view.showCallback("Press ENTER to continue...");
        }

        rematchPhase = true;
        pendingRematchOpponent = end.opponent();

        notifyAllLock(rematchLock);
    }

    private void onRematchResponse(NetPacket packet) {
        RematchResponse resp = (RematchResponse) packet.payload();
        view.showCallback("Rematch response: " + (resp.accepted() ? "accepted" : "declined") + ". " + resp.message());
    }

    private void onRematchNotificationResponse(NetPacket packet) {
        var offer = (RematchNotificationResponse) packet.payload();
        view.showCallback("Rematch offer from: " + offer.requester());
        pendingRematchRequester = offer.requester();
    }

    private void onMoveRejectedResponse(NetPacket packet) {
        MoveRejectedResponse rej = (MoveRejectedResponse) packet.payload();

        view.showCallback("Move rejected: " + rej.reason());

        if (rej.currentPlayer() == null) {
            inGame = false;
            view.showCallback("Game session lost. Returning to lobby...");
            notifyAllLock(gameLock);
        }else{
            notifyAllLock(gameLock);
        }

    }

    private void onPlayerStatsResponse(NetPacket packet) {
        myStats = (PlayerStatsResponse) packet.payload();
    }

    private void onLogoutResponse(NetPacket packet) {
        LogoutResponse resp = (LogoutResponse) packet.payload();
        view.showCallback(resp.message());
        sessionClosing = false;
        resetSessionState();
        notifyAllLock(logoutLock);
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
        try { clientNetwork.disconnect(); } catch (Exception ignored) {}
    }

    private void onErrorMessageResponse(NetPacket packet) {
        ErrorMessageResponse err = (ErrorMessageResponse) packet.payload();
        view.showCallbackHighlight("ERROR: " + err.message());
        notifyAllLock(loginLock);
        notifyAllLock(gameLock);
    }

    private void onReconnectResponse(NetPacket packet) {
        ReconnectResponse resp = (ReconnectResponse) packet.payload();
        synchronized (reconnectLock) { reconnectInProgress = false; }

        if (!resp.success()) {
            view.showCallback(resp.message());
            resetSessionState();
            notifyAllLock(loginLock);
            return;
        }

        view.showCallback(resp.message());
        loggedIn = true;
        sessionClosing = false;
        relogCode = resp.relogCode();
        myStats = resp.myStats();
        lobbyPlayers = resp.lobbyPlayers() != null ? Arrays.asList(resp.lobbyPlayers()) : new ArrayList<>();
        if (resp.currentGameState() != null) {
            inGame = !resp.currentGameState().gameOver();
            view.showCallbackHighlight(resp.currentGameState().currentPlayer().equals(username) ? "It's your turn!" : "Opponent's turn.");
        }
        InviteNotificationResponse[] invites = resp.pendingInvites();
        lastInvite = (invites != null && invites.length > 0) ? invites[invites.length - 1] : null;
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
    }

    private void onInfoResponse(NetPacket packet){
        if (!(packet.payload() instanceof String)) return;
        view.showCallback((String) packet.payload());
    }

    private boolean waitFor(Object lock, long timeoutMillis) {
        synchronized (lock) {
            try { lock.wait(timeoutMillis > 0 ? timeoutMillis : 0); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            return true;
        }
    }

    private void notifyAllLock(Object lock) {
        synchronized (lock) { lock.notifyAll(); }
    }

    public boolean getIsInGame() {
        return inGame;
    }

}
